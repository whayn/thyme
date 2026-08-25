package dev.whayn.thyme.alert

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.ThymeDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Turns a fired group into whatever that tier is supposed to look like.
 *
 * Light stops at a notification and never involves a foreground service:
 * starting one, calling startForeground and immediately stopping just to post a
 * notification is legal but wasteful, and it would put an ongoing service entry
 * in the shade for the tier whose whole point is being unobtrusive.
 */
object AlertDispatcher {

    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    suspend fun deliver(
        context: Context,
        groupKey: Long,
        doseIds: List<Long>,
        forDate: LocalDate,
        tier: AlertTier,
        critical: Boolean,
    ) {
        if (tier == AlertTier.NONE) return

        val dao = ThymeDatabase.get(context).doseDao()
        val doses = dao.dosesFor(forDate).filter { it.scheduled.id in doseIds }
        if (doses.isEmpty()) {
            Log.w(AlertIds.TAG, "nothing to show for $doseIds on $forDate")
            return
        }

        // Medium and Strong hand off to the ringer, which owns the audio and
        // the wake lock and posts its own (identical) notification. Light never
        // touches a foreground service: an ongoing service entry in the shade
        // would defeat the one tier whose point is being unobtrusive.
        // Without notification permission a foreground service still runs - its
        // notification is merely suppressed - so the audio would loop with no
        // full-screen intent to attach to, no actions, and no visible UI
        // anywhere. That is a phone playing an alarm the user cannot stop.
        // Refuse to start it, and let AlertSetup say why nothing happened.
        val canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (tier != AlertTier.LIGHT && !canNotify) {
            Log.w(
                AlertIds.TAG,
                "notifications denied - refusing to ring $tier for $doseIds, " +
                    "an alarm with no way to silence it is worse than a missed one",
            )
            return
        }

        if (tier != AlertTier.LIGHT) {
            AlertRingerService.start(context, groupKey, doseIds, forDate, tier, critical)
            Log.i(AlertIds.TAG, "handed $tier off to the ringer for $doseIds")
            return
        }

        val names = doses.map { it.medicationName }
        val timeLabel = doses.first().scheduled.time.format(timeFormatter)
        val snoozesLeft = AlertResponder.snoozesLeft(context, doseIds, forDate, tier)

        AlertNotifications.post(
            context = context,
            groupKey = groupKey,
            doseIds = doseIds,
            forDate = forDate,
            tier = tier,
            critical = critical,
            names = names,
            timeLabel = timeLabel,
            snoozesLeft = snoozesLeft,
        )
        Log.i(AlertIds.TAG, "delivered $tier notification for $names at $timeLabel")
    }
}
