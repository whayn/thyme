package dev.whayn.thyme.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import dev.whayn.thyme.alert.AlertPermissions
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Everything that decides whether an alarm actually goes off.
 *
 * Split out of Settings because four permissions plus vendor guidance plus
 * diagnostics is far too much for one card, and because this doubles as the
 * onboarding flow - shown once, after the first medication that wants a real
 * alert, never at first launch when there is nothing to be reminded about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSetupScreen(
    nextFireAtMillis: Long,
    lastRearmAtMillis: Long,
    oemDismissed: Boolean,
    onDismissOem: () -> Unit,
    onTestAlarm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var statuses by remember { mutableStateOf(AlertPermissions.statuses(context)) }

    // Every one of these is granted in a *different* app, so the only moment we
    // find out the answer is when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        statuses = AlertPermissions.statuses(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { statuses = AlertPermissions.statuses(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            PageHeader(
                title = "Will it wake you?",
                eyebrow = "Reminders",
            )
            Column(Modifier.padding(horizontal = ThymeDimens.PageGutter)) {
                Text(
                    "Android can quietly stop an app from reminding you. " +
                        "These are the four settings that decide whether it does.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            statuses.forEach { status ->
                RequirementCard(
                    status = status,
                    onFix = {
                        val needsRuntimeAsk =
                            status.requirement == AlertPermissions.Requirement.Notifications &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        if (needsRuntimeAsk) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            AlertPermissions.open(context, status.requirement)
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            val guidance = AlertPermissions.oemGuidance()
            if (guidance != null && !oemDismissed) {
                SectionCard("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} settings") {
                    Text(
                        "This make of phone stops background apps aggressively, " +
                            "and Android's own settings cannot override it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(guidance, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            AlertPermissions.open(
                                context,
                                AlertPermissions.Requirement.BatteryUnrestricted,
                            )
                        }) { Text("Open settings") }
                        TextButton(onClick = onDismissOem) { Text("Done") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionCard("Diagnostics") {
                Text(
                    nextFireAtMillis.takeIf { it > 0 }?.let { "Next alarm: ${formatMillis(it)}" }
                        ?: "No alarm scheduled",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (lastRearmAtMillis > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Checked ${formatMillis(lastRearmAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "The surest way to know is to hear it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onTestAlarm) { Text("Test alarm in 10 seconds") }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RequirementCard(
    status: AlertPermissions.Status,
    onFix: () -> Unit,
) {
    SectionCard(status.requirement.title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (status.granted) "All set" else "Needs attention",
                    style = MaterialTheme.typography.bodyLarge,
                    // `due` for attention, never `error`: nothing here is broken
                    // or dangerous, it just is not switched on yet.
                    color = if (status.granted) MaterialTheme.colorScheme.primary
                    else ThymeTheme.accents.due,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    status.requirement.consequence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!status.granted) {
                OutlinedButton(onClick = onFix) { Text("Fix") }
            }
        }
    }
}

private val diagnosticsFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

private fun formatMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(diagnosticsFormatter)
