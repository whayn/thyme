package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(tableName = "doses")
data class Dose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medication: String,
    val time: LocalTime,
    val taken: Boolean = false,
)