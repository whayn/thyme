package dev.whayn.thyme.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromLocalTime(time: LocalTime): Int = time.toSecondOfDay() / 60

    @TypeConverter
    fun toLocalTime(minutesOfDay: Int): LocalTime =
        LocalTime.ofSecondOfDay(minutesOfDay * 60L)

    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun fromInstant(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)
}