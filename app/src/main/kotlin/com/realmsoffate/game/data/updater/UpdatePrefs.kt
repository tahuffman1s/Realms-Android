package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updaterDataStore by preferencesDataStore(name = "realms_updater")

/**
 * DataStore-backed preferences for the auto-update system. Mirrors the
 * CheatsStore pattern (Flow read, suspend write). Dismissed versions are
 * bounded to 10 most-recent to prevent unbounded growth.
 */
class UpdatePrefs(private val context: Context) {

    private val keyChannel = stringPreferencesKey("channel")
    private val keyLastCheckedAt = longPreferencesKey("last_checked_at")
    private val keyDismissedVersions = stringSetPreferencesKey("dismissed_versions")
    private val keyDismissedOrder = stringPreferencesKey("dismissed_order")
    private val keyLastKnownTag = stringPreferencesKey("last_known_release_tag")

    val channel: Flow<UpdateChannel> = context.updaterDataStore.data
        .map { UpdateChannel.fromString(it[keyChannel]) }

    val lastCheckedAt: Flow<Long?> = context.updaterDataStore.data
        .map { it[keyLastCheckedAt] }

    val dismissedVersions: Flow<Set<String>> = context.updaterDataStore.data
        .map { it[keyDismissedVersions] ?: emptySet() }

    val lastKnownReleaseTag: Flow<String?> = context.updaterDataStore.data
        .map { it[keyLastKnownTag] }

    suspend fun setChannel(channel: UpdateChannel) {
        context.updaterDataStore.edit { it[keyChannel] = channel.name }
    }

    suspend fun setLastCheckedAt(epochMillis: Long) {
        context.updaterDataStore.edit { it[keyLastCheckedAt] = epochMillis }
    }

    suspend fun setLastKnownReleaseTag(tag: String?) {
        context.updaterDataStore.edit {
            if (tag == null) it.remove(keyLastKnownTag) else it[keyLastKnownTag] = tag
        }
    }

    /** Dismiss [tag] from banner display. Maintains FIFO bound of 10 entries. */
    suspend fun dismissVersion(tag: String) {
        context.updaterDataStore.edit { mutable ->
            val orderRaw = mutable[keyDismissedOrder] ?: ""
            val order = if (orderRaw.isEmpty()) mutableListOf() else orderRaw.split('|').toMutableList()
            order.remove(tag)
            order.add(tag)
            while (order.size > MAX_DISMISSED) order.removeAt(0)
            mutable[keyDismissedOrder] = order.joinToString("|")
            mutable[keyDismissedVersions] = order.toSet()
        }
    }

    /** TEST ONLY: clear all keys. */
    suspend fun clearForTest() {
        context.updaterDataStore.edit { it.clear() }
    }

    companion object {
        const val MAX_DISMISSED = 10
    }
}
