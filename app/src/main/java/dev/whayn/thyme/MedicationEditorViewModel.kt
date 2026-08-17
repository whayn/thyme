package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.DoseTime
import dev.whayn.thyme.data.Medication
import dev.whayn.thyme.data.Recurrence
import dev.whayn.thyme.data.Regimen
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class RecurrenceChoice { EveryDay, CertainDays, EveryInterval, Cycle }

/**
 * A time row while it is being edited. Quantity is held as raw *text* rather
 * than a Double: a field bound to a parsed number can never be cleared or hold
 * a half-typed value like "0.", so decimals become impossible to enter.
 */
data class EditableDose(
    /** Originating scheduled_doses row; 0 means new. Carried so a save can move
     *  a time in place instead of deleting and re-adding it. */
    val id: Long = 0,
    val time: LocalTime,
    val quantityText: String = "1",
)

data class MedicationEditorState(
    val loading: Boolean = true,
    val medication: Medication? = null,
    val regimen: Regimen = Regimen(medicationId = 0, startDate = LocalDate.now()),
    val times: List<EditableDose> = listOf(EditableDose(time = LocalTime.of(8, 0))),
    val recurrenceChoice: RecurrenceChoice = RecurrenceChoice.EveryDay,
    val dirty: Boolean = false,
    val saved: Boolean = false,
) {
    /** Two rows at the same time would silently collapse into one on save. */
    val hasDuplicateTimes: Boolean
        get() = times.map { it.time }.toSet().size != times.size

    val quantitiesValid: Boolean
        get() = times.all { (it.quantityText.toDoubleOrNull() ?: 0.0) > 0.0 }

    /** A cycle missing either half reads as "no cycle" in SQL — i.e. every day. */
    val cycleValid: Boolean
        get() = recurrenceChoice != RecurrenceChoice.Cycle ||
                (regimen.cycleOnDays != null && regimen.cycleOffDays != null)

    /** An empty day mask matches nothing, so the course would never appear. */
    val daysValid: Boolean
        get() = recurrenceChoice != RecurrenceChoice.CertainDays || regimen.daysOfWeek != 0

    val endsBeforeItStarts: Boolean
        get() = regimen.endDate?.isBefore(regimen.startDate) == true

    val canSave: Boolean
        get() = !medication?.name.isNullOrBlank() &&
                times.isNotEmpty() &&
                !hasDuplicateTimes &&
                quantitiesValid &&
                cycleValid &&
                daysValid &&
                !endsBeforeItStarts
}

/**
 * Drives three cases, distinguished by which ids arrive:
 *  - both null            → a brand new medication with its first course
 *  - medication, no regimen → another course of a drug already on file
 *  - both set             → editing that course
 */
