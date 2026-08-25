package dev.whayn.thyme.ui.alert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whayn.thyme.AlertViewModel
import dev.whayn.thyme.alert.AlertIds
import dev.whayn.thyme.alert.AlertNotifications
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.SettingsRepository
import dev.whayn.thyme.data.ThymeThemeMode
import dev.whayn.thyme.ui.theme.ThymeTheme
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The alert, over the lock screen.
 *
 * Runs in its own task (`singleInstance` + empty `taskAffinity`) and stays out
 * of Recents, so pressing Home never leaves a ghost of the main app behind it
 * and swiping Recents never resurrects a stale alarm screen.
 *
 * Note what this does *not* try to do: re-launch itself when the user leaves.
 * Background activity starts are blocked from Android 10, and fighting that
 * produces something that works on one device and not the next. The sanctioned
 * way back is the ongoing notification plus the next snooze posting a fresh
 * full-screen intent - which is allowed, and is what "unskippable" actually
 * means on this platform.
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        val doseIds = intent.getLongArrayExtra(AlertIds.EXTRA_DOSE_IDS)?.toList().orEmpty()
        val forDate = intent.getLongExtra(AlertIds.EXTRA_FOR_DATE, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        val tier = runCatching {
            AlertTier.valueOf(intent.getStringExtra(AlertIds.EXTRA_TIER).orEmpty())
        }.getOrDefault(AlertTier.MEDIUM)
        val critical = intent.getBooleanExtra(AlertIds.EXTRA_CRITICAL, false)
        val groupKey = intent.getLongExtra(AlertIds.EXTRA_GROUP_KEY, 0L)

        if (doseIds.isEmpty() || forDate == null) {
            finish()
            return
        }

        setContent {
            val settings = SettingsRepository.get(this)
                .settings
                .map { it.themeMode to it.dynamicColor }
                .collectAsStateWithLifecycle(ThymeThemeMode.System to false)
                .value

            ThymeTheme(mode = settings.first, dynamicColor = settings.second) {
                val viewModel: AlertViewModel = viewModel(
                    factory = AlertViewModel.factory(this, doseIds, forDate, tier, critical),
                )
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val haptics = LocalHapticFeedback.current

                // Back defers, it does not dismiss. A snooze is written, counted
                // against the cap and comes back, so nothing is lost - but the
                // most reflexive gesture on the phone gets a sane meaning
                // instead of being a no-op that reads as a frozen screen.
                //
                // Once the snoozes are used up it goes back to doing nothing,
                // with a haptic and a line saying why.
                BackHandler(enabled = true) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!viewModel.snoozeFromBack()) {
                        Toast.makeText(
                            this@AlertActivity,
                            "No snoozes left - take it or say why not",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                LaunchedEffect(state.finished) {
                    if (state.finished) {
                        AlertNotifications.cancel(this@AlertActivity, groupKey)
                        finishAndRemoveTask()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AlertScreen(
                        state = state,
                        onToggleDose = viewModel::toggleDose,
                        onTake = viewModel::takeSelected,
                        onSnooze = viewModel::snooze,
                        onSkip = viewModel::requestSkip,
                        onSilence = viewModel::silence,
                        onChooseReason = viewModel::chooseReason,
                        onReasonNoteChange = viewModel::updateReasonNote,
                        onConfirmReason = viewModel::confirmChosenReason,
                        onCancelReason = viewModel::cancelReason,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    )
                }
            }
        }
    }

    /**
     * A second group firing while one is on screen arrives here rather than
     * stacking a new activity, because of `singleInstance`. Restarting with the
     * new payload merges them into one screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // On a secure keyguard this prompts for the PIN rather than
            // dismissing it, which is correct for health data: the alert shows
            // on top, and the actions work once the person has authenticated.
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun intent(
            context: Context,
            groupKey: Long,
            doseIds: List<Long>,
            forDate: LocalDate,
            tier: AlertTier,
            critical: Boolean,
        ): Intent = Intent(context, AlertActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(AlertIds.EXTRA_GROUP_KEY, groupKey)
            .putExtra(AlertIds.EXTRA_DOSE_IDS, doseIds.toLongArray())
            .putExtra(AlertIds.EXTRA_FOR_DATE, forDate.toEpochDay())
            .putExtra(AlertIds.EXTRA_TIER, tier.name)
            .putExtra(AlertIds.EXTRA_CRITICAL, critical)
    }
}
