package dev.whayn.thyme.data

import androidx.room.TypeConverter
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromLocalTime(time: LocalTime): Int = time.toSecondOfDay() / 60

    @TypeConverter
    fun toLocalTime(minutesOfDay: Int): LocalTime =
        LocalTime.ofSecondOfDay(minutesOfDay * 60L)
}