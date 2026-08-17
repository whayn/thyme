package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A course of a medication: when it applies and how often.
 *
 * A [Medication] is the drug; a Regimen is one course of it; its
 * [ScheduledDose]s are the times within that course. Splitting them this way
 * lets the same drug carry two concurrent courses — a taper, say, 20mg for a
 * week and then 10mg ongoing — and means changing a rule touches one row
 * rather than every time of day.
 *
 * The four recurrence fields compose with plain AND, so there is no
 * "recurrence type" to keep in sync: *every day* is simply the defaults.
 */
@Entity(
    tableName = "regimens",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("medicationId")],
)
data class Regimen(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,

    /** First day this course applies. Also the anchor for [intervalDays] and the cycle. */
    val startDate: LocalDate,
    /** Last day it applies; null means ongoing. Stopping a course sets this to today. */
    val endDate: LocalDate? = null,

    /** Bitmask of days, `1 shl (DayOfWeek.value - 1)`. 127 = every day. */
    val daysOfWeek: Int = Recurrence.EVERY_DAY,
    /** Every N days counting from [startDate]. 1 = daily. */
    val intervalDays: Int = 1,

    /** "Weeks off" cycles: [cycleOnDays] on, then [cycleOffDays] off, repeating. */
    val cycleOnDays: Int? = null,
    val cycleOffDays: Int? = null,

    val active: Boolean = true,
)
