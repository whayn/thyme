package dev.whayn.thyme.data

import androidx.room.Embedded
import java.time.Instant

data class TodayDose(
    @Embedded val scheduled: ScheduledDose,
    val medicationId: Long,
    val medicationName: String,
    val strength: String?,
    val colorIndex: Int,
    val colorIndexRight: Int = colorIndex,
    val form: Int,
    val alertTier: AlertTier,
    val critical: Boolean,
    val logId: Long?,
    val loggedAt: Instant?,
    val outcome: DoseOutcome?,
    val skipReason: String?,
) {
    /** Successfully taken. Every adherence figure in the app means this one. */
    val taken: Boolean get() = outcome == DoseOutcome.TAKEN

    /** Deliberately not taken, with a reason recorded. Counts against adherence. */
    val skipped: Boolean get() = outcome == DoseOutcome.SKIPPED

    /**
     * Something has been decided about this dose. Not the same as [taken]:
     * anything asking "is this still outstanding?" - overdue styling, the next
     * dose due, whether the alarm should still fire - wants this, not [taken].
     */
    val resolved: Boolean get() = outcome != null
}
