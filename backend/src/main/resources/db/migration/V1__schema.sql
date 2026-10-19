CREATE TABLE scenarios (
    id                BIGSERIAL PRIMARY KEY,
    slug              VARCHAR(80)  NOT NULL UNIQUE,
    title             VARCHAR(200) NOT NULL,
    description       TEXT         NOT NULL,
    category          VARCHAR(40)  NOT NULL,
    user_role         VARCHAR(200) NOT NULL,
    ai_role           VARCHAR(200) NOT NULL,
    emotional_context TEXT         NOT NULL,
    objective         TEXT         NOT NULL,
    success_criteria  TEXT         NOT NULL,
    difficulty        VARCHAR(20)  NOT NULL,
    max_turns         INT          NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE characters (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL UNIQUE,
    role_title     VARCHAR(150) NOT NULL,
    personality    TEXT         NOT NULL,
    speaking_style TEXT         NOT NULL,
    initial_mood   VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE scenario_characters (
    scenario_id  BIGINT NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    PRIMARY KEY (scenario_id, character_id)
);

CREATE TABLE style_profiles (
    id          BIGSERIAL PRIMARY KEY,
    style_key   VARCHAR(60)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NOT NULL,
    inspired_by VARCHAR(150) NOT NULL,
    warmth      INT NOT NULL CHECK (warmth BETWEEN 1 AND 10),
    confidence  INT NOT NULL CHECK (confidence BETWEEN 1 AND 10),
    directness  INT NOT NULL CHECK (directness BETWEEN 1 AND 10),
    humor       INT NOT NULL CHECK (humor BETWEEN 1 AND 10),
    empathy     INT NOT NULL CHECK (empathy BETWEEN 1 AND 10),
    formality   INT NOT NULL CHECK (formality BETWEEN 1 AND 10),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
    id               UUID PRIMARY KEY,
    scenario_id      BIGINT      NOT NULL REFERENCES scenarios (id),
    character_id     BIGINT      NOT NULL REFERENCES characters (id),
    style_id         BIGINT      NOT NULL REFERENCES style_profiles (id),
    status           VARCHAR(20) NOT NULL,
    mood             VARCHAR(20) NOT NULL,
    scene_state      TEXT        NOT NULL,
    goal_progress    VARCHAR(20) NOT NULL,
    user_turn_count  INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ
);


CREATE TABLE session_turns (
    id            BIGSERIAL PRIMARY KEY,
    session_id    UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    seq           INT         NOT NULL,
    speaker       VARCHAR(12) NOT NULL,
    message       TEXT        NOT NULL,
    scene_state   TEXT,
    mood          VARCHAR(20),
    goal_progress VARCHAR(20),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, seq)
);

CREATE TABLE evaluations (
    id            BIGSERIAL PRIMARY KEY,
    session_id    UUID        NOT NULL UNIQUE REFERENCES sessions (id) ON DELETE CASCADE,
    overall_score INT         NOT NULL,
    outcome       VARCHAR(20) NOT NULL,
    summary       TEXT        NOT NULL,
    result_json   TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alternative_replies (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    turn_id             BIGINT      NOT NULL REFERENCES session_turns (id) ON DELETE CASCADE,
    style_id            BIGINT      NOT NULL REFERENCES style_profiles (id),
    original_message    TEXT        NOT NULL,
    alternative_message TEXT        NOT NULL,
    comparison_json     TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, turn_id, style_id)
);
CREATE INDEX idx_alt_session ON alternative_replies (session_id);
