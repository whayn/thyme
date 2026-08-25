package dev.whayn.thyme.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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

/**
 * How alerts behave. Kept apart from [ThymeSettings], which is threaded through
 * the theme: the scheduler and the ringer want a narrow surface they can read
 * from a suspend context, not the whole preferences object.
 */
data class AlertSettings(
    val snoozeMinutes: Int = 10,
    val ringSeconds: Int = 120,
    /** Manual and automatic snoozes share this cap, so snoozing cannot outlive it. */
    val maxAutoRepeats: Int = 3,
    /** Off by default: a gentle reminder should stay gentle unless asked otherwise. */
    val escalationEnabled: Boolean = false,
    val escalateAfterMinutes: Int = 10,
    /** Removes the repeat cap, for Strong only. */
    val repeatUntilAnswered: Boolean = false,
    /**
     * Use `setAlarmClock` instead of `setExactAndAllowWhileIdle`.
     *
     * Off by default. setAlarmClock is the more reliable of the two - no Doze
     * rate limit at all - but it puts an alarm icon in the status bar *and*
     * takes over the system's "next alarm", so a pill reminder would show up
     * where someone looks to check their wake-up time. That is a poor trade to
     * make on the user's behalf, so it is offered rather than assumed.
     */
    val useAlarmClock: Boolean = false,
    val defaultAlertTier: AlertTier = AlertTier.LIGHT,
    val setupCompleted: Boolean = false,
    val oemGuidanceDismissed: Boolean = false,
    /** Diagnostics only, for the reliability screen. */
    val nextFireAtMillis: Long = 0L,
    val lastRearmAtMillis: Long = 0L,
)

private val Context.settingsDataStore: DataStore<Preferences> by
preferencesDataStore(name = "thyme_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")

        val snoozeMinutes = intPreferencesKey("alert_snooze_minutes")
        val ringSeconds = intPreferencesKey("alert_ring_seconds")
        val maxAutoRepeats = intPreferencesKey("alert_max_repeats")
        val escalationEnabled = booleanPreferencesKey("alert_escalation_enabled")
        val escalateAfterMinutes = intPreferencesKey("alert_escalate_after_minutes")
        val repeatUntilAnswered = booleanPreferencesKey("alert_repeat_until_answered")
        val useAlarmClock = booleanPreferencesKey("alert_use_alarm_clock")
        val defaultAlertTier = stringPreferencesKey("alert_default_tier")
        val setupCompleted = booleanPreferencesKey("alert_setup_completed")
        val oemGuidanceDismissed = booleanPreferencesKey("alert_oem_dismissed")
        val nextFireAt = longPreferencesKey("alert_next_fire_at")
        val lastRearmAt = longPreferencesKey("alert_last_rearm_at")
    }

    val alertSettings: Flow<AlertSettings> = context.settingsDataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            val defaults = AlertSettings()
            AlertSettings(
                snoozeMinutes = prefs[Keys.snoozeMinutes] ?: defaults.snoozeMinutes,
                ringSeconds = prefs[Keys.ringSeconds] ?: defaults.ringSeconds,
                maxAutoRepeats = prefs[Keys.maxAutoRepeats] ?: defaults.maxAutoRepeats,
                escalationEnabled = prefs[Keys.escalationEnabled] ?: defaults.escalationEnabled,
                escalateAfterMinutes = prefs[Keys.escalateAfterMinutes]
                    ?: defaults.escalateAfterMinutes,
                repeatUntilAnswered = prefs[Keys.repeatUntilAnswered]
                    ?: defaults.repeatUntilAnswered,
                useAlarmClock = prefs[Keys.useAlarmClock] ?: defaults.useAlarmClock,
                defaultAlertTier = prefs[Keys.defaultAlertTier]
                    ?.let { stored -> runCatching { AlertTier.valueOf(stored) }.getOrNull() }
                    ?: defaults.defaultAlertTier,
                setupCompleted = prefs[Keys.setupCompleted] ?: defaults.setupCompleted,
                oemGuidanceDismissed = prefs[Keys.oemGuidanceDismissed]
                    ?: defaults.oemGuidanceDismissed,
                nextFireAtMillis = prefs[Keys.nextFireAt] ?: 0L,
                lastRearmAtMillis = prefs[Keys.lastRearmAt] ?: 0L,
            )
        }

    /** One-shot read, for the scheduler and the ringer. Both are already suspend. */
    suspend fun currentAlertSettings(): AlertSettings = alertSettings.first()

    suspend fun setSnoozeMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.snoozeMinutes] = minutes }
    }

    suspend fun setRingSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.ringSeconds] = seconds }
    }

    suspend fun setMaxAutoRepeats(count: Int) {
        context.settingsDataStore.edit { it[Keys.maxAutoRepeats] = count }
    }

    suspend fun setEscalationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.escalationEnabled] = enabled }
    }

    suspend fun setRepeatUntilAnswered(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.repeatUntilAnswered] = enabled }
    }

    suspend fun setUseAlarmClock(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.useAlarmClock] = enabled }
    }

    suspend fun setDefaultAlertTier(tier: AlertTier) {
        context.settingsDataStore.edit { it[Keys.defaultAlertTier] = tier.name }
    }

    suspend fun setSetupCompleted(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.setupCompleted] = done }
    }

    suspend fun setOemGuidanceDismissed(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.oemGuidanceDismissed] = done }
    }

    suspend fun recordRearm(nextFireAtMillis: Long, atMillis: Long) {
        context.settingsDataStore.edit {
            it[Keys.nextFireAt] = nextFireAtMillis
            it[Keys.lastRearmAt] = atMillis
        }
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
