package dev.whayn.thyme.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.SettingsRepository
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Drives the alert system from the shell. Debug builds only - it lives in
 * `src/debug` and is registered by `src/debug/AndroidManifest.xml`, so it can
 * never reach a release APK.
 *
 * Exists because every other way in is either slow or lies. Waiting for a real
 * 08:00 dose is slow; driving the UI is worse (`adb shell input text` does not
 * raise the soft keyboard, and the alert screen deliberately swallows BACK, so
 * a scripted flow looks like a hang); and the two shortcuts that already
 * existed - `fireTestAlarm` and the old direct-deliver broadcast - both skip
 * the scheduler, which is where the bugs live.
 *
 * Use `tools/alert` rather than typing these by hand:
 *
 *   adb shell am broadcast -a dev.whayn.thyme.DEBUG \
 *     -n dev.whayn.thyme/.alert.AlertDebugReceiver \
 *     --es cmd arm --el seconds 30 --es tier STRONG --ez critical true
 */
class AlertDebugReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // The old single-purpose action keeps working, so anything already
        // scripted against it does not break.
        val cmd = intent.getStringExtra("cmd")
            ?: if (intent.action == LEGACY_FIRE) "fire" else "dump"

        val pending = goAsync()
        scope.launch {
            try {
                Log.i(AlertIds.TAG, "DEBUG $cmd ${intent.extras?.keySet()?.joinToString()}")
                when (cmd) {
                    "dump" -> dump(app, intent)
                    "arm" -> arm(app, intent)
                    "fire" -> fire(app, intent)
                    "rearm" -> rearm(app, intent)
                    "answer" -> answer(app, intent)
                    "snooze" -> snooze(app, intent)
                    "stop" -> stopRinger(app)
                    "reset" -> reset(app, intent)
                    "settings" -> settings(app, intent)
                    else -> Log.w(AlertIds.TAG, "DEBUG unknown command '$cmd'")
                }
            } catch (t: Throwable) {
                Log.e(AlertIds.TAG, "DEBUG $cmd failed", t)
            } finally {
                Log.i(AlertIds.TAG, "DEBUG $cmd done")
                pending.finish()
            }
        }
    }

    private suspend fun dump(context: Context, intent: Intent) {
        val days = intent.getIntExtra("days", 2)
        val text = AlertDiagnostics.dump(context, days)

        // Android's logger truncates a single entry at roughly 4 KB, and a dump
        // with a few days of doses in it goes past that - silently, losing the
        // tail, which is where the per-day tables are. So emit it in chunks
        // just under the limit, split on line boundaries. Still as few calls as
        // possible: logcat interleaves by entry, and a dump shredded one line
        // at a time gets threaded through the re-arm's own logging.
        var chunk = StringBuilder()
        fun flush() {
            if (chunk.isNotEmpty()) Log.i(AlertIds.TAG, chunk.toString())
            chunk = StringBuilder()
        }
        text.lineSequence().forEach { line ->
            if (chunk.length + line.length + 1 > MAX_LOG_CHUNK) flush()
            chunk.append(line).append('\n')
        }
        flush()
    }

    /**
     * The end-to-end path: seed a real dose, let the scheduler find it, and let
     * AlarmManager deliver it. Everything else here is a shortcut past
     * something; this one is not.
     */
    private suspend fun arm(context: Context, intent: Intent) {
        val armed = AlertDebugFixtures.armRealDose(
            context = context,
            minSeconds = intent.getLongExtra("seconds", 30L),
            tier = tierOf(intent),
            critical = intent.getBooleanExtra("critical", false),
        )
        Log.i(
            AlertIds.TAG,
            "DEBUG armed dose=${armed.doseId} fires at ${armed.fireAt} " +
                "(rounded up to the minute - scheduled doses have no seconds); " +
                "planner picked ${armed.planned?.doseIds} at ${armed.planned?.fireAt}",
        )
        if (armed.planned?.doseIds?.contains(armed.doseId) != true) {
            // Worth shouting about: the dose exists and is due, but the planner
            // chose something else. Either something sooner is pending, or the
            // candidate filter dropped it.
            Log.w(
                AlertIds.TAG,
                "DEBUG the seeded dose is NOT what the planner armed - run `dump` to see why",
            )
        }
    }

    /**
     * Straight to the dispatcher: the notification, the heads-up, the
     * full-screen takeover and the ringer, with no scheduler in the way.
     *
     * Seeds its own group by default rather than grabbing whatever real dose is
     * next, because the notification's shape comes from the group - one dose
     * says "Taken" and several say "Take all", critical loses the Skip action -
     * and "whatever was next" cannot reach most of those. Pass `--el doseId` to
     * aim it at a specific real dose instead.
     */
    private suspend fun fire(context: Context, intent: Intent) {
        val explicit = intent.getLongExtra("doseId", -1L)
        val tier = tierOf(intent)
        val critical = intent.getBooleanExtra("critical", false)

        val (doseIds, forDate) = if (explicit > 0) {
            listOf(explicit) to intent.getLongExtra("forDate", LocalDate.now().toEpochDay())
                .let(LocalDate::ofEpochDay)
        } else {
            AlertDebugFixtures.seedGroup(
                context = context,
                count = intent.getIntExtra("count", 1),
                tier = tier,
                critical = critical,
                mixed = intent.getBooleanExtra("mixed", false),
            )
        }
        if (doseIds.isEmpty()) {
            Log.w(AlertIds.TAG, "DEBUG fire: nothing to show")
            return
        }

        // Spending the snooze cap up front is the only way to see the
        // no-Snooze-action notification, which is otherwise reachable only
        // after sitting through three real snooze cycles.
        if (intent.getBooleanExtra("noSnooze", false)) {
            val dao = ThymeDatabase.get(context).doseDao()
            val settings = SettingsRepository.get(context).currentAlertSettings()
            val now = java.time.Instant.now()
            doseIds.forEach { id ->
                dao.upsertAlert(
                    dev.whayn.thyme.data.DoseAlert(
                        scheduledDoseId = id,
                        forDate = forDate,
                        state = dev.whayn.thyme.data.AlertState.FIRED,
                        nextFireAt = now,
                        dueAt = now,
                        snoozeCount = settings.maxAutoRepeats,
                        firstFiredAt = now,
                    )
                )
            }
        }

        Log.i(
            AlertIds.TAG,
            "DEBUG fire tier=$tier critical=$critical doses=$doseIds on $forDate",
        )
        AlertDispatcher.deliver(
            context = context,
            groupKey = System.currentTimeMillis() / 60_000,
            doseIds = doseIds,
            forDate = forDate,
            tier = tier,
            critical = critical,
        )
    }


    private suspend fun rearm(context: Context, intent: Intent) {
        val next = AlarmScheduler.get(context).rearm(intent.getStringExtra("reason") ?: "debug")
        Log.i(AlertIds.TAG, "DEBUG rearm -> ${next?.doseIds} at ${next?.fireAt}")
    }

    /**
     * Answers whatever is ringing, or an explicit dose.
     *
     * Defaulting to the ringing alert is the whole point: it is exactly what a
     * finger would do, and it removes the "look up the id first" step that made
     * testing the snooze cap tedious enough to skip.
     */
    private suspend fun answer(context: Context, intent: Intent) {
        val target = targetOf(context, intent) ?: return
        val skipped = intent.getBooleanExtra("skip", false)
        if (skipped) {
            AlertResponder.skipped(
                context, target.doseIds, target.forDate,
                intent.getStringExtra("reason") ?: "debug",
            )
        } else {
            AlertResponder.taken(context, target.doseIds, target.forDate)
        }
        AlertRingerService.stop(context)
        NotificationManagerCompat.from(context).cancelAll()
    }

    private suspend fun snooze(context: Context, intent: Intent) {
        val target = targetOf(context, intent) ?: return
        val willReturn = AlertResponder.snooze(
            context = context,
            doseIds = target.doseIds,
            forDate = target.forDate,
            tier = target.tier,
            automatic = intent.getBooleanExtra("automatic", false),
        )
        Log.i(AlertIds.TAG, "DEBUG snooze ${target.doseIds} -> willReturn=$willReturn")
        AlertRingerService.stop(context)
    }

    private fun stopRinger(context: Context) {
        AlertRingerService.stop(context)
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * Back to a clean slate without losing the fixture data.
     *
     * `Clear all data` in Settings is too blunt for this: it takes the 45 days
     * of seeded history with it, and re-seeding between every alarm test is how
     * a feedback loop stops being one.
     */
    private suspend fun reset(context: Context, intent: Intent) {
        val dao = ThymeDatabase.get(context).doseDao()
        AlertRingerService.stop(context)
        NotificationManagerCompat.from(context).cancelAll()

        // pruneAlerts is "delete everything before this date"; a date far
        // enough ahead means all of them. The DAO deliberately has no
        // delete-all, because nothing in the app should ever want one.
        dao.pruneAlerts(LocalDate.now().plusYears(100))
        val retired = AlertDebugFixtures.retire(dao)

        if (intent.getBooleanExtra("logs", false)) {
            val today = LocalDate.now()
            dao.dosesFor(today).forEach { dao.deleteLog(it.scheduled.id, today) }
            Log.i(AlertIds.TAG, "DEBUG reset: today's dose_logs cleared too")
        }
        Log.i(AlertIds.TAG, "DEBUG reset: alerts cleared, $retired debug fixtures retired")
        AlarmScheduler.get(context).rearm("debug-reset")
    }

    /**
     * Rewrites the alert settings from the shell.
     *
     * The snooze cap chain is the slowest thing in this system to observe: at
     * the shipped defaults, watching three auto-repeats expire takes half an
     * hour. At `snooze 1, ring 10, repeats 2` it takes about three minutes,
     * which is the difference between a test that gets run and one that does not.
     */
    private suspend fun settings(context: Context, intent: Intent) {
        val repo = SettingsRepository.get(context)
        intent.ifInt("snooze") { repo.setSnoozeMinutes(it) }
        intent.ifInt("ring") { repo.setRingSeconds(it) }
        intent.ifInt("repeats") { repo.setMaxAutoRepeats(it) }
        intent.ifBool("escalation") { repo.setEscalationEnabled(it) }
        intent.ifBool("repeatUntilAnswered") { repo.setRepeatUntilAnswered(it) }
        intent.ifBool("alarmClock") { repo.setUseAlarmClock(it) }
        intent.getStringExtra("defaultTier")?.let { name ->
            runCatching { AlertTier.valueOf(name.uppercase()) }
                .onSuccess { repo.setDefaultAlertTier(it) }
        }
        Log.i(AlertIds.TAG, "DEBUG settings now ${repo.currentAlertSettings()}")
        // useAlarmClock changes which AlarmManager API the pending alarm uses,
        // and the pending alarm was armed with the old one.
        AlarmScheduler.get(context).rearm("debug-settings")
    }

    private data class Target(
        val doseIds: List<Long>,
        val forDate: LocalDate,
        val tier: AlertTier,
    )

    private suspend fun targetOf(context: Context, intent: Intent): Target? {
        val explicit = intent.getLongExtra("doseId", -1L)
        if (explicit > 0) {
            val forDate = intent.getLongExtra("forDate", LocalDate.now().toEpochDay())
                .let(LocalDate::ofEpochDay)
            return Target(listOf(explicit), forDate, tierOf(intent))
        }
        AlertRingerService.ringing.value?.let {
            return Target(it.doseIds, it.forDate, it.tier)
        }
        // Nothing ringing and no id given: fall back to whatever has an alert
        // row waiting, which is what a Light notification sitting in the tray
        // looks like from here.
        val dao = ThymeDatabase.get(context).doseDao()
        val alert = dao.alertsFrom(LocalDate.now().minusDays(1)).minByOrNull { it.nextFireAt }
        if (alert == null) {
            Log.w(AlertIds.TAG, "DEBUG nothing ringing and no alert rows - pass --el doseId")
            return null
        }
        val tier = dao.alertSettingsFor(listOf(alert.scheduledDoseId))
            .firstOrNull()?.alertTier ?: AlertTier.LIGHT
        return Target(listOf(alert.scheduledDoseId), alert.forDate, tier)
    }

    private fun tierOf(intent: Intent): AlertTier = runCatching {
        AlertTier.valueOf((intent.getStringExtra("tier") ?: "STRONG").uppercase())
    }.getOrDefault(AlertTier.STRONG)

    private inline fun Intent.ifInt(key: String, block: (Int) -> Unit) {
        if (hasExtra(key)) block(getIntExtra(key, 0))
    }

    private inline fun Intent.ifBool(key: String, block: (Boolean) -> Unit) {
        if (hasExtra(key)) block(getBooleanExtra(key, false))
    }

    private companion object {
        const val LEGACY_FIRE = "dev.whayn.thyme.DEBUG_FIRE"

        /** Comfortably under the logger's ~4 KB per-entry truncation point. */
        const val MAX_LOG_CHUNK = 3_000
    }
}
