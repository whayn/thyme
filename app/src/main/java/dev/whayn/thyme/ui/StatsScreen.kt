package dev.whayn.thyme.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.CalendarDay
import dev.whayn.thyme.MedicationAdherence
import dev.whayn.thyme.StatsSummary
import dev.whayn.thyme.StatsWindow
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val weekdayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
fun StatsScreen(
    summary: StatsSummary,
    window: StatsWindow,
    onSelectWindow: (StatsWindow) -> Unit,
    month: YearMonth,
    calendarDays: List<CalendarDay>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                Text(
                    "STATS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text("Adherence", style = MaterialTheme.typography.displaySmall)
            }
        }
        item {
            CalendarSection(
                month = month,
                days = calendarDays,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onDayClick = onDayClick,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StatsWindow.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = window == entry,
                            onClick = { onSelectWindow(entry) },
                            shape = SegmentedButtonDefaults.itemShape(index, StatsWindow.entries.size),
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        }
        item {
            OverviewCard(summary = summary, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (summary.expected == 0) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Nothing tracked yet", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tick a dose on Today to start building history.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(summary.perMedication, key = { it.medicationId }) { adherence ->
                MedicationAdherenceRow(
                    adherence = adherence,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CalendarSection(
    month: YearMonth,
    days: List<CalendarDay>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(month.format(monthFormatter), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            onClick = { onDayClick(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One ring per day rather than one dot per dose: a dot row grows with the dose
 * count and runs out of room in a 7-wide grid, but a ring just subdivides its
 * fixed circumference into more, thinner arcs — same size cell however many
 * medications that day has. Solid arc = taken, faded arc = missed, one arc per
 * scheduled dose, colored by that medication's own accent.
 */
@Composable
private fun DayCell(day: CalendarDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isToday = day.date == LocalDate.now()
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val doseArcs = day.doses.map { ThymeTheme.accents.medicationColor(it.colorIndex) to it.taken }

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .alpha(if (day.inMonth) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val strokeWidth = 3.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (doseArcs.isNotEmpty()) {
                val gapDegrees = 8f
                val sweep = 360f / doseArcs.size
                doseArcs.forEachIndexed { index, (accent, taken) ->
                    drawArc(
                        color = if (taken) accent else accent.copy(alpha = 0.3f),
                        startAngle = -90f + index * sweep + gapDegrees / 2f,
                        sweepAngle = sweep - gapDegrees,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OverviewCard(summary: StatsSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "${(summary.percent * 100).roundToInt()}%",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (summary.streakDays == 1) "1-day streak" else "${summary.streakDays}-day streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MedicationAdherenceRow(adherence: MedicationAdherence, modifier: Modifier = Modifier) {
    val accent = ThymeTheme.accents.medicationColor(adherence.colorIndex)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.size(12.dp))
                Text(
                    text = adherence.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(adherence.percent * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { adherence.percent },
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
