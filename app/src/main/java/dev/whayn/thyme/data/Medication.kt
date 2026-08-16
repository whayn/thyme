package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val strength: String? = null, // Strength is the strength of one pill, ie 1000mg of paracetamol
    val active: Boolean = true,
)