package dev.whayn.thyme.alert

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.whayn.thyme.R
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.SettingsRepository
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Makes the noise for Medium and Strong.
 *
 * Audio lives in a service rather than the alert Activity so it survives the
 * Activity being destroyed - the user pressing Home must not silence an alarm.
 *
 * The foreground type is `shortService`, which needs no Play Console
 * declaration (unlike `specialUse`) and is not a lie about what this does
 * (unlike `mediaPlayback`). Its ~3 minute ceiling is not a limitation being
 * worked around: an alarm that rings past three minutes and then auto-snoozes
 * is what every alarm clock does, and ringing forever in a pocket only flattens
 * the battery.
 */
class AlertRingerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopRunnable: Runnable? = null

    private var doseIds: List<Long> = emptyList()
    private var forDate: LocalDate? = null
    private var tier: AlertTier = AlertTier.MEDIUM
    private var groupKey: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        doseIds = intent.getLongArrayExtra(AlertIds.EXTRA_DOSE_IDS)?.toList().orEmpty()
        forDate = intent.getLongExtra(AlertIds.EXTRA_FOR_DATE, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        tier = runCatching { AlertTier.valueOf(intent.getStringExtra(AlertIds.EXTRA_TIER).orEmpty()) }
            .getOrDefault(AlertTier.MEDIUM)
        groupKey = intent.getLongExtra(AlertIds.EXTRA_GROUP_KEY, 0L)
        val critical = intent.getBooleanExtra(AlertIds.EXTRA_CRITICAL, false)

        // Must reach startForeground within 5 seconds of startForegroundService
        // or the system throws, so this goes up synchronously with a provisional
        // notification and is enriched afterwards.
        startForeground(provisionalNotification())

        scope.launch {
            enrichNotification(critical)
        }

        ringing.value = RingingAlert(groupKey, doseIds, forDate ?: LocalDate.now(), tier, critical)
        startRinging()
        // Belt and braces for the receiver's re-arm: idempotent, and it means a
        // ringing alarm always implies the next one is scheduled, whatever
        // happened upstream.
        AlarmScheduler.get(this).requestRearm("ringer-started")
        return START_NOT_STICKY
    }

    private fun startForeground(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, groupKey.toInt(), notification, type)
    }

    private fun provisionalNotification(): android.app.Notification =
        NotificationCompat.Builder(this, AlertChannels.channelFor(tier.name))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medication due")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            // Without this the foreground notification can be withheld for up to
            // ten seconds, which for an alarm is indistinguishable from broken.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private suspend fun enrichNotification(critical: Boolean) {
        val date = forDate ?: return
        val dao = ThymeDatabase.get(this).doseDao()
        val doses = dao.dosesFor(date).filter { it.scheduled.id in doseIds }
        if (doses.isEmpty()) return
        AlertNotifications.post(
            context = this,
            groupKey = groupKey,
            doseIds = doseIds,
            forDate = date,
            tier = tier,
            critical = critical,
            names = doses.map { it.medicationName },
            timeLabel = doses.first().scheduled.time
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            snoozesLeft = AlertResponder.snoozesLeft(this, doseIds, date, tier),
        )
    }

    private fun startRinging() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "thyme:ringer").apply {
            acquire(MAX_RING_MS + WAKE_LOCK_SLACK_MS)
        }

        // USAGE_ALARM routes to the alarm stream: audible through ringer-silent
        // and exempt from Do Not Disturb by default. This is the whole reason
        // audio belongs here rather than on the notification channel for Strong.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val uri = alarmUri()
        if (uri != null) {
            player = MediaPlayer().apply {
                runCatching {
                    setAudioAttributes(attributes)
                    setDataSource(this@AlertRingerService, uri)
                    isLooping = tier == AlertTier.STRONG
                    prepare()
                    start()
                }.onFailure { Log.e(AlertIds.TAG, "ringer audio failed", it) }
            }
        }

        vibrator = vibratorOrNull()
        val pattern = longArrayOf(0, 400, 300, 400, 1200)
        if (tier == AlertTier.STRONG) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }

        scope.launch {
            val seconds = SettingsRepository.get(this@AlertRingerService)
                .currentAlertSettings().ringSeconds
            val capped = (seconds * 1000L).coerceAtMost(MAX_RING_MS)
            handler.post {
                stopRunnable = Runnable { onRingFinished() }.also {
                    handler.postDelayed(it, capped)
                }
            }
        }
    }

    /**
     * A device with no alarm sound configured is common on emulators and not
     * unheard of on real hardware, and a silent alarm is the worst possible
     * failure here - so fall back rather than trust the first lookup.
     */
    private fun alarmUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)

    private fun vibratorOrNull(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    /** Ring time is up: put it off rather than keep going. */
    private fun onRingFinished() {
        val date = forDate
        if (date == null || doseIds.isEmpty()) {
            stopSelf()
            return
        }
        scope.launch {
            val willReturn = AlertResponder.snooze(this@AlertRingerService, doseIds, date, tier, automatic = true)
            if (!willReturn) AlertNotifications.cancel(this@AlertRingerService, groupKey)
            stopSelf()
        }
    }

    /**
     * The platform's shortService deadline, from API 34. Not implementing this
     * gets the process killed with a crash-shaped log rather than a clean stop.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.i(AlertIds.TAG, "shortService timeout reached, auto-snoozing")
        onRingFinished()
    }

    override fun onDestroy() {
        ringing.value = null
        stopRunnable?.let(handler::removeCallbacks)
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /**
         * What is ringing right now, or null.
         *
         * Exists so the app itself can offer a way out. Pressing Home leaves the
         * alarm playing by design, and if notifications are limited or the
         * full-screen intent was declined there may be nothing on screen to tap
         * - opening the app has to be enough.
         */
        val ringing = MutableStateFlow<RingingAlert?>(null)

        /** Below the platform's shortService ceiling, with room to clean up. */
        private const val MAX_RING_MS = 150_000L
        private const val WAKE_LOCK_SLACK_MS = 30_000L

        const val ACTION_STOP = "dev.whayn.thyme.alert.STOP_RINGING"

        fun start(
            context: Context,
            groupKey: Long,
            doseIds: List<Long>,
            forDate: LocalDate,
            tier: AlertTier,
            critical: Boolean,
        ) {
            val intent = Intent(context, AlertRingerService::class.java)
                .putExtra(AlertIds.EXTRA_GROUP_KEY, groupKey)
                .putExtra(AlertIds.EXTRA_DOSE_IDS, doseIds.toLongArray())
                .putExtra(AlertIds.EXTRA_FOR_DATE, forDate.toEpochDay())
                .putExtra(AlertIds.EXTRA_TIER, tier.name)
                .putExtra(AlertIds.EXTRA_CRITICAL, critical)
            // Legal from a background context here only because an alarm-clock
            // alarm just fired, which grants a temporary allowlist. This is why
            // setAlarmClock must stay the primary path.
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlertRingerService::class.java))
        }
    }
}
