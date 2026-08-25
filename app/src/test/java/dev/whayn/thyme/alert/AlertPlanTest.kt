package dev.whayn.thyme.alert

import dev.whayn.thyme.data.AlertState
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseAlert
import dev.whayn.thyme.data.DoseOutcome
import dev.whayn.thyme.data.ScheduledDose
import dev.whayn.thyme.data.TodayDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AlertPlanTest {

    private val noneHandled = emptySet<Long>()
    private val paris = ZoneId.of("Europe/Paris")
    private val newYork = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 8, 21)

    private fun dose(
        id: Long,
        time: LocalTime,
        tier: AlertTier = AlertTier.LIGHT,
        critical: Boolean = false,
        outcome: DoseOutcome? = null,
    ) = TodayDose(
        scheduled = ScheduledDose(id = id, regimenId = 1, time = time),
        medicationId = id,
        medicationName = "Med$id",
        strength = null,
        colorIndex = 0,
        colorIndexRight = 0,
        form = 0,
        alertTier = tier,
        critical = critical,
        logId = outcome?.let { id },
        loggedAt = outcome?.let { Instant.EPOCH },
        outcome = outcome,
        skipReason = null,
    )

    private fun at(h: Int, m: Int = 0, date: LocalDate = day, zone: ZoneId = paris): Instant =
        date.atTime(h, m).atZone(zone).toInstant()

    // ── Calendar semantics ───────────────────────────────────────────────────

    @Test
    fun `a dose fires on its own calendar date`() {
        assertEquals(at(8), AlertPlan.instantOf(day, LocalTime.of(8, 0), paris))
    }

    @Test
    fun `a pre-dawn dose fires that same morning, not the next one`() {
        // The 05:00 day-start is a list-ordering rule and stops at the UI. If it
        // leaked into scheduling, a Mondays-only 02:00 regimen would ring on a
        // Tuesday while logging itself against Monday.
        assertEquals(at(2), AlertPlan.instantOf(day, LocalTime.of(2, 0), paris))
    }

    // ── Daylight saving ──────────────────────────────────────────────────────

    @Test
    fun `a dose inside the spring-forward gap still fires exactly once`() {
        // 2026-03-08, US eastern: 02:00 does not exist, clocks jump to 03:00.
        val gapDay = LocalDate.of(2026, 3, 8)
        val fired = AlertPlan.instantOf(gapDay, LocalTime.of(2, 30), newYork)
        assertEquals(gapDay.atTime(3, 30).atZone(newYork).toInstant(), fired)
    }

    @Test
    fun `a dose inside the fall-back overlap takes the earlier offset`() {
        // 2026-11-01, US eastern: 01:30 happens twice. Once is the right answer
        // for a medication; twice would be a double dose.
        val overlapDay = LocalDate.of(2026, 11, 1)
        val fired = AlertPlan.instantOf(overlapDay, LocalTime.of(1, 30), newYork)
        assertEquals(overlapDay.atTime(1, 30).atZone(newYork).withEarlierOffsetAtOverlap()
            .toInstant(), fired)
    }

    // ── What is eligible to ring ─────────────────────────────────────────────

    @Test
    fun `resolved doses never ring again`() {
        val doses = listOf(
            dose(1, LocalTime.of(8, 0), outcome = DoseOutcome.TAKEN),
            dose(2, LocalTime.of(9, 0), outcome = DoseOutcome.SKIPPED),
            dose(3, LocalTime.of(10, 0)),
        )
        val candidates = AlertPlan.candidatesOn(day, doses, paris, noneHandled)
        assertEquals(listOf(3L), candidates.map { it.second.scheduled.id })
    }

    @Test
    fun `medications set to Off never ring`() {
        val doses = listOf(
            dose(1, LocalTime.of(8, 0), tier = AlertTier.NONE),
            dose(2, LocalTime.of(9, 0), tier = AlertTier.LIGHT),
        )
        assertEquals(
            listOf(2L),
            AlertPlan.candidatesOn(day, doses, paris, noneHandled).map { it.second.scheduled.id },
        )
    }

    @Test
    fun `a dose that already rang is not offered again`() {
        // The re-fire loop this prevents: a dose rings, is still unresolved, is
        // still inside the catch-up window, and so stays the next candidate.
        // The alarm then re-arms into the past and fires immediately, forever.
        val doses = listOf(dose(1, LocalTime.of(8, 0)), dose(2, LocalTime.of(20, 0)))
        val candidates = AlertPlan.candidatesOn(day, doses, paris, alreadyHandled = setOf(1L))
        assertEquals(listOf(2L), candidates.map { it.second.scheduled.id })
    }

    @Test
    fun `catch-up fires a dose missed in the last hour but not an older one`() {
        val doses = listOf(dose(1, LocalTime.of(8, 0)))
        val within = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, doses, paris, noneHandled), day, at(8).minus(AlertPlan.CATCH_UP),
        )
        assertEquals("59 minutes late is still worth taking", at(8), within?.fireAt)

        val tooOld = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, doses, paris, noneHandled), day,
            at(8).minus(AlertPlan.CATCH_UP).plusSeconds(1),
        )
        assertEquals(at(8), tooOld?.fireAt)

        val wayPast = AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, noneHandled), day, at(14))
        assertNull("waking someone at 14:00 about an 08:00 dose helps nobody", wayPast)
    }

    @Test
    fun `a dose that rang and then expired at its cap does not ring again`() {
        // The ring-forever loop this exists to prevent, spelled out:
        //  08:00 Strong fires, rings 120s, snoozes three times, hits the cap.
        //  Its alert row goes EXPIRED and - correctly - no log row is written,
        //  because absence is what means missed.
        //  It is now ~32 minutes past 08:00, so the dose is still unresolved AND
        //  still inside the 60-minute catch-up window. If EXPIRED rows are not
        //  excluded it is a valid candidate that "fires immediately", forever.
        val doses = listOf(dose(1, LocalTime.of(8, 0), tier = AlertTier.STRONG))
        val thirtyTwoPast = at(8, 32)

        val withoutExclusion =
            AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, noneHandled), day,
                thirtyTwoPast.minus(AlertPlan.CATCH_UP))
        assertEquals("guard rail: it really would ring again", at(8), withoutExclusion?.fireAt)

        val expired = setOf(1L)
        val withExclusion =
            AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, expired), day,
                thirtyTwoPast.minus(AlertPlan.CATCH_UP))
        assertNull("an expired alert must not be resurrected by catch-up", withExclusion)
    }

    // ── Doses set to Off ─────────────────────────────────────────────────────

    @Test
    fun `an Off dose rides along with a group it did not trigger`() {
        // It never causes an alert. But once something else has already put a
        // screen in front of you at that instant, hiding it just means doing it
        // twice.
        val vitamin = dose(1, LocalTime.of(8, 0), tier = AlertTier.NONE)
        val med = dose(2, LocalTime.of(8, 0), tier = AlertTier.MEDIUM)
        val all = listOf(vitamin, med)

        val group = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, all, paris, noneHandled), day, at(7), allDue = all,
            zone = paris,
        )!!
        assertEquals(listOf(1L, 2L), group.doseIds.sorted())
        assertEquals("a passenger must not make the group louder", AlertTier.MEDIUM, group.tier)
    }

    @Test
    fun `an Off dose alone never forms a group`() {
        val only = listOf(dose(1, LocalTime.of(8, 0), tier = AlertTier.NONE))
        assertNull(
            AlertPlan.groupFrom(
                AlertPlan.candidatesOn(day, only, paris, noneHandled), day, at(7), allDue = only,
                zone = paris,
            )
        )
    }

    @Test
    fun `an already-resolved Off dose does not ride along`() {
        val vitamin = dose(1, LocalTime.of(8, 0), tier = AlertTier.NONE, outcome = DoseOutcome.TAKEN)
        val med = dose(2, LocalTime.of(8, 0), tier = AlertTier.MEDIUM)
        val all = listOf(vitamin, med)
        val group = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, all, paris, noneHandled), day, at(7), allDue = all,
            zone = paris,
        )!!
        assertEquals(listOf(2L), group.doseIds)
    }

    // ── Grouping ─────────────────────────────────────────────────────────────

    @Test
    fun `doses at the same instant become one alert`() {
        val doses = listOf(
            dose(1, LocalTime.of(8, 0)),
            dose(2, LocalTime.of(8, 0)),
            dose(3, LocalTime.of(20, 0)),
        )
        val group = AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, noneHandled), day, at(7))!!
        assertEquals(at(8), group.fireAt)
        assertEquals(listOf(1L, 2L), group.doseIds.sorted())
    }

    @Test
    fun `a group rings at its loudest member but keeps criticality separate`() {
        val doses = listOf(
            dose(1, LocalTime.of(8, 0), tier = AlertTier.LIGHT, critical = true),
            dose(2, LocalTime.of(8, 0), tier = AlertTier.STRONG, critical = false),
        )
        val group = AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, noneHandled), day, at(7))!!
        assertEquals(AlertTier.STRONG, group.tier)
        assertTrue("a critical member makes the group critical", group.anyCritical)
    }

    @Test
    fun `doses a minute apart stay two alerts`() {
        val doses = listOf(dose(1, LocalTime.of(8, 0)), dose(2, LocalTime.of(8, 1)))
        val group = AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, noneHandled), day, at(7))!!
        assertEquals(listOf(1L), group.doseIds)
    }

    @Test
    fun `nothing left today yields nothing`() {
        val doses = listOf(dose(1, LocalTime.of(8, 0)))
        assertNull(AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, noneHandled), day, at(9)))
    }

    @Test
    fun `an empty day yields nothing`() {
        assertNull(AlertPlan.groupFrom(emptyList(), day, at(9)))
    }

    @Test
    fun `a dose due exactly now still fires`() {
        val doses = listOf(dose(1, LocalTime.of(8, 0)))
        val group = AlertPlan.groupFrom(AlertPlan.candidatesOn(day, doses, paris, noneHandled), day, at(8))
        assertEquals(at(8), group?.fireAt)
    }

    // ── Snooze interleaving ──────────────────────────────────────────────────

    private fun snooze(id: Long, fireAt: Instant, forDate: LocalDate = day) = DoseAlert(
        id = id,
        scheduledDoseId = id,
        forDate = forDate,
        state = AlertState.SNOOZED,
        nextFireAt = fireAt,
        dueAt = fireAt,
    )

    @Test
    fun `a snooze that lands first wins the single alarm slot`() {
        val scheduled = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, listOf(dose(1, LocalTime.of(20, 0))), paris, noneHandled), day, at(7),
        )
        val snoozed = AlertPlan.snoozedGroup(listOf(snooze(9, at(8, 10)))) {
            AlertTier.STRONG to false
        }
        assertEquals(at(8, 10), AlertPlan.soonest(scheduled, snoozed)?.fireAt)
    }

    @Test
    fun `a snooze later than the next dose does not delay it`() {
        val scheduled = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, listOf(dose(1, LocalTime.of(9, 0))), paris, noneHandled), day, at(7),
        )
        val snoozed = AlertPlan.snoozedGroup(listOf(snooze(9, at(23, 0)))) {
            AlertTier.LIGHT to false
        }
        assertEquals(at(9), AlertPlan.soonest(scheduled, snoozed)?.fireAt)
    }

    @Test
    fun `a snooze across midnight keeps the day it belongs to`() {
        // 23:50 dose, snoozed ten minutes. It fires tomorrow and must still be
        // logged against today, or adherence quietly lands on the wrong date.
        val snoozed = AlertPlan.snoozedGroup(
            listOf(snooze(1, at(0, 0, day.plusDays(1)), forDate = day)),
        ) { AlertTier.STRONG to true }
        assertEquals(day, snoozed!!.forDate)
        assertEquals(at(0, 0, day.plusDays(1)), snoozed.fireAt)
    }

    @Test
    fun `a snoozed dose whose medication was switched Off drops out`() {
        assertNull(AlertPlan.snoozedGroup(listOf(snooze(1, at(8, 10)))) { AlertTier.NONE to false })
    }

    @Test
    fun `only snoozed rows are considered`() {
        val fired = snooze(1, at(8, 10)).copy(state = AlertState.FIRED)
        val expired = snooze(2, at(8, 20)).copy(state = AlertState.EXPIRED)
        assertNull(AlertPlan.snoozedGroup(listOf(fired, expired)) { AlertTier.STRONG to false })
    }

    @Test
    fun `soonest handles either side being absent`() {
        val only = AlertPlan.snoozedGroup(listOf(snooze(1, at(8, 10)))) { AlertTier.LIGHT to false }
        assertEquals(only, AlertPlan.soonest(null, only))
        assertEquals(only, AlertPlan.soonest(only, null))
        assertNull(AlertPlan.soonest(null, null))
    }

    // ── Group keys ───────────────────────────────────────────────────────────

    @Test
    fun `group keys are distinct per instant and fit in an Int`() {
        val a = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, listOf(dose(1, LocalTime.of(8, 0))), paris, noneHandled), day, at(7),
        )!!
        val b = AlertPlan.groupFrom(
            AlertPlan.candidatesOn(day, listOf(dose(2, LocalTime.of(8, 1))), paris, noneHandled), day, at(7),
        )!!
        assertTrue(a.groupKey != b.groupKey)
        assertTrue("must fit in Int for notification ids", a.groupKey < Int.MAX_VALUE.toLong())
    }
}
