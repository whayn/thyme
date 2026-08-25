package dev.whayn.thyme.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.alert.RingingAlert
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme

/**
 * The way out of a ringing alarm from inside the app.
 *
 * Pressing Home during an alert deliberately leaves it playing, and the
 * notification is meant to be the way back - but notifications can be limited,
 * a full-screen intent can be declined, and a phone making a noise the user
 * cannot find the source of is its own kind of failure. Opening the app has to
 * be enough on its own.
 *
 * Wears `due` like the alert itself, so the two read as the same event.
 */
@Composable
internal fun RingingBanner(
    alert: RingingAlert?,
    onOpen: () -> Unit,
    onSilence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = ThymeTheme.accents
    AnimatedVisibility(
        visible = alert != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ThymeDimens.PageGutter, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            color = accents.dueContainer,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    SectionEyebrow("Ringing now", color = accents.due)
                    Text(
                        text = alert?.doseIds?.size?.let { count ->
                            if (count == 1) "A dose is due" else "$count doses are due"
                        }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = accents.onDueContainer,
                    )
                }
                TextButton(
                    onClick = onSilence,
                    colors = ButtonDefaults.textButtonColors(contentColor = accents.due),
                ) { Text("Silence") }
                Button(onClick = onOpen) { Text("Open") }
            }
        }
    }
}
