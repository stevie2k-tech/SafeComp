package com.safecomp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GeofenceConfig::class], version = 1, exportSchema = true)
abstract class SafeCompDatabase : RoomDatabase() {
    abstract fun geofenceConfigDao(): GeofenceConfigDao

    companion object {
        fun build(context: Context): SafeCompDatabase =
            Room.databaseBuilder(context, SafeCompDatabase::class.java, "safecomp.db").build()
    }
}
