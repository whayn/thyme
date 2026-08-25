package dev.whayn.thyme.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.whayn.thyme.MainActivity
import dev.whayn.thyme.data.AlertState
import dev.whayn.thyme.data.DoseAlert
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.SettingsRepository
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps exactly one alarm pending at a time, aimed at the next dose that still
 * needs answering.
 *
 * The obvious design is one alarm per dose. Alarm apps do not do that: N alarms
 * means N PendingIntents to reconcile on every edit, and the OS caps how many
 * you get. One alarm, recomputed from scratch whenever anything changes, has a
 * single source of truth and no reconciliation at all.
 *
 * [rearm] is idempotent, so calling it too often is free and calling it too
 * rarely is a silent bug. Bias hard towards calling it.
 */
class AlarmScheduler private constructor(private val context: Context) {

    private val dao: DoseDao by lazy { ThymeDatabase.get(context).doseDao() }
    private val alarmManager by lazy { context.getSystemService(AlarmManager::class.java) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Boot, ON_RESUME and a dose write can land within milliseconds of each
     * other. Two concurrent recomputes racing on the same PendingIntent can
     * leave the app armed for the loser's instant.
     */
    private val mutex = Mutex()

    /** Fire-and-forget test alarm, for call sites with no coroutine of their own. */
    fun requestTestAlarm(seconds: Long = 10) {
        scope.launch { fireTestAlarm(seconds) }
    }

    /** Fire-and-forget re-arm, for call sites with no coroutine of their own. */
    fun requestRearm(reason: String) {
        scope.launch { rearm(reason) }
    }

    /**
     * What would ring next, worked out but not armed.
     *
     * Split out of [rearm] so the question can be asked without the side
     * effects that change its answer - arming the alarm, recording the next
     * fire time. [rearm] is the only caller that acts on the result; the debug
     * diagnostics call it to compare what the planner *believes* against what
     * the OS was actually told, which is where the interesting disagreements
     * are.
     *
     * Deliberately does not take [mutex]. It only reads, and making a dump wait
     * behind an in-flight re-arm would hide exactly the race worth seeing.
     */
    suspend fun plan(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PlannedAlert? {
        val today = LocalDate.now(zone)
        val handled = dao.alertsFrom(today)
            .groupBy({ it.forDate }, { it.scheduledDoseId })
            .mapValues { (_, ids) -> ids.toSet() }
        return AlertPlan.soonest(nextScheduled(today, zone, now, handled), nextSnoozed())
    }

    suspend fun rearm(reason: String): PlannedAlert? = mutex.withLock {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)

        dao.pruneAlerts(today.minusDays(ALERT_RETENTION_DAYS))

        val next = plan(now, zone)
        val useAlarmClock = SettingsRepository.get(context).currentAlertSettings().useAlarmClock
        if (next == null) {
            alarmManager.cancel(firePendingIntent(null))
            Log.i(AlertIds.TAG, "rearm($reason): nothing pending, alarm cancelled")
        } else {
            arm(next, useAlarmClock)
            Log.i(AlertIds.TAG, "rearm($reason): next ${next.doseIds} at ${next.fireAt} tier=${next.tier}")
        }
        armHeartbeat(now)
        SettingsRepository.get(context).recordRearm(
            nextFireAtMillis = next?.fireAt?.toEpochMilli() ?: 0L,
            atMillis = now.toEpochMilli(),
        )
        next
    }

    /**
     * Rings in [seconds], using whatever is genuinely due next.
     *
     * This is the only honest way to answer "will it actually wake me?" - the
     * four permission rows can all read green and the phone can still be
     * configured to swallow it. It ships in release for that reason.
     */
    suspend fun fireTestAlarm(seconds: Long = 10) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val sample = (0 until AlertPlan.LOOKAHEAD_DAYS).firstNotNullOfOrNull { offset ->
            val date = today.plusDays(offset.toLong())
            dao.dosesFor(date).firstOrNull { it.alertTier != AlertTier.NONE }?.let { date to it }
        } ?: return

        val (date, dose) = sample
        val intent = Intent(context, AlertAlarmReceiver::class.java)
            .setAction(AlertIds.ACTION_FIRE)
            .putExtra(AlertIds.EXTRA_DOSE_IDS, longArrayOf(dose.scheduled.id))
            .putExtra(AlertIds.EXTRA_FOR_DATE, date.toEpochDay())
            .putExtra(AlertIds.EXTRA_TIER, dose.alertTier.name)
            .putExtra(AlertIds.EXTRA_CRITICAL, dose.critical)
            .putExtra(AlertIds.EXTRA_GROUP_KEY, TEST_GROUP_KEY)
        val pi = PendingIntent.getBroadcast(context, RC_TEST, intent, AlertIds.FLAGS)
        val at = Instant.now().plusSeconds(seconds).toEpochMilli()
        runCatching { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            .onFailure { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        Log.i(AlertIds.TAG, "test alarm set for $seconds s using dose ${dose.scheduled.id}")
    }

    /**
     * Walks forward until a day yields something.
     *
     * Starting the window at now minus the catch-up allowance is what makes "the
     * phone was off over the 08:00 dose" ring on unlock rather than vanish.
     */
    private suspend fun nextScheduled(
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
        handled: Map<LocalDate, Set<Long>>,
    ): PlannedAlert? {
        val from = now.minus(AlertPlan.CATCH_UP)
        for (offset in 0 until AlertPlan.LOOKAHEAD_DAYS) {
            val date = today.plusDays(offset.toLong())
            val due = dao.dosesFor(date)
            val candidates = AlertPlan.candidatesOn(date, due, zone, handled[date].orEmpty())
            AlertPlan.groupFrom(candidates, date, from, allDue = due, zone = zone)
                ?.let { return it }
        }
        return null
    }

    /**
     * Records that a group has rung, so the scheduler moves past it.
     *
     * Written before the next re-arm, never after: the gap between the two is
     * exactly where a re-fire loop lives.
     */
    suspend fun markFired(doseIds: List<Long>, forDate: LocalDate, dueAt: Instant) {
        val now = Instant.now()
        doseIds.forEach { id ->
            val existing = dao.alertFor(id, forDate)
            dao.upsertAlert(
                DoseAlert(
                    id = existing?.id ?: 0,
                    scheduledDoseId = id,
                    forDate = forDate,
                    state = AlertState.FIRED,
                    nextFireAt = dueAt,
                    dueAt = existing?.dueAt ?: dueAt,
                    snoozeCount = existing?.snoozeCount ?: 0,
                    escalationStep = existing?.escalationStep ?: 0,
                    firstFiredAt = existing?.firstFiredAt ?: now,
                )
            )
        }
    }

    private suspend fun nextSnoozed(): PlannedAlert? {
        val rows = dao.snoozedAlerts()
        if (rows.isEmpty()) return null
        val settings = dao.alertSettingsFor(rows.map { it.scheduledDoseId })
            .associateBy { it.scheduledDoseId }
        return AlertPlan.snoozedGroup(rows) { alert ->
            settings[alert.scheduledDoseId]?.let { it.alertTier to it.critical }
        }
    }

    /**
     * Both paths fire in Doze and both grant a temporary power allowlist when
     * they do - `REASON_ALARM_MANAGER_ALARM_CLOCK` and
     * `REASON_ALARM_MANAGER_WHILE_IDLE` respectively - which is what lets the
     * receiver start the ringer service from the background.
     *
     * What differs: `setAlarmClock` has no Doze rate limit, but claims the
     * status-bar alarm icon and the system's "next alarm". Allow-while-idle
     * alarms are invisible but are held to roughly one per nine minutes per app
     * while the device is idle - which a 10-minute snooze clears and a
     * 5-minute one does not.
     */
    private fun arm(next: PlannedAlert, useAlarmClock: Boolean) {
        val operation = firePendingIntent(next)
        val at = next.fireAt.toEpochMilli()
        try {
            if (useAlarmClock) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(at, showPendingIntent()), operation,
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked while we were running. Degrade to
            // an inexact alarm rather than crash: a late reminder beats none.
            Log.w(AlertIds.TAG, "exact alarms denied, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /**
     * A coarse repeating check that the main alarm still exists.
     *
     * setAndAllowWhileIdle rather than setInexactRepeating, which Doze suspends
     * outright. Six hours is well clear of the roughly nine-minute floor that
     * throttles allow-while-idle alarms, so it is never deferred for quota.
     */
    private fun armHeartbeat(now: Instant) {
        val intent = Intent(context, AlertAlarmReceiver::class.java)
            .setAction(AlertIds.ACTION_HEARTBEAT)
        val pi = PendingIntent.getBroadcast(context, AlertIds.RC_HEARTBEAT, intent, AlertIds.FLAGS)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now.plus(HEARTBEAT_INTERVAL).toEpochMilli(),
            pi,
        )
    }

    /** True when the main alarm's PendingIntent is still registered with the OS. */
    fun mainAlarmExists(): Boolean {
        val intent = Intent(context, AlertAlarmReceiver::class.java).setAction(AlertIds.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, AlertIds.RC_MAIN_ALARM, intent, AlertIds.FLAGS_PROBE,
        ) != null
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun firePendingIntent(next: PlannedAlert?): PendingIntent {
        val intent = Intent(context, AlertAlarmReceiver::class.java)
            .setAction(AlertIds.ACTION_FIRE)
        if (next != null) {
            intent.putExtra(AlertIds.EXTRA_DOSE_IDS, next.doseIds.toLongArray())
            // The dose's own day travels with the alarm and is written back
            // verbatim. Recomputing it at answer time would file a 23:00 dose
            // answered at 00:30 under the wrong date.
            intent.putExtra(AlertIds.EXTRA_FOR_DATE, next.forDate.toEpochDay())
            intent.putExtra(AlertIds.EXTRA_TIER, next.tier.name)
            intent.putExtra(AlertIds.EXTRA_CRITICAL, next.anyCritical)
            intent.putExtra(AlertIds.EXTRA_GROUP_KEY, next.groupKey)
        }
        return PendingIntent.getBroadcast(
            context, AlertIds.RC_MAIN_ALARM, intent, AlertIds.FLAGS,
        )
    }

    /** Tapping the status-bar alarm chip should land on the dose list. */
    private fun showPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            AlertIds.RC_SHOW_INTENT,
            Intent(context, MainActivity::class.java),
            AlertIds.FLAGS,
        )

    companion object {
        private const val RC_TEST = 1004
        private const val TEST_GROUP_KEY = 999_999L
        private const val ALERT_RETENTION_DAYS = 7L
        private val HEARTBEAT_INTERVAL: Duration = Duration.ofHours(6)

        @Volatile
        private var instance: AlarmScheduler? = null

        fun get(context: Context): AlarmScheduler =
            instance ?: synchronized(this) {
                instance ?: AlarmScheduler(context.applicationContext).also { instance = it }
            }
    }
}
