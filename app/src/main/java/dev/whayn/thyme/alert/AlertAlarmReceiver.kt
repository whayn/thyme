package dev.whayn.thyme.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import dev.whayn.thyme.data.AlertTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * Where the single scheduled alarm lands, and where the heartbeat lands.
 *
 * Re-arming happens here, *before* anything is dispatched. If the ringer then
 * crashes, or nobody touches the phone for a day, the next dose's alarm is
 * already set - the chain never depends on the user answering this one.
 */
class AlertAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action

        // Always with a timeout. A leaked partial wake lock on a medication app
        // earns battery complaints that are invisible in testing.
        val wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "thyme:alarm")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        val pending = goAsync()

        scope.launch {
            try {
                when (action) {
                    AlertIds.ACTION_HEARTBEAT -> {
                        val alive = AlarmScheduler.get(appContext).mainAlarmExists()
                        Log.i(AlertIds.TAG, "heartbeat: mainAlarmExists=$alive")
                        AlarmScheduler.get(appContext).rearm("heartbeat")
                    }

                    AlertIds.ACTION_FIRE -> {
                        val doseIds = intent.getLongArrayExtra(AlertIds.EXTRA_DOSE_IDS)?.toList()
                            .orEmpty()
                        val forDate = intent.getLongExtra(AlertIds.EXTRA_FOR_DATE, Long.MIN_VALUE)
                            .takeIf { it != Long.MIN_VALUE }
                            ?.let(LocalDate::ofEpochDay)
                        val tier = intent.getStringExtra(AlertIds.EXTRA_TIER)
                        val critical = intent.getBooleanExtra(AlertIds.EXTRA_CRITICAL, false)

                        Log.i(
                            AlertIds.TAG,
                            "FIRE at ${Instant.now()} doses=$doseIds forDate=$forDate " +
                                "tier=$tier critical=$critical",
                        )

                        // Delivery goes first, and deliberately so. The firing
                        // alarm drops this app onto a temporary power allowlist
                        // for about ten seconds, and that window is the only
                        // reason a background foreground-service start is legal.
                        // Spending it on a database scan and an AlarmManager
                        // call first risks ForegroundServiceStartNotAllowed -
                        // which fails silently, with the next alarm correctly
                        // armed, so nothing looks wrong.
                        //
                        // Wrapped so a delivery failure can never swallow the
                        // re-arm below: losing one alert is recoverable, losing
                        // the chain is not.
                        if (forDate != null && doseIds.isNotEmpty()) {
                            runCatching {
                                AlertDispatcher.deliver(
                                    context = appContext,
                                    groupKey = intent.getLongExtra(AlertIds.EXTRA_GROUP_KEY, 0L),
                                    doseIds = doseIds,
                                    forDate = forDate,
                                    tier = runCatching { AlertTier.valueOf(tier.orEmpty()) }
                                        .getOrDefault(AlertTier.LIGHT),
                                    critical = critical,
                                )
                            }.onFailure { Log.e(AlertIds.TAG, "delivery failed", it) }
                        }

                        val scheduler = AlarmScheduler.get(appContext)
                        // Record the turn before re-arming. The gap between
                        // ringing and recording is exactly where a re-fire loop
                        // lives: the dose is still unresolved and still recent,
                        // so it stays the next candidate forever.
                        if (forDate != null && doseIds.isNotEmpty()) {
                            scheduler.markFired(doseIds, forDate, Instant.now())
                        }
                        scheduler.rearm("after-fire")
                    }

                    else -> Log.w(AlertIds.TAG, "unexpected action: $action")
                }
            } catch (t: Throwable) {
                Log.e(AlertIds.TAG, "alarm handling failed", t)
            } finally {
                pending.finish()
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private companion object {
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
