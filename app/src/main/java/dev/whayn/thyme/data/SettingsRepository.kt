package dev.whayn.thyme.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Which colours the app runs in. Lives in `data` rather than `ui.theme`
 * because it is persisted preference data dependencies point UI → data, never
 * the other way.
 */
enum class ThymeThemeMode { System, Light, Dark }

data class ThymeSettings(
    val themeMode: ThymeThemeMode = ThymeThemeMode.System,
    val dynamicColor: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by
preferencesDataStore(name = "thyme_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
    }

    val settings: Flow<ThymeSettings> = context.settingsDataStore.data
        // A corrupt or unreadable file should fall back to defaults, not crash
        // the app on launch.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            ThymeSettings(
                themeMode = prefs[Keys.themeMode]
                    ?.let { stored -> runCatching { ThymeThemeMode.valueOf(stored) }.getOrNull() }
                    ?: ThymeThemeMode.System,
                dynamicColor = prefs[Keys.dynamicColor] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThymeThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext)
                    .also { instance = it }
            }
    }
}
