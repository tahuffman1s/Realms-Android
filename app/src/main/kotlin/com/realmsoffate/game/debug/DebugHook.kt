package com.realmsoffate.game.debug

import android.app.Activity
import com.realmsoffate.game.game.GameViewModel
import kotlinx.coroutines.flow.MutableStateFlow

object DebugHook {
    var onAttach: ((Activity, GameViewModel) -> Unit)? = null
    val themeOverride = MutableStateFlow<Boolean?>(null)
    val fontScaleOverride = MutableStateFlow<Float?>(null)
}
