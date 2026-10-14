package com.hackqing.scale.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ScaleMeasurementEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HackqingDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        fun build(context: Context): HackqingDatabase =
            Room.databaseBuilder(context, HackqingDatabase::class.java, "hackqing.db").build()
    }
}
