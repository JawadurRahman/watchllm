package com.focussystems.watchllm.llm

/** Single hardcoded config. Later milestones make [threads] and [systemPrompt] switchable. */
object LlmConfig {
    const val MODEL_FILE_NAME = "SmolLM2-135M-Instruct-Q4_K_M.gguf"
    const val THREADS = 2
    const val N_CTX = 2048
    const val MAX_TOKENS = 128
    const val TEMPERATURE = 0.3f
    const val SYSTEM_PROMPT = "You are a helpful assistant on a smartwatch. Answer concisely in 1-3 sentences."
}
