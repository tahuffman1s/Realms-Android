package com.realmsoffate.game.data.updater

import java.io.File

/**
 * State machine consumed by the UI. UpdateRepository emits transitions through
 * a single MutableStateFlow. Note: Available -> Downloading -> ReadyToInstall
 * is the only valid forward path; cancellations and errors revert to earlier
 * states (or Idle / UpToDate as appropriate).
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val release: ReleaseInfo) : UpdateState()
    data class Downloading(val release: ReleaseInfo, val progress: Int) : UpdateState()
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateState()
    data class Error(val reason: String, val recoverable: Boolean) : UpdateState()
}
