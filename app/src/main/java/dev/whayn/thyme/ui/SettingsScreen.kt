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
import dev.whayn.thyme.data.ThymeSettings
import dev.whayn.thyme.data.ThymeThemeMode

@Composable
fun SettingsScreen(
    settings: ThymeSettings,
    onThemeModeChange: (ThymeThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
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
            // Room for the pinned version line below.
            .padding(bottom = 48.dp),
    ) {
        PageHeader(title = "Settings")
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

    }
        Text(
            "Thyme ${appVersion(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "1.0"
