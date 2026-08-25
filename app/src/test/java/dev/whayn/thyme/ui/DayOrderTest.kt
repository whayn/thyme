package dev.whayn.thyme.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Pins the split between [dayOrder] and [isOverdue].
 *
 * [dayOrder] rotates the clock so 05:00 is zero. That is a valid sort key and
 * the list still uses it, but it is *not* a valid "is this before that" test:
 * the rotation breaks the ordering for anything spanning it. Overdue detection
 * used to go through it anyway, which marked every dose overdue between 00:00
 * and 05:00 - the 08:00 one included, five hours early.
 */
class DayOrderTest {

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    // ── The bug this exists to prevent ───────────────────────────────────────

    @Test
    fun `morning dose is not overdue in the small hours`() {
        // 03:00, looking at today. The 08:00 dose is five hours away.
        assertFalse(isOverdue(t(8), t(3), DayPosition.Today, resolved = false))
        assertFalse(isOverdue(t(13), t(3), DayPosition.Today, resolved = false))
        assertFalse(isOverdue(t(20), t(3), DayPosition.Today, resolved = false))
    }

    @Test
    fun `pre-dawn dose is overdue once it has passed`() {
        // The 02:00 dose sorts to the bottom of the list but really did happen
        // an hour ago, so at 03:00 it is genuinely overdue.
        assertTrue(isOverdue(t(2), t(3), DayPosition.Today, resolved = false))
    }

    @Test
    fun `the whole day is not overdue at one minute past midnight`() {
        listOf(t(8), t(13), t(20), t(22)).forEach { due ->
            assertFalse("$due should not be overdue at 00:01",
                isOverdue(due, t(0, 1), DayPosition.Today, resolved = false))
        }
    }

    // ── Ordinary daytime behaviour ───────────────────────────────────────────

    @Test
    fun `overdue tracks the raw clock during the day`() {
        assertTrue(isOverdue(t(8), t(14), DayPosition.Today, resolved = false))
        assertTrue(isOverdue(t(13), t(14), DayPosition.Today, resolved = false))
        assertFalse(isOverdue(t(20), t(14), DayPosition.Today, resolved = false))
    }

    @Test
    fun `a dose due exactly now is not yet overdue`() {
        assertFalse(isOverdue(t(8), t(8), DayPosition.Today, resolved = false))
    }

    // ── Other days have no present moment ────────────────────────────────────

    @Test
    fun `past days are wholly overdue whatever the clock says`() {
        // 08:00 is "after" 03:00 on the clock, but the day is gone.
        assertTrue(isOverdue(t(8), t(3), DayPosition.Past, resolved = false))
        assertTrue(isOverdue(t(23), t(3), DayPosition.Past, resolved = false))
    }

    @Test
    fun `future days are never overdue whatever the clock says`() {
        assertFalse(isOverdue(t(2), t(23), DayPosition.Future, resolved = false))
        assertFalse(isOverdue(t(8), t(23), DayPosition.Future, resolved = false))
    }

    // ── Resolution always wins ───────────────────────────────────────────────

    @Test
    fun `a resolved dose is never overdue`() {
        DayPosition.entries.forEach { position ->
            assertFalse("$position", isOverdue(t(8), t(20), position, resolved = true))
        }
    }

    // ── dayOrder still does its own job ──────────────────────────────────────

    @Test
    fun `dayOrder keeps a pre-dawn dose at the end of the day`() {
        val times = listOf(t(2), t(8), t(13), t(20), t(22))
        assertEquals(
            listOf(t(8), t(13), t(20), t(22), t(2)),
            times.sortedBy(::dayOrder),
        )
    }

    @Test
    fun `dayOrder is zero at the day start and maximal just before it`() {
        assertEquals(0, dayOrder(t(5)))
        assertEquals(24 * 60 - 1, dayOrder(t(4, 59)))
        assertEquals(DAY_START_MINUTE, dayOrder(t(10)))
    }
}
