package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
) : ViewModel() {

    val settings: StateFlow<ThymeSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThymeSettings(),
        )

    fun setThemeMode(mode: ThymeThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    /** Debug-only: wipes real data and replaces it with fixtures for trying out Stats. */
    fun seedFakeData() {
        viewModelScope.launch {
            dao.clearAllData()
            DevSeeder.seed(dao)
        }
    }

    fun clearAllData() {
        viewModelScope.launch { dao.clearAllData() }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                SettingsViewModel(SettingsRepository.get(context), ThymeDatabase.get(context).doseDao())
            }
        }
    }
}
