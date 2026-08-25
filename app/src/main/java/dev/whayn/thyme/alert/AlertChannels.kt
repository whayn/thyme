package dev.whayn.thyme.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings

/**
 * The notification channels the alert system posts on.
 *
 * **Ids are versioned, and that is not decoration.** A channel's sound,
 * importance and vibration are immutable once created: the only way to change
 * them later is a new id plus deleting the old one. Without the suffix, the
 * first release's settings would be permanent on every device that ever
 * installed it.
 */
object AlertChannels {

    const val LIGHT = "thyme_alert_light_v1"
    const val MEDIUM = "thyme_alert_medium_v1"
    const val STRONG = "thyme_alert_strong_v1"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // USAGE_ALARM routes to the alarm stream, which plays through
        // ringer-silent and is exempt from Do Not Disturb by default. That is
        // also why setBypassDnd is not used anywhere here: it silently no-ops
        // without notification-policy access, which needs a special-access
        // screen we would rather not send anyone to.
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        manager.createNotificationChannel(
            NotificationChannel(LIGHT, "Gentle reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "A notification and a short sound."
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, alarmAudio)
                    enableVibration(true)
                }
        )

        manager.createNotificationChannel(
            NotificationChannel(MEDIUM, "Full-screen reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Takes over the screen with a short sound."
                    // Medium keeps its sound on the channel: the system then
                    // guarantees it plays, even if this process is killed a
                    // millisecond after posting.
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, alarmAudio)
                    enableVibration(true)
                }
        )

        manager.createNotificationChannel(
            NotificationChannel(STRONG, "Alarms", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "A real alarm: full screen, and it keeps ringing."
                    // Deliberately silent. Strong's audio belongs to the ringer
                    // service so it can loop and outlive the activity; leaving
                    // the channel sound on would play two alarm tones at once.
                    setSound(null, null)
                    enableVibration(false)
                }
        )
    }

    fun channelFor(tier: String?): String = when (tier) {
        "STRONG" -> STRONG
        "MEDIUM" -> MEDIUM
        else -> LIGHT
    }
}
