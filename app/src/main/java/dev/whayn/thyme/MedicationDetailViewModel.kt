package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.alert.AlarmScheduler
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.MedicationWithRegimens
import dev.whayn.thyme.data.ThymeDatabase
import dev.whayn.thyme.data.TodayDose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** How this medication has actually been taken, over [windowDays] up to today. */
data class MedicationHistory(
    val taken: Int,
    val expected: Int,
    val windowDays: Int,
    /** The next dose still due today, if there is one. */
    val nextDoseToday: LocalTime?,
) {
    val percent: Float get() = if (expected == 0) 0f else taken.toFloat() / expected

    companion object {
        val Empty = MedicationHistory(taken = 0, expected = 0, windowDays = 0, nextDoseToday = null)
    }
}

data class MedicationDetailState(
    val loading: Boolean,
    val medication: MedicationWithRegimens?,
    val history: MedicationHistory = MedicationHistory.Empty,
)

class MedicationDetailViewModel(
    private val dao: DoseDao,
    private val alarms: AlarmScheduler,
    private val medicationId: Long,
) : ViewModel() {

    /**
     * Adherence for this one medication, from the same per-day query the rest of
     * the app uses. Filtering [TodayDose] by medication id rather than adding a
     * dedicated aggregate keeps the recurrence rules in exactly one place (the
     * `WHERE` clause in `observeDosesForDate`) instead of restating them in SQL
     * that would then have to be kept in sync.
     */
    private val history: Flow<MedicationHistory> = run {
        val today = LocalDate.now()
        val dates = (0 until HISTORY_DAYS).map { today.minusDays(it.toLong()) }
        combine(dates.map(dao::observeDosesFor)) { perDay ->
            var taken = 0
            var expected = 0
            perDay.forEach { doses ->
                doses.filter { it.medicationId == medicationId }.forEach { dose ->
                    expected++
                    if (dose.taken) taken++
                }
            }
            // perDay[0] is today, because `dates` counts backwards from it.
            val now = LocalTime.now()
            val nextToday = perDay.firstOrNull()
                // `resolved`, not `taken`: a dose you deliberately skipped is
                // settled, and would otherwise be offered as "next up" forever.
                ?.filter { it.medicationId == medicationId && !it.resolved }
                ?.map { it.scheduled.time }
                ?.filter { it > now }
                ?.minOrNull()

            MedicationHistory(
                taken = taken,
                expected = expected,
                windowDays = HISTORY_DAYS,
                nextDoseToday = nextToday,
            )
        }
    }

    val state: StateFlow<MedicationDetailState> =
        combine(dao.observeMedication(medicationId), history) { medication, history ->
            MedicationDetailState(loading = false, medication = medication, history = history)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MedicationDetailState(loading = true, medication = null),
        )

    /**
     * Changes how loudly this medication asks for attention.
     *
     * Tier and critical are deliberately separate: tier is how the alert
     * *arrives*, critical is how you get *out* of it. Setting a medication to
     * Off does not make it non-critical, and a quiet medication can still
     * refuse to be skipped without giving a reason.
     */
    fun setAlertSettings(tier: AlertTier, critical: Boolean) {
        viewModelScope.launch {
            dao.updateAlertSettings(medicationId, tier, critical)
            // Switching to Off can remove the very dose the alarm is aimed at.
            alarms.rearm("alert-settings")
        }
    }

    /** Ends every active course today; past days keep their history. */
    fun stopAll() {
        viewModelScope.launch {
            dao.stopMedication(medicationId, LocalDate.now())
            alarms.rearm("medication-stopped")
        }
    }

    /** Soft-deletes the whole medication, then tells the screen to leave. */
    fun deleteMedication(onDeleted: () -> Unit) {
        viewModelScope.launch {
            dao.deleteMedication(medicationId)
            alarms.rearm("medication-deleted")
            onDeleted()
        }
    }

    companion object {
        private const val HISTORY_DAYS = 30

        fun factory(context: Context, medicationId: Long) = viewModelFactory {
            initializer {
                MedicationDetailViewModel(
                    ThymeDatabase.get(context).doseDao(),
                    AlarmScheduler.get(context),
                    medicationId,
                )
            }
        }
    }
}
