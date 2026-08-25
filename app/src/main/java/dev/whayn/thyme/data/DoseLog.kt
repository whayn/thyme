package dev.whayn.thyme.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledDose::class,
            parentColumns = ["id"],
            childColumns = ["scheduledDoseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scheduledDoseId", "forDate"], unique = true)
    ]
)
/**
 * One resolved dose. The row's *existence* means resolved; [outcome] says how.
 *
 * This amends, rather than breaks, the event-sourced rule: there is still no
 * `taken` boolean, and `TodayDose.taken` stays a derived getter. The unique index
 * on (scheduledDoseId, forDate) is what makes it work - one row per dose per day,
 * so taken and skipped are mutually exclusive by construction.
 *
 * A dose with no row here is still simply *missed*. That is why there is no
 * MISSED outcome and no nightly job to write one.
 */
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledDoseId: Long,
    val forDate: LocalDate,
    /** When the outcome was recorded. Was `takenAt`, before a row could mean "skipped". */
    val loggedAt: Instant,
    @ColumnInfo(defaultValue = "TAKEN")
    val outcome: DoseOutcome = DoseOutcome.TAKEN,
    /** Only ever set on SKIPPED. */
    val skipReason: String? = null,
)