class MedicationEditorViewModel(
    private val dao: DoseDao,
    private val medicationId: Long?,
    private val regimenId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        MedicationEditorState(
            loading = medicationId != null,
            medication = if (medicationId == null) Medication(name = "") else null,
        )
    )
    val state: StateFlow<MedicationEditorState> = _state.asStateFlow()

    init {
        if (medicationId != null) {
            viewModelScope.launch {
                val loaded = dao.getMedication(medicationId)
                if (loaded == null) {
                    _state.update { it.copy(loading = false) }
                    return@launch
                }
                val existing = regimenId?.let { id ->
                    loaded.regimens.firstOrNull { it.regimen.id == id }
                }
                _state.value = if (existing != null) {
                    MedicationEditorState(
                        loading = false,
                        medication = loaded.medication,
                        regimen = existing.regimen,
                        times = existing.activeDoses
                            .map { EditableDose(it.id, it.time, formatQuantity(it.quantity)) }
                            .ifEmpty { listOf(EditableDose(time = LocalTime.of(8, 0))) },
                        recurrenceChoice = choiceFor(existing.regimen),
                    )
                } else {
                    // Adding a course: keep the drug's identity, start a fresh schedule.
                    MedicationEditorState(
                        loading = false,
                        medication = loaded.medication,
                        regimen = Regimen(
                            medicationId = medicationId,
                            startDate = LocalDate.now(),
                        ),
                        times = listOf(EditableDose(time = LocalTime.of(8, 0))),
                        recurrenceChoice = RecurrenceChoice.EveryDay,
                    )
                }
            }
        } else {
            _state.value = _state.value.copy(loading = false)
        }
    }

    fun setMedication(medication: Medication) {
        _state.update { it.copy(medication = medication, dirty = true) }
    }

    fun setRegimen(regimen: Regimen) {
        _state.update { it.copy(regimen = regimen, dirty = true) }
    }

    fun setTimes(times: List<EditableDose>) {
        _state.update { it.copy(times = times, dirty = true) }
    }

    /** Picks the next free hour so adding twice never creates a duplicate. */
    fun addTime() {
        _state.update { current ->
            val used = current.times.map { it.time }.toSet()
            val next = generateSequence(LocalTime.of(12, 0)) { it.plusHours(1) }
                .take(24)
                .firstOrNull { it !in used }
                ?: LocalTime.of(12, 0)
            current.copy(times = current.times + EditableDose(time = next), dirty = true)
        }
    }

    fun setChoice(choice: RecurrenceChoice) {
        _state.update { current ->
            val old = current.regimen
            val next = when (choice) {
                RecurrenceChoice.EveryDay -> old.copy(
                    daysOfWeek = Recurrence.EVERY_DAY,
                    intervalDays = 1,
                    cycleOnDays = null,
                    cycleOffDays = null,
                )

                RecurrenceChoice.CertainDays -> old.copy(
                    daysOfWeek = if (old.daysOfWeek == Recurrence.EVERY_DAY) Recurrence.WEEKDAYS
                    else old.daysOfWeek,
                    intervalDays = 1,
                    cycleOnDays = null,
                    cycleOffDays = null,
                )

                RecurrenceChoice.EveryInterval -> old.copy(
                    daysOfWeek = Recurrence.EVERY_DAY,
                    intervalDays = old.intervalDays.coerceAtLeast(2),
                    cycleOnDays = null,
                    cycleOffDays = null,
                )

                RecurrenceChoice.Cycle -> old.copy(
                    daysOfWeek = Recurrence.EVERY_DAY,
                    intervalDays = 1,
                    cycleOnDays = old.cycleOnDays ?: 21,
                    cycleOffDays = old.cycleOffDays ?: 7,
                )
            }
            current.copy(regimen = next, recurrenceChoice = choice, dirty = true)
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = state.value
        if (!current.canSave) return
        val medication = current.medication ?: return

        // Normalise at the boundary so " Paracetamol " and an empty strength
        // never reach the database.
        val cleaned = medication.copy(
            name = medication.name.trim(),
            strength = medication.strength?.trim()?.ifBlank { null },
        )
        val times = current.times.map {
            DoseTime(it.id, it.time, it.quantityText.toDoubleOrNull() ?: 1.0)
        }

        viewModelScope.launch {
            dao.saveMedication(cleaned, current.regimen, times)
            _state.update { it.copy(saved = true, dirty = false) }
            onSaved()
        }
    }

    private fun choiceFor(regimen: Regimen): RecurrenceChoice = when {
        regimen.cycleOnDays != null && regimen.cycleOffDays != null -> RecurrenceChoice.Cycle
        regimen.intervalDays > 1 -> RecurrenceChoice.EveryInterval
        regimen.daysOfWeek == Recurrence.EVERY_DAY -> RecurrenceChoice.EveryDay
        else -> RecurrenceChoice.CertainDays
    }

    companion object {
        fun factory(context: Context, medicationId: Long?, regimenId: Long?) = viewModelFactory {
            initializer {
                MedicationEditorViewModel(
                    dao = ThymeDatabase.get(context).doseDao(),
                    medicationId = medicationId,
                    regimenId = regimenId,
                )
            }
        }
    }
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
