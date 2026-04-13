package com.realmsoffate.game.data

enum class AiProvider(val id: String, val label: String, val placeholder: String) {
    GEMINI("gemini", "Gemini", "AIza..."),
    DEEPSEEK("deepseek", "DeepSeek", "sk-..."),
    CLAUDE("claude", "Claude", "sk-ant-api03-...");

    fun validate(key: String): Boolean = when (this) {
        GEMINI -> key.length > 20
        DEEPSEEK -> key.length > 20
        CLAUDE -> key.startsWith("sk-")
    }

    companion object {
        fun from(id: String?): AiProvider = values().firstOrNull { it.id == id } ?: GEMINI
    }
}
