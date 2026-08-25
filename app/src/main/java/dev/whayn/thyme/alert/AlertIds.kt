package dev.whayn.thyme.alert

import android.app.PendingIntent

/** One place for every id the alert system hands to the platform. */
object AlertIds {

    const val TAG = "ThymeAlert"

    /**
     * The single scheduled alarm. There is exactly one at a time, aimed at the
     * next unresolved dose, and re-armed after every fire, edit, snooze and boot.
     * Re-arming reuses this request code, so FLAG_UPDATE_CURRENT replaces both
     * the extras and the schedule atomically - no explicit cancel needed.
     */
    const val RC_MAIN_ALARM = 1001

    /** The watchdog heartbeat, which only ever re-arms the main alarm. */
    const val RC_HEARTBEAT = 1002

    /** Backs the alarm-clock chip in the status bar. */
    const val RC_SHOW_INTENT = 1003

    const val ACTION_FIRE = "dev.whayn.thyme.alert.FIRE"
    const val ACTION_HEARTBEAT = "dev.whayn.thyme.alert.HEARTBEAT"

    const val EXTRA_DOSE_IDS = "doseIds"
    const val EXTRA_FOR_DATE = "forDate"
    const val EXTRA_TIER = "tier"
    const val EXTRA_CRITICAL = "critical"
    const val EXTRA_GROUP_KEY = "groupKey"

    /**
     * FLAG_IMMUTABLE is mandatory from API 31 - omitting it throws rather than
     * warns - and every PendingIntent here is fully specified up front, so
     * there is nothing a recipient would need to fill in.
     */
    const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Read-only probe: does a PendingIntent already exist, without creating one. */
    const val FLAGS_PROBE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
}

/** The alert currently making a noise, published by the ringer for in-app recovery. */
data class RingingAlert(
    val groupKey: Long,
    val doseIds: List<Long>,
    val forDate: java.time.LocalDate,
    val tier: dev.whayn.thyme.data.AlertTier,
    val critical: Boolean,
)
