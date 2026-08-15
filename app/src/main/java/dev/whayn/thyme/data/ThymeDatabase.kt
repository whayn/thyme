package dev.whayn.thyme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Dose::class], version = 1)
@TypeConverters(Converters::class)
abstract class ThymeDatabase : RoomDatabase() {

    abstract fun doseDao(): DoseDao

    companion object {
        @Volatile
        private var instance: ThymeDatabase? = null

        fun get(context: Context): ThymeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThymeDatabase::class.java,
                    "thyme.db"
                ).build().also { instance = it }
            }
    }
}