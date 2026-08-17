package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime

/**
 * One time of day within a [Regimen]. Whether it applies on a given date is
 * decided entirely by its regimen's window and recurrence.
 */
@Entity(
    tableName = "scheduled_doses",
    foreignKeys = [
        ForeignKey(
            entity = Regimen::class,
            parentColumns = ["id"],
            childColumns = ["regimenId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("regimenId")]
)
data class ScheduledDose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val regimenId: Long,
    val time: LocalTime,
    val quantity: Double = 1.0, // Quantity is the amount of pills taken in one dose
    // Soft delete: dose_logs cascade off this row, so removing a time from a
    // regimen deactivates it rather than deleting the history attached to it.
    val active: Boolean = true,
)
