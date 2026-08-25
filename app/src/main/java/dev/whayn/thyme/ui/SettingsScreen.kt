package dev.whayn.thyme.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.BuildConfig
import androidx.compose.material3.OutlinedButton
import dev.whayn.thyme.alert.AlertPermissions
import dev.whayn.thyme.data.AlertSettings
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.ThymeSettings
import dev.whayn.thyme.ui.theme.ThymeTheme
import dev.whayn.thyme.data.ThymeThemeMode

@Composable
fun SettingsScreen(
    settings: ThymeSettings,
    alertSettings: AlertSettings,
    onThemeModeChange: (ThymeThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    onRingSecondsChange: (Int) -> Unit,
    onRepeatUntilAnsweredChange: (Boolean) -> Unit,
    onUseAlarmClockChange: (Boolean) -> Unit,
    onDefaultTierChange: (AlertTier) -> Unit,
    onOpenAlertSetup: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onSeedFakeData: () -> Unit = {},
    onClearAllData: () -> Unit = {},
) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        PageHeader(title = "Settings")
        Spacer(Modifier.height(12.dp))

        // Reminders sits above Appearance: whether an alarm goes off outranks
        // what colour the app is.
        SectionCard("Reminders") {
            val outstanding = AlertPermissions.statuses(LocalContext.current).count { !it.granted }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Alarm reliability", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when (outstanding) {
                            0 -> "All set"
                            1 -> "1 thing needs attention"
                            else -> "$outstanding things need attention"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (outstanding == 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else ThymeTheme.accents.due,
                    )
                }
                OutlinedButton(onClick = onOpenAlertSetup) { Text("Review") }
            }

            Spacer(Modifier.height(20.dp))
            Text("Default alert level", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Used for medications you add from now on",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            // Four items fit a segmented row at this width without wrapping,
            // matching the theme picker directly below.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AlertTier.entries.forEachIndexed { index, tier ->
                    SegmentedButton(
                        selected = alertSettings.defaultAlertTier == tier,
                        onClick = { onDefaultTierChange(tier) },
                        shape = SegmentedButtonDefaults.itemShape(index, AlertTier.entries.size),
                        label = { Text(tier.label) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Snooze for", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            val snoozeOptions = listOf(5, 10, 15, 30)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                snoozeOptions.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = alertSettings.snoozeMinutes == minutes,
                        onClick = { onSnoozeMinutesChange(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index, snoozeOptions.size),
                        label = { Text("${minutes}m") },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Ring for", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            val ringOptions = listOf(30, 60, 120, 180)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ringOptions.forEachIndexed { index, seconds ->
                    SegmentedButton(
                        selected = alertSettings.ringSeconds == seconds,
                        onClick = { onRingSecondsChange(seconds) },
                        shape = SegmentedButtonDefaults.itemShape(index, ringOptions.size),
                        label = { Text(if (seconds < 60) "${seconds}s" else "${seconds / 60}m") },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep alerting until answered", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Strong alarms only. Otherwise they stop after a few tries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = alertSettings.repeatUntilAnswered,
                    onCheckedChange = onRepeatUntilAnsweredChange,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show in status bar", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (alertSettings.useAlarmClock) {
                            "Most reliable. Shows an alarm icon, and your phone " +
                                "will report the next dose as your next alarm."
                        } else {
                            "Stays out of the status bar. While the phone is idle, " +
                                "reminders can be held to one every 9 minutes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = alertSettings.useAlarmClock,
                    onCheckedChange = onUseAlarmClockChange,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Grouped in cards like every other tab. These controls used to sit
        // directly on the page background, which made Settings the one screen
        // with no surfaces on it at all.
        SectionCard("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            val modes = listOf(
                ThymeThemeMode.System to "System",
                ThymeThemeMode.Light to "Light",
                ThymeThemeMode.Dark to "Dark",
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dynamic colour", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Match the colours in your wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColorChange)
            }
        }

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(12.dp))
            SectionCard("Developer") {
                Text(
                    "Replaces everything in the database with 45 days of fixture history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onSeedFakeData) { Text("Seed fake data") }
                    TextButton(
                        onClick = onClearAllData,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Clear all data") }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Thyme ${appVersion(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
    }
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "1.0"
