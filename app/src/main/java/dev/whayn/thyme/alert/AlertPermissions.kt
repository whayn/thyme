package dev.whayn.thyme.alert

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * What the alert system needs from the platform, and how to go and get it.
 *
 * Everything here is checked at runtime rather than assumed from the manifest.
 * Declaring a permission and holding it stopped being the same thing several
 * API levels ago, and each of these fails in its own quiet way: alerts that
 * never appear, alerts that arrive fifteen minutes late, alerts that were meant
 * to take over the screen and turn into a banner instead.
 */
object AlertPermissions {

    enum class Requirement(val title: String, val consequence: String) {
        Notifications(
            "Notifications",
            "Without this, no reminder can appear at all.",
        ),
        ExactAlarms(
            "Exact alarms",
            "Without this, reminders can arrive up to 15 minutes late.",
        ),
        FullScreen(
            "Full-screen alerts",
            "Without this, alarms show as a banner instead of taking over the screen.",
        ),
        BatteryUnrestricted(
            "Unrestricted battery",
            "Without this, the system may delay or drop reminders to save power.",
        ),
    }

    data class Status(val requirement: Requirement, val granted: Boolean)

    fun statuses(context: Context): List<Status> = Requirement.entries.map {
        Status(it, isGranted(context, it))
    }

    fun isGranted(context: Context, requirement: Requirement): Boolean = when (requirement) {
        Requirement.Notifications ->
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        Requirement.ExactAlarms ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        Requirement.FullScreen ->
            Build.VERSION.SDK_INT < 34 ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        Requirement.BatteryUnrestricted ->
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Where to send someone to fix it.
     *
     * Battery optimisation deliberately opens the *list* rather than asking
     * directly: the direct prompt needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
     * which is on Play's restricted list and carries a declaration. Two extra
     * taps once, ever, is a better trade than that.
     *
     * Every intent is offered with a fallback, because OEM builds do not all
     * resolve the specific settings screens.
     */
    fun intentsFor(context: Context, requirement: Requirement): List<Intent> {
        val appUri = Uri.fromParts("package", context.packageName, null)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)

        return when (requirement) {
            Requirement.Notifications -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                appDetails,
            )

            Requirement.ExactAlarms ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri), appDetails)
                } else {
                    listOf(appDetails)
                }

            Requirement.FullScreen ->
                if (Build.VERSION.SDK_INT >= 34) {
                    listOf(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, appUri),
                        appDetails,
                    )
                } else {
                    listOf(appDetails)
                }

            Requirement.BatteryUnrestricted -> listOf(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                appDetails,
            )
        }
    }

    /** Tries each candidate in turn; nothing here is allowed to crash the screen. */
    fun open(context: Context, requirement: Requirement): Boolean {
        intentsFor(context, requirement).forEach { intent ->
            val launched = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) return true
        }
        return false
    }

    /**
     * Vendor-specific guidance, shown only where it is actually needed.
     *
     * Deliberately copy rather than a deep link: the ComponentName recipes that
     * circulate for these screens throw on ROM versions that renamed or
     * unexported them, and a crash in the reliability screen is a special kind
     * of embarrassing. A Pixel user never sees this at all.
     */
    fun oemGuidance(manufacturer: String = Build.MANUFACTURER): String? =
        when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" ->
                "Security → Permissions → Autostart → enable Thyme. " +
                    "Then Battery saver → Thyme → No restrictions."

            "samsung" ->
                "Battery → Background usage limits → Never sleeping apps → add Thyme."

            "huawei", "honor" ->
                "Battery → App launch → Thyme → Manage manually → enable all three."

            "oppo", "realme", "oneplus", "vivo", "iqoo" ->
                "Battery → App battery usage → Thyme → " +
                    "allow background activity and auto launch."

            else -> null
        }
}
