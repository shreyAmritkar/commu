# Engish: AI communication practice

Practise realistic conversations with an AI character that reacts to what you actually say, get personalised feedback, and compare your replies with alternatives inspired by a communication style.

**Stack:** plain HTML/CSS/JS frontend served from Spring Boot's static folder (no build step) · Java 17 + Spring Boot 3 · PostgreSQL + Flyway · Spring Data JPA · **Ollama** (local LLM, accessed only through the backend).

## Prerequisites
- Java 17+ and Maven
- PostgreSQL 14+ (or Docker)
- [Ollama](https://ollama.com) with a model pulled

## Setup and run

```bash
# 1. Database (pick one)
docker compose up -d                       # uses the bundled docker-compose.yml
# or, with a local Postgres:
#   createuser engish --pwprompt && createdb engish -O engish   (password: engish)

# 2. Ollama
ollama pull llama3.1:8b                    # or any chat model; set OLLAMA_MODEL to match
ollama serve                               # skip if the Ollama app is already running

# 3. Run everything (backend + frontend, http://localhost:8080)
cd backend
cp .env.example .env                       # edit if your DB or model differs
mvn spring-boot:run                        # Flyway creates the schema and seeds 5 scenarios + 5 styles
```

Open http://localhost:8080. The HTML frontend lives in `backend/src/main/resources/static` and is served by Spring Boot itself, so there is no separate frontend server and no CORS setup.


**Environment variables** (`backend/.env.example`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_TIMEOUT_SECONDS`, `OLLAMA_TEMPERATURE`, `CORS_ORIGINS`, `PORT`. The frontend needs no env file; it calls the same origin it is served from.

**Tips for local models:** the first request is slow while the model loads. Evaluations and alternative replies are the longest calls, so raise `OLLAMA_TIMEOUT_SECONDS` on slow hardware. Models of 7-8B parameters and up follow the JSON format reliably; smaller ones may trigger the "unusable response" error more often.

## Architecture

```
static HTML + JS (served by Spring Boot)  --REST/JSON-->  Spring Boot monolith  --JDBC-->  PostgreSQL
  static/js/api.js  service layer               |
  static/js/app.js  6 pages, hash router        +--HTTP--> Ollama /api/chat
```

Backend packages (`com.engish.*`):

| Package | Responsibility |
|---|---|
| `scenario`, `character`, `style` | Read-only catalogue: scenarios, AI characters, communication style profiles (6 traits each) |
| `session` | Session lifecycle (`ACTIVE` → `FINISHED` → `COMPLETED`), guest scoping, history |
| `interaction` | Turns, mood/goal/next-action enums, `ConversationService` (one conversational turn) |
| `ai` | `LlmClient` interface, `OllamaClient`, `PromptTemplates` (all prompt text), `AiService` (call, parse, validate, retry once) |
| `evaluation` | Evaluation generation and storage, results endpoints |
| `feedback` | Alternative reply + comparison generation and storage |
| `common` | Error model, CORS, guest-id handling |

Key rules: the model only *suggests* `nextAction`; the backend ends a conversation when the turn limit is hit, or when the model suggests wrapping up after at least 3 turns. LLM output is parsed into Java records and validated (enums, required fields, score ranges) before anything is saved; one retry on malformed JSON, then a clear error. Evaluations and alternatives are stored once and reused.

## Main request flow

1. `POST /api/sessions` (`scenarioId`, `styleId`): backend loads scenario + character, asks Ollama for the opening line, then saves the session and turn 0.
2. `POST /api/sessions/{id}/turns`: loads the last 10 turns and scene state, asks Ollama for `{characterMessage, sceneState, mood, goalProgress, nextAction}`, validates it, then saves both turns and updates session state in one transaction. On AI failure nothing is saved and the UI keeps your draft.
3. When the session is `FINISHED` (or you end early): `POST /api/sessions/{id}/complete` generates the six-dimension evaluation, stores it and marks the session `COMPLETED`. `GET /api/sessions/{id}/results` returns the stored result.
4. `POST /api/sessions/{id}/alternative-replies` (`turnId`, optional `styleId`): generates an alternative message, then a separate comparison (wording, tone, strategy, likely effect), and stores both.
5. `GET /api/sessions/history` lists your sessions.

**Guest scoping:** the browser creates a random id (kept in `localStorage`) and sends it as `X-Guest-Id`. Sessions are only visible to the guest that created them; others get a 404.

## Endpoints
`GET /api/scenarios` · `GET /api/scenarios/{id}` · `GET /api/styles` · `POST /api/sessions` · `GET /api/sessions/{id}` · `POST /api/sessions/{id}/turns` · `POST /api/sessions/{id}/complete` · `GET /api/sessions/{id}/results` · `POST /api/sessions/{id}/alternative-replies` · `GET /api/sessions/history`

Errors share one shape: `{ "code": "...", "message": "...", "timestamp": "...", "fieldErrors": {...} }`. AI problems use `AI_UNAVAILABLE` (503), `AI_TIMEOUT` (504), `AI_BAD_RESPONSE` (502) and `AI_NOT_CONFIGURED` (503).

## Known limits (prototype)
No auth, rate limiting or automated tests, by design. Concurrent submits to the same session are rejected with a 409 rather than queued.
