package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.alert.AlarmScheduler
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.DoseOutcome
import dev.whayn.thyme.data.ThymeDatabase
import dev.whayn.thyme.data.TodayDose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class DoseListState(
    val loading: Boolean,
    val doses: List<TodayDose>,
)

class DoseListViewModel(
    private val dao: DoseDao,
    private val alarms: AlarmScheduler,
    initialDate: LocalDate? = null,
) : ViewModel() {

    /** null means "follow the real today"; a value pins the list to a chosen day. */
    private val _pinnedDate = MutableStateFlow(initialDate)
    private val _today = MutableStateFlow(LocalDate.now())
    private val _now = MutableStateFlow(LocalTime.now())

    val today: StateFlow<LocalDate> = _today.asStateFlow()
    val now: StateFlow<LocalTime> = _now.asStateFlow()

    val date: StateFlow<LocalDate> =
        combine(_pinnedDate, _today) { pinned, today -> pinned ?: today }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LocalDate.now(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val doses: StateFlow<DoseListState> = date
        .flatMapLatest { day -> dao.observeDosesFor(day).map { DoseListState(loading = false, doses = it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DoseListState(loading = true, doses = emptyList()),
        )

    /**
     * Read straight from the backing flows rather than from `date.value`:
     * `stateIn(WhileSubscribed)` stops updating once the UI detaches, so its
     * `.value` can be stale exactly when a write needs the truth.
     */
    private fun currentDate(): LocalDate = _pinnedDate.value ?: _today.value

    fun refreshClock() {
        _today.value = LocalDate.now()
        _now.value = LocalTime.now()
    }

    /** Selecting the Today tab re-pins the list to the real today. */
    fun showToday() {
        refreshClock()
        _pinnedDate.value = null
    }

    /** Picking today un-pins, so the list resumes following the real date. */
    fun selectDate(date: LocalDate) {
        _pinnedDate.value = if (date == _today.value) null else date
    }

    /**
     * Ticking a row on Today means "taken", and untitcking clears whatever was
     * there - a take *or* a skip - back to pending.
     *
     * Branching on `resolved` rather than `taken` is load-bearing: a skipped dose
     * has `taken == false` but already owns a row, so the old insert path would
     * hit the unique index on (scheduledDoseId, forDate) and throw. Skips can
     * only be *created* from the alert screen, where they cost some friction;
     * here they can only be cleared.
     */
    fun toggle(item: TodayDose) {
        val day = currentDate()
        viewModelScope.launch {
            if (item.resolved) {
                dao.deleteLog(item.scheduled.id, day)
            } else {
                dao.resolveDose(
                    scheduledDoseId = item.scheduled.id,
                    forDate = day,
                    outcome = DoseOutcome.TAKEN,
                )
            }
            alarms.rearm("dose-toggled")
        }
    }

    companion object {
        fun factory(context: Context, initialDate: LocalDate? = null) = viewModelFactory {
            initializer {
                DoseListViewModel(
                    ThymeDatabase.get(context).doseDao(),
                    AlarmScheduler.get(context),
                    initialDate,
                )
            }
        }
    }
}
