package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.MedicationWithRegimens
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MedicationsViewModel(private val dao: DoseDao) : ViewModel() {

    val medications: StateFlow<List<MedicationWithRegimens>> = dao.observeMedications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Ends the course today. Past days keep showing what was actually taken. */
    fun stop(medicationId: Long) {
        viewModelScope.launch { dao.stopMedication(medicationId, LocalDate.now()) }
    }

    /** Hides it on every date. For entries added by mistake. */
    fun delete(medicationId: Long) {
        viewModelScope.launch { dao.deleteMedication(medicationId) }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { MedicationsViewModel(ThymeDatabase.get(context).doseDao()) }
        }
    }
}
