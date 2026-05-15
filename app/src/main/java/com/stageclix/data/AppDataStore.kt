package com.stageclix.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "stageclix_data"
)

class AppDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val APP_DATA_KEY = stringPreferencesKey("app_data")

    val appDataFlow: Flow<AppData> = context.dataStore.data.map { prefs ->
        val raw = prefs[APP_DATA_KEY]
        if (raw == null) {
            AppData(
                setlists = listOf(
                    Setlist(
                        name = "My Setlist",
                        songs = listOf(Song(name = "Song 1"))
                    )
                )
            ).also { saveAppData(it) }
        } else {
            runCatching {
                json.decodeFromString<AppData>(raw)
            }.getOrDefault(AppData())
        }
    }

    suspend fun saveAppData(data: AppData) {
        context.dataStore.edit { prefs ->
            prefs[APP_DATA_KEY] = json.encodeToString(data)
        }
    }
}
