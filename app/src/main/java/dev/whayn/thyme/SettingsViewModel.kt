package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.alert.AlarmScheduler
import dev.whayn.thyme.data.AlertSettings
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DevSeeder
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.SettingsRepository
import dev.whayn.thyme.data.ThymeDatabase
import dev.whayn.thyme.data.ThymeSettings
import dev.whayn.thyme.data.ThymeThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val dao: DoseDao,
    private val alarms: AlarmScheduler,
) : ViewModel() {

    val settings: StateFlow<ThymeSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThymeSettings(),
        )

    val alertSettings: StateFlow<AlertSettings> = repository.alertSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlertSettings(),
        )

    fun setThemeMode(mode: ThymeThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { repository.setSnoozeMinutes(minutes) }
    }

    fun setRingSeconds(seconds: Int) {
        viewModelScope.launch { repository.setRingSeconds(seconds) }
    }

    fun setEscalationEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEscalationEnabled(enabled) }
    }

    fun setRepeatUntilAnswered(enabled: Boolean) {
        viewModelScope.launch { repository.setRepeatUntilAnswered(enabled) }
    }

    fun setUseAlarmClock(enabled: Boolean) {
        viewModelScope.launch {
            repository.setUseAlarmClock(enabled)
            // The pending alarm was armed with the old API; re-arm to swap it.
            alarms.rearm("alarm-api-changed")
        }
    }

    fun setDefaultAlertTier(tier: AlertTier) {
        viewModelScope.launch { repository.setDefaultAlertTier(tier) }
    }

    fun dismissOemGuidance() {
        viewModelScope.launch { repository.setOemGuidanceDismissed(true) }
    }

    fun testAlarm() {
        viewModelScope.launch { alarms.fireTestAlarm() }
    }

    /** Debug-only: wipes real data and replaces it with fixtures for trying out Stats. */
    fun seedFakeData() {
        viewModelScope.launch {
            dao.clearAllData()
            DevSeeder.seed(dao)
            alarms.rearm("seeded")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            dao.clearAllData()
            alarms.rearm("cleared")
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    SettingsRepository.get(context),
                    ThymeDatabase.get(context).doseDao(),
                    AlarmScheduler.get(context),
                )
            }
        }
    }
}
