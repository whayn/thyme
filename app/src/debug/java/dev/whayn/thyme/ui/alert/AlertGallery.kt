package dev.whayn.thyme.ui.alert

import dev.whayn.thyme.AlertUiState
import dev.whayn.thyme.SkipReason
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseOutcome
import dev.whayn.thyme.data.ScheduledDose
import dev.whayn.thyme.data.TodayDose
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Every state [AlertScreen] can be in, as data.
 *
 * The alert screen is the hardest surface in the app to look at: reaching it
 * for real needs an alarm to fire, which needs a dose to be due, and half these
 * states additionally need a particular snooze count or a half-finished skip.
 * Checking a padding change against eighteen of them that way is not something
 * anyone does twice.
 *
 * [AlertUiState] is a plain data class and [AlertScreen] is a pure function of
 * it, so none of that is actually necessary - the states can simply be written
 * down. No alarm, no foreground service, no database, no waiting.
 *
 * What this deliberately does **not** cover: the notification, the heads-up,
 * the full-screen takeover and the ringer are drawn by the OS from real
 * objects, and nothing rendered in-process can stand in for them. Those need
 * `tools/alert fire`, on a device.
 */
object AlertGallery {

    /** One entry in the gallery: a name to find it by and the state to draw. */
    data class Case(
        val name: String,
        /** What this case is for - shown in the list, so the point is not lost. */
        val note: String,
        val state: AlertUiState,
    )

    private val sertraline = dose(
        id = 1, name = "Sertraline", strength = "100mg", time = LocalTime.of(8, 0),
        colorIndex = 6, form = 1, tier = AlertTier.STRONG,
    )
    private val vitaminD = dose(
        id = 2, name = "Vitamin D3", strength = "1000IU", time = LocalTime.of(8, 0),
        colorIndex = 2, form = 8, tier = AlertTier.NONE,
    )
    private val amoxicillin = dose(
        id = 3, name = "Amoxicillin", strength = "500mg", time = LocalTime.of(8, 0),
        colorIndex = 0, colorIndexRight = 10, form = 0, tier = AlertTier.LIGHT,
    )
    private val criticalDose = sertraline.copy(critical = true)

