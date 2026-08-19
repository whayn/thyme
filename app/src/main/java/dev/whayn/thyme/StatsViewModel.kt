package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.ThymeDatabase
import dev.whayn.thyme.data.TodayDose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class StatsWindow(val days: Int, val label: String) {
    Week(7, "7d"),
    Month(30, "30d"),
    Quarter(90, "90d"),
}

data class MedicationAdherence(
    val medicationId: Long,
    val name: String,
    val colorIndex: Int,
    val taken: Int,
    val expected: Int,
) {
    val percent: Float get() = if (expected == 0) 0f else taken.toFloat() / expected
}

data class StatsSummary(
    val taken: Int,
    val expected: Int,
    val streakDays: Int,
    val perMedication: List<MedicationAdherence>,
) {
    val percent: Float get() = if (expected == 0) 0f else taken.toFloat() / expected

    companion object {
        val Empty = StatsSummary(taken = 0, expected = 0, streakDays = 0, perMedication = emptyList())
    }
}

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val doses: List<TodayDose>,
)

data class StatsState(
    val loading: Boolean,
    val summary: StatsSummary,
    val calendarDays: List<CalendarDay>,
)

class StatsViewModel(private val dao: DoseDao) : ViewModel() {

    private val _window = MutableStateFlow(StatsWindow.Month)
    val window: StateFlow<StatsWindow> = _window.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val calendarDays: Flow<List<CalendarDay>> = _month
        .flatMapLatest { month ->
            val dates = gridDatesFor(month)
            combine(dates.map(dao::observeDosesFor)) { perDay ->
                dates.zip(perDay.toList()) { date, doses ->
                    CalendarDay(date = date, inMonth = date.month == month.month, doses = doses)
                }
            }
        }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    /** Complete Monday-Sunday weeks spanning [month], so the grid never has a ragged first/last row. */
    private fun gridDatesFor(month: YearMonth): List<LocalDate> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val summary: Flow<StatsSummary> = _window
        .flatMapLatest { window ->
            val today = LocalDate.now()
            // Newest first, so buildSummary can walk the streak front-to-back.
            val dates = (0 until window.days).map { today.minusDays(it.toLong()) }
            combine(dates.map(dao::observeDosesFor)) { perDay -> buildSummary(perDay.toList()) }
        }

    val state: StateFlow<StatsState> =
        combine(summary, calendarDays) { summary, days ->
            StatsState(loading = false, summary = summary, calendarDays = days)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsState(
                loading = true,
                summary = StatsSummary.Empty,
                calendarDays = emptyList(),
            ),
        )

    fun selectWindow(window: StatsWindow) {
        _window.value = window
    }

    /**
     * [perDay] is newest-first, one entry per day in the window. Days with no
     * scheduled doses are skipped for the streak, since nothing to take neither
     * extends nor breaks it, so the walk stops at the first day that had a
     * dose and wasn't fully taken.
     *
     * Today is the exception: it is still in progress. An unfinished today used
     * to zero the streak outright, which meant the number read 0 for most of
     * every day. It now only ever *extends* the streak, never breaks it.
     */
    private fun buildSummary(perDay: List<List<TodayDose>>): StatsSummary {
        var taken = 0
        var expected = 0
        val perMedication = LinkedHashMap<Long, MedicationAdherence>()

        perDay.forEach { doses ->
            doses.forEach { dose ->
                expected++
                if (dose.taken) taken++
                val existing = perMedication[dose.medicationId]
                perMedication[dose.medicationId] = MedicationAdherence(
                    medicationId = dose.medicationId,
                    name = dose.medicationName,
                    colorIndex = dose.colorIndex,
                    taken = (existing?.taken ?: 0) + if (dose.taken) 1 else 0,
                    expected = (existing?.expected ?: 0) + 1,
                )
            }
        }

        fun complete(doses: List<TodayDose>) = doses.isEmpty() || doses.all { it.taken }

        val todayCounts = perDay.firstOrNull()?.let { it.isNotEmpty() && complete(it) } == true
        val streakDays = perDay
            .drop(if (todayCounts) 0 else 1)
            .takeWhile(::complete)
            .count { doses -> doses.isNotEmpty() }

        return StatsSummary(
            taken = taken,
            expected = expected,
            streakDays = streakDays,
            perMedication = perMedication.values.sortedByDescending { it.expected },
        )
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { StatsViewModel(ThymeDatabase.get(context).doseDao()) }
        }
    }
}
