package com.engish.ai;

/** Provider abstraction. Ollama is the only implementation today; add another @Component to support more. */
public interface LlmClient {
    String complete(String systemPrompt, String userPrompt, boolean jsonMode);
}
