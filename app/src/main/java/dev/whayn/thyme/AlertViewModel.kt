package dev.whayn.thyme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.whayn.thyme.alert.AlertResponder
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.ThymeDatabase
import dev.whayn.thyme.data.TodayDose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Why a dose was not taken. "Already took it" is deliberately not a skip. */
enum class SkipReason(val label: String, val resolvesAsTaken: Boolean = false) {
    AlreadyTook("Already took it", resolvesAsTaken = true),
    NotNeeded("Not needed today"),
    OutOfStock("Don't have it with me"),
    Unwell("Feeling unwell"),
    Other("Something else"),
}

data class AlertUiState(
    val loading: Boolean = true,
    val doses: List<TodayDose> = emptyList(),
    /** Ticked in the list but not yet committed, so a group can be answered piecemeal. */
    val selected: Set<Long> = emptySet(),
    val forDate: LocalDate = LocalDate.now(),
    val tier: AlertTier = AlertTier.MEDIUM,
    val critical: Boolean = false,
    val snoozesLeft: Int = 0,
    val snoozeMinutes: Int = 10,
    val silenced: Boolean = false,
    /** Set briefly after snoozing, so the confirmation is readable before the screen goes. */
    val snoozedUntilLabel: String? = null,
    /** Step 1 of the high-friction skip: choose a reason. */
    val askingReason: Boolean = false,
    val chosenReason: SkipReason? = null,
    val reasonNote: String = "",
    val finished: Boolean = false,
) {
    val unresolved: List<TodayDose> get() = doses.filterNot { it.resolved }
    val allSelected: Boolean get() = unresolved.isNotEmpty() && selected.size == unresolved.size

    /**
     * Whether the friction applies to what is *currently ticked*, rather than to
     * the group as a whole.
     *
     * A critical medication should not drag the vitamin beside it through a
     * reason sheet, so the tertiary action reflects the selection: untick the
     * critical dose and the button becomes a plain one-tap Skip.
     */
    val selectedCritical: Boolean
        get() = doses.any { it.scheduled.id in selected && it.critical }
    val canConfirmReason: Boolean
        get() = chosenReason != null && (chosenReason != SkipReason.Other || reasonNote.isNotBlank())
}

class AlertViewModel(
    private val dao: DoseDao,
    private val context: Context,
    private val doseIds: List<Long>,
    private val forDate: LocalDate,
    private val tier: AlertTier,
    private val critical: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AlertUiState(forDate = forDate, tier = tier, critical = critical),
    )
    val state: StateFlow<AlertUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val doses = dao.dosesFor(forDate).filter { it.scheduled.id in doseIds }
            val settings = dev.whayn.thyme.data.SettingsRepository.get(context)
                .currentAlertSettings()
            _state.value = _state.value.copy(
                loading = false,
                doses = doses,
                // Everything starts ticked: the common case is taking all of
                // them, and un-ticking one is rarer than ticking three.
                selected = doses.filterNot { it.resolved }.map { it.scheduled.id }.toSet(),
                snoozesLeft = AlertResponder.snoozesLeft(context, doseIds, forDate, tier),
                snoozeMinutes = settings.snoozeMinutes,
            )
        }
    }

    fun toggleDose(id: Long) {
        val current = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (id in current) current - id else current + id,
        )
    }

    /** Stops the noise without deciding anything. Logs nothing, on purpose. */
    fun silence() {
        dev.whayn.thyme.alert.AlertRingerService.stop(context)
        _state.value = _state.value.copy(silenced = true)
    }

    fun takeSelected() {
        val ids = _state.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            AlertResponder.taken(context, ids, forDate)
            finishOrContinue(ids)
        }
    }

    fun snooze() {
        if (_state.value.snoozedUntilLabel != null) return  // already on the way out
        viewModelScope.launch {
            AlertResponder.snooze(context, doseIds, forDate, tier, automatic = false)
            dev.whayn.thyme.alert.AlertRingerService.stop(context)
            val until = LocalTime.now().plusMinutes(_state.value.snoozeMinutes.toLong())
            _state.update {
                it.copy(snoozedUntilLabel = until.format(snoozeFormatter), silenced = true)
            }
            // Long enough to read, short enough not to feel like a hang.
            delay(SNOOZE_CONFIRM_MS)
            close()
        }
    }

    /**
     * Back defers rather than dismisses.
     *
     * The alert still cannot be waved away without a record - a snooze is
     * written, counted against the cap, and comes back. But making the most
     * reflexive gesture on the phone do nothing at all read as a frozen screen,
     * and punished people for a habit rather than a decision.
     *
     * Returns false when there are no snoozes left, so the caller can say so
     * instead of silently ignoring the press.
     */
    fun snoozeFromBack(): Boolean {
        if (_state.value.snoozesLeft <= 0) return false
        snooze()
        return true
    }

    /** Non-critical doses skip in one tap; critical ones must give a reason first. */
    fun requestSkip() {
        if (_state.value.selectedCritical) {
            _state.value = _state.value.copy(askingReason = true)
        } else {
            confirmSkip(reason = null)
        }
    }

    fun chooseReason(reason: SkipReason) {
        _state.value = _state.value.copy(chosenReason = reason)
    }

    fun updateReasonNote(note: String) {
        _state.value = _state.value.copy(reasonNote = note)
    }

    fun cancelReason() {
        _state.value = _state.value.copy(
            askingReason = false, chosenReason = null, reasonNote = "",
        )
    }

    fun confirmChosenReason() {
        val state = _state.value
        val reason = state.chosenReason ?: return
        val text = if (reason == SkipReason.Other) state.reasonNote.trim() else reason.label
        // "Already took it" is by far the most common reason and it is not a
        // skip. Recording it as one would understate adherence for exactly the
        // people who are taking their medication properly.
        if (reason.resolvesAsTaken) {
            viewModelScope.launch {
                AlertResponder.taken(context, state.selected.ifEmpty { doseIds.toSet() }.toList(), forDate)
                close()
            }
        } else {
            confirmSkip(text)
        }
    }

    private fun confirmSkip(reason: String?) {
        val ids = _state.value.selected.ifEmpty { doseIds.toSet() }.toList()
        viewModelScope.launch {
            AlertResponder.skipped(context, ids, forDate, reason)
            finishOrContinue(ids)
        }
    }

    /** Answering part of a group leaves the rest on screen, still ringing. */
    private suspend fun finishOrContinue(answered: List<Long>) {
        val remaining = dao.dosesFor(forDate)
            .filter { it.scheduled.id in doseIds && !it.resolved }
        if (remaining.isEmpty()) {
            close()
        } else {
            _state.value = _state.value.copy(
                doses = dao.dosesFor(forDate).filter { it.scheduled.id in doseIds },
                selected = remaining.map { it.scheduled.id }.toSet(),
                askingReason = false,
                chosenReason = null,
                reasonNote = "",
            )
        }
    }

    private fun close() {
        dev.whayn.thyme.alert.AlertRingerService.stop(context)
        _state.value = _state.value.copy(finished = true)
    }

    companion object {
        private const val SNOOZE_CONFIRM_MS = 1_400L
        private val snoozeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        fun factory(
            context: Context,
            doseIds: List<Long>,
            forDate: LocalDate,
            tier: AlertTier,
            critical: Boolean,
        ) = viewModelFactory {
            initializer {
                AlertViewModel(
                    ThymeDatabase.get(context).doseDao(),
                    context.applicationContext,
                    doseIds,
                    forDate,
                    tier,
                    critical,
                )
            }
        }
    }
}
