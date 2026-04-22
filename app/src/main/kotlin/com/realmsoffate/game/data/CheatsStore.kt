package com.realmsoffate.game.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cheatsDataStore by preferencesDataStore(name = "rpg_cheats")

class CheatsStore(private val context: Context) {
    private val keyEnabled = booleanPreferencesKey("cheats_enabled")
    private val keyUnnaturalTwenty = booleanPreferencesKey("cheat_unnatural_twenty")
    private val keyLoser = booleanPreferencesKey("cheat_loser")
    private val keyInfiniteGold = booleanPreferencesKey("cheat_infinite_gold")

    val enabled: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyEnabled] ?: false }
    val unnaturalTwenty: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyUnnaturalTwenty] ?: false }
    val loser: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyLoser] ?: false }
    val infiniteGold: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyInfiniteGold] ?: false }

    suspend fun unlock() {
        context.cheatsDataStore.edit { it[keyEnabled] = true }
    }

    suspend fun disable() {
        context.cheatsDataStore.edit {
            it[keyEnabled] = false
            it[keyUnnaturalTwenty] = false
            it[keyLoser] = false
            it[keyInfiniteGold] = false
        }
    }

    suspend fun setUnnaturalTwenty(on: Boolean) {
        context.cheatsDataStore.edit {
            it[keyUnnaturalTwenty] = on
            if (on) it[keyLoser] = false
        }
    }

    suspend fun setLoser(on: Boolean) {
        context.cheatsDataStore.edit {
            it[keyLoser] = on
            if (on) it[keyUnnaturalTwenty] = false
        }
    }

    suspend fun setInfiniteGold(on: Boolean) {
        context.cheatsDataStore.edit { it[keyInfiniteGold] = on }
    }
}
