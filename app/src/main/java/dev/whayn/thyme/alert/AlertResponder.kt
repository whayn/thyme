package dev.whayn.thyme.alert

import android.content.Context
import android.util.Log
import dev.whayn.thyme.data.AlertState
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseAlert
import dev.whayn.thyme.data.DoseOutcome
import dev.whayn.thyme.data.SettingsRepository
import dev.whayn.thyme.data.ThymeDatabase
import java.time.Instant
import java.time.LocalDate

/**
 * The one place a dose's alert is answered.
 *
 * Shared by the notification actions, the full-screen alert and the ring
 * timeout, so "taken" means the same thing however it was said - and so the
 * cleanup (clear the alert row, drop the notification, re-arm) can never be
 * half-done by one caller and not another.
 *
 * Every write takes [forDate] as a parameter rather than reading the clock. A
 * 23:00 alarm answered at 00:30 belongs to the previous day, and a dose snoozed
 * past midnight keeps its original date - recomputing here would file both
 * against the wrong day and quietly corrupt adherence.
 */
object AlertResponder {

    suspend fun taken(context: Context, doseIds: List<Long>, forDate: LocalDate) =
        resolve(context, doseIds, forDate, DoseOutcome.TAKEN, null)

    suspend fun skipped(
        context: Context,
        doseIds: List<Long>,
        forDate: LocalDate,
        reason: String?,
    ) = resolve(context, doseIds, forDate, DoseOutcome.SKIPPED, reason)

    private suspend fun resolve(
        context: Context,
        doseIds: List<Long>,
        forDate: LocalDate,
        outcome: DoseOutcome,
        reason: String?,
    ) {
        val dao = ThymeDatabase.get(context).doseDao()
        val at = Instant.now()
        doseIds.forEach { id ->
            dao.resolveDose(id, forDate, outcome, reason, at)
            dao.deleteAlert(id, forDate)
        }
        Log.i(AlertIds.TAG, "resolved $doseIds as $outcome for $forDate")
        AlarmScheduler.get(context).rearm("answered")
    }

    /**
     * Puts a group off, and reports whether it will actually come back.
     *
     * Returns false once the cap is reached: at that point the alert is retired
     * as EXPIRED and **no log row is written**, because an absent row already
     * means missed. Writing one would invent a decision nobody made.
     */
    suspend fun snooze(
        context: Context,
        doseIds: List<Long>,
        forDate: LocalDate,
        tier: AlertTier,
        automatic: Boolean,
    ): Boolean {
        val dao = ThymeDatabase.get(context).doseDao()
        val settings = SettingsRepository.get(context).currentAlertSettings()
        val now = Instant.now()

        // Strong may be set to keep asking indefinitely. Light never re-fires at
        // all - its notification simply stays in the tray - so an automatic
        // snooze there is a no-op.
        val unlimited = tier == AlertTier.STRONG && settings.repeatUntilAnswered
        if (automatic && tier == AlertTier.LIGHT) return false

        var willReturn = false
        doseIds.forEach { id ->
            val existing = dao.alertFor(id, forDate)
            val count = (existing?.snoozeCount ?: 0) + 1
            val capped = !unlimited && count > settings.maxAutoRepeats
            willReturn = willReturn || !capped

            dao.upsertAlert(
                DoseAlert(
                    id = existing?.id ?: 0,
                    scheduledDoseId = id,
                    forDate = forDate,
                    state = if (capped) AlertState.EXPIRED else AlertState.SNOOZED,
                    nextFireAt = now.plusSeconds(settings.snoozeMinutes * 60L),
                    dueAt = existing?.dueAt ?: now,
                    snoozeCount = count,
                    escalationStep = existing?.escalationStep ?: 0,
                    firstFiredAt = existing?.firstFiredAt ?: now,
                )
            )
        }

        Log.i(
            AlertIds.TAG,
            "snooze(auto=$automatic) $doseIds for $forDate -> willReturn=$willReturn",
        )
        AlarmScheduler.get(context).rearm("snoozed")
        return willReturn
    }

    /** How many more times this group may be put off, for the button label. */
    suspend fun snoozesLeft(
        context: Context,
        doseIds: List<Long>,
        forDate: LocalDate,
        tier: AlertTier,
    ): Int {
        val settings = SettingsRepository.get(context).currentAlertSettings()
        if (tier == AlertTier.STRONG && settings.repeatUntilAnswered) return Int.MAX_VALUE
        val dao = ThymeDatabase.get(context).doseDao()
        val used = doseIds.maxOfOrNull { dao.alertFor(it, forDate)?.snoozeCount ?: 0 } ?: 0
        return (settings.maxAutoRepeats - used).coerceAtLeast(0)
    }
}
