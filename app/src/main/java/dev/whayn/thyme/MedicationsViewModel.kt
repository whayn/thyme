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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MedicationsState(
    val loading: Boolean,
    val medications: List<MedicationWithRegimens>,
)

class MedicationsViewModel(private val dao: DoseDao) : ViewModel() {

    val medications: StateFlow<MedicationsState> = dao.observeMedications()
        .map { MedicationsState(loading = false, medications = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MedicationsState(loading = true, medications = emptyList()),
        )

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { MedicationsViewModel(ThymeDatabase.get(context).doseDao()) }
        }
    }
}
