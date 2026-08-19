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
    val logId: Long?,
    val takenAt: Instant?,
) {
    val taken: Boolean get() = takenAt != null
}
