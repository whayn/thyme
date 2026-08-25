package dev.whayn.thyme.alert

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.whayn.thyme.MainActivity
import dev.whayn.thyme.R
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.ui.alert.AlertActivity
import java.time.LocalDate

/**
 * Builds the alert notification.
 *
 * Every action lives here rather than only on the alert screen, because Medium
 * and Strong land as an ordinary heads-up notification on any device where
 * USE_FULL_SCREEN_INTENT is denied - which, from API 34, is the default for
 * anything the system does not classify as a calling or alarm app. The
 * notification has to be a complete way to answer, not a shell.
 */
object AlertNotifications {

    fun post(
        context: Context,
        groupKey: Long,
        doseIds: List<Long>,
        forDate: LocalDate,
        tier: AlertTier,
        critical: Boolean,
        names: List<String>,
        timeLabel: String,
        snoozesLeft: Int,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val title = when {
            names.size == 1 -> names.first()
            else -> "${names.size} medications due"
        }
        val body = if (names.size == 1) timeLabel else "$timeLabel · ${names.joinToString(", ")}"

        val builder = NotificationCompat.Builder(context, AlertChannels.channelFor(tier.name))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Medium and Strong stay put until answered - they have a service
            // behind them and are meant to be insistent. Light is dismissable:
            // an unswipeable notification about vitamin D is exactly what gets
            // an app's notifications switched off wholesale.
            .setOngoing(tier != AlertTier.LIGHT)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            // A lock screen announcing "Sertraline 50mg" to whoever glances at
            // the table is a real privacy failure, so the public face is generic.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, AlertChannels.channelFor(tier.name))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Medication due")
                    .setContentText(timeLabel)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .build()
            )
            // Tapping the body should land on whatever is actually being asked.
            // For Medium and Strong that is the alert screen - which matters
            // most when the full-screen intent was downgraded to a heads-up
            // because the phone was in use, since then this is the only way in.
            .setContentIntent(
                if (tier == AlertTier.LIGHT) {
                    openAppIntent(context, groupKey)
                } else {
                    PendingIntent.getActivity(
                        context,
                        requestCode(groupKey, SLOT_OPEN),
                        AlertActivity.intent(context, groupKey, doseIds, forDate, tier, critical),
                        AlertIds.FLAGS,
                    )
                }
            )
            .addAction(
                0,
                if (doseIds.size > 1) "Take all" else "Taken",
                actionIntent(context, AlertActions.TAKEN, groupKey, doseIds, forDate),
            )

        // Medium and Strong ask to take over the screen. From API 34 this is an
        // app-op that is revoked by default for anything the system does not
        // consider a calling or alarm app, so it has to be checked rather than
        // assumed - when it is denied the notification silently becomes an
        // ordinary heads-up, which is exactly why every action lives on it.
        if (tier != AlertTier.LIGHT && canUseFullScreen(context)) {
            builder.setFullScreenIntent(
                PendingIntent.getActivity(
                    context,
                    requestCode(groupKey, SLOT_FULLSCREEN),
                    AlertActivity.intent(context, groupKey, doseIds, forDate, tier, critical),
                    AlertIds.FLAGS,
                ),
                true,
            )
        }

        if (snoozesLeft > 0) {
            builder.addAction(
                0,
                "Snooze",
                actionIntent(context, AlertActions.SNOOZE, groupKey, doseIds, forDate),
            )
        }

        // Critical doses get no one-tap skip. The reason flow lives in the app,
        // and opening it must be a getActivity PendingIntent: from Android 12 a
        // broadcast receiver may not start an activity, and a trampoline like
        // that is silently dropped rather than failing loudly.
        if (!critical) {
            builder.addAction(
                0,
                "Skip",
                actionIntent(context, AlertActions.SKIP, groupKey, doseIds, forDate),
            )
        }

        manager.notify(groupKey.toInt(), builder.build())
    }

    /** Declaring USE_FULL_SCREEN_INTENT is not the same as having it, from API 34. */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun cancel(context: Context, groupKey: Long) {
        NotificationManagerCompat.from(context).cancel(groupKey.toInt())
    }

    private fun openAppIntent(context: Context, groupKey: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(groupKey, SLOT_OPEN),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            AlertIds.FLAGS,
        )

    private fun actionIntent(
        context: Context,
        action: String,
        groupKey: Long,
        doseIds: List<Long>,
        forDate: LocalDate,
    ): PendingIntent {
        val intent = Intent(context, AlertActionReceiver::class.java)
            .setAction(action)
            .putExtra(AlertIds.EXTRA_GROUP_KEY, groupKey)
            .putExtra(AlertIds.EXTRA_DOSE_IDS, doseIds.toLongArray())
            .putExtra(AlertIds.EXTRA_FOR_DATE, forDate.toEpochDay())
        return PendingIntent.getBroadcast(
            context,
            requestCode(groupKey, slotOf(action)),
            intent,
            AlertIds.FLAGS,
        )
    }

    /**
     * Distinct request codes per group and action, or the actions would share a
     * PendingIntent and the last one built would win for all of them.
     */
    private fun requestCode(groupKey: Long, slot: Int): Int =
        (groupKey.toInt() shl 3) or slot

    private fun slotOf(action: String) = when (action) {
        AlertActions.TAKEN -> SLOT_TAKEN
        AlertActions.SNOOZE -> SLOT_SNOOZE
        else -> SLOT_SKIP
    }

    private const val SLOT_OPEN = 0
    private const val SLOT_TAKEN = 1
    private const val SLOT_SNOOZE = 2
    private const val SLOT_SKIP = 3
    private const val SLOT_FULLSCREEN = 4
}

object AlertActions {
    const val TAKEN = "dev.whayn.thyme.alert.TAKEN"
    const val SNOOZE = "dev.whayn.thyme.alert.SNOOZE"
    const val SKIP = "dev.whayn.thyme.alert.SKIP"
}
