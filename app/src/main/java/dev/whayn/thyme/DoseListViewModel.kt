package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.DoseLog
import dev.whayn.thyme.data.ThymeDatabase
import dev.whayn.thyme.data.TodayDose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime


class DoseListViewModel(private val dao: DoseDao) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val doses: StateFlow<List<TodayDose>> = _date
        .flatMapLatest { day -> dao.observeDosesFor(day) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun refreshDate() {
        _date.value = LocalDate.now()
    }
 
    fun toggle(item: TodayDose) {
        val day = _date.value
        viewModelScope.launch {
            if (item.taken) {
                dao.deleteLog(item.scheduled.id, day)
            } else {
                dao.insertLog(
                    DoseLog(
                        scheduledDoseId = item.scheduled.id,
                        forDate = day,
                        takenAt = Instant.now(),
                    )
                )
            }
        }
    }

    fun addDose(name: String, strength: String?, time: LocalTime, quantity: Double = 1.0) {
        viewModelScope.launch {
            dao.addDose(
                name = name.trim(), // Sanitization
                strength = strength?.trim()?.ifBlank { null },
                time = time,
                quantity = quantity,
            )
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