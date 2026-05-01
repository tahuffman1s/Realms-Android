package com.realmsoffate.game.data

import com.realmsoffate.game.data.content.ContentRepository

internal object PlayerNamePool {
    val NAMES: List<String> get() = ContentRepository.playerNames
}
