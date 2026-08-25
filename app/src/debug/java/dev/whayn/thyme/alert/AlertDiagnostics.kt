package dev.whayn.thyme.alert

import android.content.Context
import android.os.Build
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.SettingsRepository
import dev.whayn.thyme.data.ThymeDatabase
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the alert system currently believes, as one block of text.
 *
 * Debug builds only. Exists because the alert state machine is spread across
 * four places that each answer a different question - `dose_alerts` holds the
 * snooze counts, DataStore holds the settings and the recorded next fire,
 * AlarmManager holds the actual pending alarm, and [AlertPlan] holds the
 * opinion about what *should* be next - and every real bug so far has been two
 * of those four disagreeing. Reading them one at a time through the Database
 * Inspector, `dumpsys alarm` and the log is how those bugs stayed hidden.
 *
 * So the dump puts them side by side and says so out loud when they diverge.
 */
object AlertDiagnostics {

    /** Marks a line that is a disagreement rather than a fact. */
    private const val WARN = "  <!> "

    private val timeOnly = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val minuteOnly = DateTimeFormatter.ofPattern("HH:mm")
    private val full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun dump(context: Context, days: Int = 2): String {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val dao = ThymeDatabase.get(context).doseDao()
        val scheduler = AlarmScheduler.get(context)
        val settings = SettingsRepository.get(context).currentAlertSettings()

        return buildString {
            head("Thyme alert diagnostics")
            line("now", "${full.format(now.atZone(zone))}  $zone")
            line("device", "${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")

            section("permissions")
            AlertPermissions.statuses(context).forEach { status ->
                line(status.requirement.title, if (status.granted) "granted" else "DENIED")
                if (!status.granted) appendLine(WARN + status.requirement.consequence)
            }

            section("alarm")
            val armed = scheduler.mainAlarmExists()
            line("main PendingIntent", if (armed) "present" else "ABSENT")
            line("exact alarms usable", yesNo(scheduler.canScheduleExact()))
            line("which API", if (settings.useAlarmClock) "setAlarmClock" else "setExactAndAllowWhileIdle")

            val recorded = settings.nextFireAtMillis.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
            line("recorded next fire", recorded?.let { "${full.format(it.atZone(zone))} (${rel(now, it)})" } ?: "none")
            line(
                "last re-arm",
                settings.lastRearmAtMillis.takeIf { it > 0L }
                    ?.let { Instant.ofEpochMilli(it) }
                    ?.let { "${full.format(it.atZone(zone))} (${rel(now, it)})" }
                    ?: "never",
            )

            // The planner is re-run here rather than trusted from the last
            // re-arm, because "what it decided then" and "what it would decide
            // now" are different questions and only the gap between them is
            // diagnostic.
            val planned = runCatching { scheduler.plan(now, zone) }
            planned.onFailure { line("planner", "THREW: $it") }
            planned.onSuccess { next ->
                line(
                    "planner says next",
                    next?.let {
                        "doses=${it.doseIds} at ${full.format(it.fireAt.atZone(zone))} " +
                            "(${rel(now, it.fireAt)}) tier=${it.tier} " +
                            "critical=${it.anyCritical} forDate=${it.forDate} key=${it.groupKey}"
                    } ?: "nothing pending",
                )
                if (next != null && !armed) {
                    appendLine(WARN + "something to fire, but no alarm is registered with the OS")
                }
                if (next == null && armed) {
                    appendLine(WARN + "nothing to fire, but an alarm is still registered")
                }
                // A recorded time in the past that the planner has moved on
                // from is the fingerprint of the re-fire loop: the alarm was
                // aimed at an instant already gone.
                if (next != null && recorded != null && next.fireAt != recorded) {
                    appendLine(
                        WARN + "planner (${timeOnly.format(next.fireAt.atZone(zone))}) disagrees " +
                            "with the last armed instant (${timeOnly.format(recorded.atZone(zone))}) " +
                            "- something changed without re-arming",
                    )
                }
                if (next != null && next.fireAt.isBefore(now.minus(AlertPlan.CATCH_UP))) {
                    appendLine(WARN + "planned instant is older than the catch-up window")
                }
            }

            section("ringer")
            line(
                "state",
                AlertRingerService.ringing.value?.let {
                    "RINGING doses=${it.doseIds} tier=${it.tier} " +
                        "critical=${it.critical} forDate=${it.forDate} key=${it.groupKey}"
                } ?: "idle",
            )

            section("settings")
            line("snooze", "${settings.snoozeMinutes} min")
            line("ring for", "${settings.ringSeconds} s")
            line("max auto repeats", settings.maxAutoRepeats.toString())
            line("repeat until answered", yesNo(settings.repeatUntilAnswered))
            line("escalation", if (settings.escalationEnabled) "after ${settings.escalateAfterMinutes} min" else "off")
            line("default tier", settings.defaultAlertTier.name)
            line("setup completed", yesNo(settings.setupCompleted))

            // The alert rows are the state machine itself. Retention is 7 days,
            // so this is the whole table, not a window onto it.
            val alerts = dao.alertsFrom(today.minusDays(7)).sortedBy { it.nextFireAt }
            section("dose_alerts (${alerts.size})")
            if (alerts.isEmpty()) {
                appendLine("  none - every dose is pending at its scheduled instant")
            } else {
                appendLine("  dose  forDate     state    next fire  snoozes  first fired")
                alerts.forEach { a ->
                    appendLine(
                        "  " + a.scheduledDoseId.toString().padStart(4) +
                            "  " + a.forDate +
                            "  " + a.state.name.padEnd(7) +
                            "  " + timeOnly.format(a.nextFireAt.atZone(zone)) +
                            "   " + "${a.snoozeCount}/${settings.maxAutoRepeats}".padEnd(7) +
                            "  " + (a.firstFiredAt?.let { timeOnly.format(it.atZone(zone)) } ?: "-"),
                    )
                }
            }

            for (offset in 0..days) {
                val date = today.plusDays(offset.toLong())
                val doses = dao.dosesFor(date)
                val label = when (offset) {
                    0 -> "$date (today)"
                    1 -> "$date (tomorrow)"
                    else -> "$date"
                }
                section("doses $label (${doses.size})")
                if (doses.isEmpty()) {
                    appendLine("  nothing due")
                    continue
                }
                appendLine("  id    time   tier    crit  state")
                doses.forEach { d ->
                    val fireAt = AlertPlan.instantOf(date, d.scheduled.time, zone)
                    val state = when {
                        d.resolved -> "${d.outcome}"
                        d.alertTier == AlertTier.NONE -> "silent"
                        alerts.any { it.scheduledDoseId == d.scheduled.id && it.forDate == date } ->
                            "handled (see above)"
                        fireAt.isBefore(now.minus(AlertPlan.CATCH_UP)) -> "missed, outside catch-up"
                        fireAt.isBefore(now) -> "DUE NOW (inside catch-up)"
                        else -> "pending ${rel(now, fireAt)}"
                    }
                    appendLine(
                        "  " + d.scheduled.id.toString().padStart(4) +
                            "  " + minuteOnly.format(d.scheduled.time) +
                            "  " + d.alertTier.name.padEnd(6) +
                            "  " + (if (d.critical) "yes " else "no  ") +
                            "  " + state + "  " + d.medicationName,
                    )
                }
            }
            appendLine("-".repeat(60))
        }
    }

    private fun StringBuilder.head(title: String) {
        appendLine()
        appendLine("-".repeat(60))
        appendLine(title)
        appendLine("-".repeat(60))
    }

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("== $title ".padEnd(60, '='))
    }

    private fun StringBuilder.line(label: String, value: String) {
        appendLine("  " + label.padEnd(22) + value)
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    /**
     * Relative time, spelled out. Written by hand rather than with
     * `Duration.toMinutesPart`, which is API 31+ and would compile fine against
     * this project's minSdk 26 only to crash on an older device.
     */
    private fun rel(from: Instant, to: Instant): String {
        val delta = Duration.between(from, to)
        val total = delta.abs().seconds
        val text = when {
            total >= 3600 -> "%dh %02dm".format(total / 3600, (total % 3600) / 60)
            total >= 60 -> "%dm %02ds".format(total / 60, total % 60)
            else -> "${total}s"
        }
        return if (delta.isNegative) "$text ago" else "in $text"
    }
}
