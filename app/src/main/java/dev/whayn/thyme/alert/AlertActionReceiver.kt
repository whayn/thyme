package dev.whayn.thyme.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.whayn.thyme.data.AlertTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Handles the notification's own buttons.
 *
 * Only Taken / Snooze / Skip live here, and none of them start an activity:
 * from Android 12 a broadcast receiver may not launch one, and such a
 * trampoline is dropped silently rather than failing loudly. Anything that
 * needs UI - the skip-reason flow - is a getActivity PendingIntent instead.
 */
class AlertActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action ?: return
        val doseIds = intent.getLongArrayExtra(AlertIds.EXTRA_DOSE_IDS)?.toList().orEmpty()
        val forDate = intent.getLongExtra(AlertIds.EXTRA_FOR_DATE, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        val groupKey = intent.getLongExtra(AlertIds.EXTRA_GROUP_KEY, 0L)
        if (doseIds.isEmpty() || forDate == null) return

        val pending = goAsync()
        scope.launch {
            try {
                // Whatever the answer, stop making noise first.
                AlertRingerService.stop(appContext)
                when (action) {
                    AlertActions.TAKEN -> {
                        AlertResponder.taken(appContext, doseIds, forDate)
                        AlertNotifications.cancel(appContext, groupKey)
                    }

                    AlertActions.SKIP -> {
                        AlertResponder.skipped(appContext, doseIds, forDate, reason = null)
                        AlertNotifications.cancel(appContext, groupKey)
                    }

                    AlertActions.SNOOZE -> {
                        AlertResponder.snooze(
                            appContext, doseIds, forDate, AlertTier.LIGHT, automatic = false,
                        )
                        AlertNotifications.cancel(appContext, groupKey)
                    }

                    else -> Log.w(AlertIds.TAG, "unknown action $action")
                }
            } catch (t: Throwable) {
                Log.e(AlertIds.TAG, "action $action failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
