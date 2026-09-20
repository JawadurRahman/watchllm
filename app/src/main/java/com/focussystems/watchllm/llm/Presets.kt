package com.focussystems.watchllm.llm

/**
 * A preset = a system prompt + a template that wraps the user's input.
 * [promptTemplate] uses `{input}` as the placeholder. Edit [PRESETS] to add or change presets.
 */
data class Preset(
    val id: String,
    val label: String,
    val systemPrompt: String,
    val promptTemplate: String = "{input}",
) {
    fun buildPrompt(input: String) = promptTemplate.replace("{input}", input.trim())
}

val PRESETS: List<Preset> = listOf(
    Preset(
        id = "free",
        label = "Ask anything",
        systemPrompt = LlmConfig.SYSTEM_PROMPT,
        promptTemplate = "{input}",
    ),
    Preset(
        id = "fact",
        label = "Quick fact",
        systemPrompt = "You share one interesting, accurate fact. Reply in 1-2 short sentences.",
        promptTemplate = "Tell me a quick fact about: {input}",
    ),
    Preset(
        id = "simple",
        label = "Explain simply",
        systemPrompt = "You explain things in plain language a child could follow. Use 1-3 short sentences.",
        promptTemplate = "Explain simply: {input}",
    ),
    Preset(
        id = "summarize",
        label = "Summarize",
        systemPrompt = "You summarize text. Reply with a summary of 1-2 sentences and nothing else.",
        promptTemplate = "Summarize this:\n{input}",
    ),
    Preset(
        id = "shorter",
        label = "Rewrite shorter",
        systemPrompt = "You rewrite text to be shorter while keeping its meaning. Reply with only the rewritten text.",
        promptTemplate = "Rewrite this shorter:\n{input}",
    ),
)

fun presetById(id: String?): Preset? = PRESETS.firstOrNull { it.id == id }