    /**
     * Ordered so the everyday cases come first and the edge cases sit together
     * at the end. The index is stable enough to deep-link
     * (`tools/alert screen 7`) but is not an identifier - add new cases at the
     * end rather than in the middle.
     */
    val cases: List<Case> = listOf(
        Case(
            "Single dose",
            "The common case. One pill is the hero, not the clock.",
            base(listOf(sertraline)),
        ),
        Case(
            "Single, critical",
            "Tertiary action reads 'Can't take this now' rather than 'Skip'.",
            base(listOf(criticalDose)),
        ),
        Case(
            "Group of three",
            "All ticked. Primary reads 'Take all (3)'.",
            base(listOf(sertraline, vitaminD, amoxicillin)),
        ),
        Case(
            "Group, partly ticked",
            "Primary reads 'Take 2' - answering a group piecemeal.",
            base(listOf(sertraline, vitaminD, amoxicillin), selected = setOf(1L, 3L)),
        ),
        Case(
            "Group, nothing ticked",
            "Primary and Skip both disabled. Snooze stays live.",
            base(listOf(sertraline, vitaminD, amoxicillin), selected = emptySet()),
        ),
        Case(
            "Critical unticked in a mixed group",
            "The friction follows the selection: this must read plain 'Skip'.",
            base(listOf(criticalDose, vitaminD), selected = setOf(2L)),
        ),
        Case(
            "Critical ticked in a mixed group",
            "Same group, critical ticked - now 'Can't take this now'.",
            base(listOf(criticalDose, vitaminD), selected = setOf(1L, 2L)),
        ),
        Case(
            "No snoozes left",
            "The Snooze button is absent entirely, not disabled.",
            base(listOf(sertraline), snoozesLeft = 0),
        ),
        Case(
            "Unlimited snoozes",
            "repeatUntilAnswered: 'Snooze 10 min' with no count in brackets.",
            base(listOf(sertraline), snoozesLeft = Int.MAX_VALUE),
        ),
        Case(
            "Silenced",
            "After Silence: the button is gone, nothing has been decided.",
            base(listOf(sertraline)).copy(silenced = true),
        ),
        Case(
            "Snooze confirmation",
            "The ~1.4s state between snoozing and the screen closing.",
            base(listOf(sertraline)).copy(silenced = true, snoozedUntilLabel = "3:07 PM"),
        ),
        Case(
            "Reason: nothing chosen",
            "Step one of the critical skip. Confirm is blocked and says why.",
            base(listOf(criticalDose)).copy(askingReason = true),
        ),
        Case(
            "Reason: Other",
            "Free-text field appears; an empty note still blocks Confirm.",
            base(listOf(criticalDose))
                .copy(askingReason = true, chosenReason = SkipReason.Other),
        ),
        Case(
            "Reason: Other, filled",
            "Confirm now enabled, reading 'Skip this dose'.",
            base(listOf(criticalDose)).copy(
                askingReason = true,
                chosenReason = SkipReason.Other,
                reasonNote = "Waiting on a repeat prescription",
            ),
        ),
        Case(
            "Reason: Already took it",
            "Resolves as TAKEN, so the button flips to 'Mark as taken'.",
            base(listOf(criticalDose))
                .copy(askingReason = true, chosenReason = SkipReason.AlreadyTook),
        ),
        Case(
            "Long name, no strength",
            "Layout under a name that wraps and a missing detail line.",
            base(
                listOf(
                    dose(
                        id = 9,
                        name = "Hydrochlorothiazide / Lisinopril",
                        strength = null,
                        time = LocalTime.of(8, 0),
                        colorIndex = 4,
                        form = 3,
                        tier = AlertTier.STRONG,
                    )
                )
            ),
        ),
        Case(
            "Six in one group",
            "The list scrolls; the bottom bar must stay put.",
            base(
                listOf(
                    sertraline, vitaminD, amoxicillin,
                    dose(4, "Melatonin", "3mg", LocalTime.of(8, 0), 9, form = 1),
                    dose(5, "Salbutamol", "100mcg", LocalTime.of(8, 0), 8, form = 12),
                    dose(6, "Ibuprofen", "200mg", LocalTime.of(8, 0), 11, form = 3),
                )
            ),
        ),
        Case(
            "One already answered",
            "A partly-resolved group: the taken dose drops out of `unresolved`.",
            base(listOf(sertraline, vitaminD, amoxicillin), selected = setOf(1L, 2L))
                .let { s ->
                    s.copy(
                        doses = s.doses.map {
                            if (it.scheduled.id == 3L) {
                                it.copy(outcome = DoseOutcome.TAKEN, loggedAt = Instant.now())
                            } else {
                                it
                            }
                        }
                    )
                },
        ),
    )

    private fun base(
        doses: List<TodayDose>,
        selected: Set<Long> = doses.map { it.scheduled.id }.toSet(),
        snoozesLeft: Int = 3,
    ) = AlertUiState(
        loading = false,
        doses = doses,
        selected = selected,
        forDate = LocalDate.of(2026, 8, 22),
        tier = doses.maxOfOrNull { it.alertTier } ?: AlertTier.MEDIUM,
        critical = doses.any { it.critical },
        snoozesLeft = snoozesLeft,
        snoozeMinutes = 10,
    )

    private fun dose(
        id: Long,
        name: String,
        strength: String?,
        time: LocalTime,
        colorIndex: Int,
        colorIndexRight: Int = colorIndex,
        form: Int,
        tier: AlertTier = AlertTier.LIGHT,
        critical: Boolean = false,
    ) = TodayDose(
        scheduled = ScheduledDose(id = id, regimenId = id, time = time),
        medicationId = id,
        medicationName = name,
        strength = strength,
        colorIndex = colorIndex,
        colorIndexRight = colorIndexRight,
        form = form,
        alertTier = tier,
        critical = critical,
        logId = null,
        loggedAt = null,
        outcome = null,
        skipReason = null,
    )
}
