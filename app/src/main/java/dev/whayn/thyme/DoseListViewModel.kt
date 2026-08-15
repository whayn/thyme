package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.data.Dose
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

private val sampleDoses = listOf(
    Dose(medication = "Paracetamol", time = LocalTime.of(12, 0)),
    Dose(medication = "Omeprazole", time = LocalTime.of(16, 0)),
    Dose(medication = "Sertraline", time = LocalTime.of(8, 0)),
)

class DoseListViewModel(private val dao: DoseDao) : ViewModel() {
    val doses: StateFlow<List<Dose>> = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            if (dao.count() == 0) dao.insertAll(sampleDoses)
        }
    }

    fun toggle(dose: Dose) {
        viewModelScope.launch {
            dao.setTaken(dose.id, !dose.taken)
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                DoseListViewModel(ThymeDatabase.get(context).doseDao())
            }
        }
    }
}