package dev.whayn.thyme.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-arms after the events that silently throw alarms away.
 *
 * `MY_PACKAGE_REPLACED` is the one that catches people out: **installing an app
 * update cancels every alarm it had scheduled**. Without this, each
 * `gradlew installDebug` kills the alarm during development - which looks
 * exactly like a scheduling bug - and every Play update does it to users.
 *
 * `ACTION_LOCKED_BOOT_COMPLETED` is deliberately absent: the database lives in
 * credential-protected storage and cannot be read before first unlock, so there
 * would be nothing to schedule from.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        Log.i(AlertIds.TAG, "system event: $action")
        // Cheap enough to do inline via the scheduler's own scope: this is one
        // indexed read and one AlarmManager call.
        AlarmScheduler.get(context.applicationContext).requestRearm(action)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
