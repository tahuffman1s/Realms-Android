package com.realmsoffate.game.data

enum class AiProvider(val id: String, val label: String, val placeholder: String) {
    DEEPSEEK("deepseek", "DeepSeek", "sk-...");

    fun validate(key: String): Boolean = key.length > 20

    companion object {
        fun from(id: String?): AiProvider = DEEPSEEK
    }
}
