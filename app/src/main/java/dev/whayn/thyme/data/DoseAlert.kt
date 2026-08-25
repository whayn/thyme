package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Transient scheduling state for one dose on one day: has it fired, has it been
 * snoozed, when should it make noise next.
 *
 * This is deliberately *not* in `dose_logs`. A log row is history - what the
 * person did. This is the alarm clock's own bookkeeping, and it gets pruned.
 *
 * **Rows are created lazily, never eagerly.** A dose with no alert row and no
 * log row is by definition "pending, fires at its scheduled instant", which the
 * existing per-day query already tells us for free. A row appears only when
 * something non-default happens: it fired, or it was snoozed. Materialising rows
 * ahead of time would reintroduce exactly the nightly job that the event-sourced
 * design was built to avoid.
 *
 * [forDate] is the dose's own day and is carried, never recomputed: a 23:00
 * alarm answered at 00:30 still belongs to the previous day, and a 23:50 dose
 * snoozed past midnight keeps its original date while only [nextFireAt] moves.
 */
@Entity(
    tableName = "dose_alerts",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledDose::class,
            parentColumns = ["id"],
            childColumns = ["scheduledDoseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scheduledDoseId", "forDate"], unique = true),
        Index(value = ["nextFireAt"]),
    ]
)
data class DoseAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledDoseId: Long,
    val forDate: LocalDate,
    val state: AlertState,
    /** When this alert should next make noise. */
    val nextFireAt: Instant,
    /** The dose's own scheduled instant, kept so escalation can measure elapsed time. */
    val dueAt: Instant,
    /** Auto-snoozes and manual snoozes counted together, so snoozing cannot outlive the cap. */
    val snoozeCount: Int = 0,
    val escalationStep: Int = 0,
    val firstFiredAt: Instant? = null,
)
