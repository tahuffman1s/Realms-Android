package com.realmsoffate.game.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsDataStore by preferencesDataStore(name = "rpg_prefs")

class PreferencesStore(private val context: Context) {
    private val keyApi = stringPreferencesKey("rpg_api_key")
    private val keyProvider = stringPreferencesKey("rpg_provider")

    val provider: Flow<AiProvider> = context.prefsDataStore.data.map { prefs ->
        AiProvider.from(prefs[keyProvider])
    }

    val apiKey: Flow<String> = context.prefsDataStore.data.map { it[keyApi].orEmpty() }

    suspend fun save(provider: AiProvider, apiKey: String) {
        context.prefsDataStore.edit {
            it[keyProvider] = provider.id
            it[keyApi] = apiKey
        }
    }

}
