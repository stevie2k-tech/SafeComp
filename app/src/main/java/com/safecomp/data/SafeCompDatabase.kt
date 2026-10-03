package com.safecomp.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GeofenceConfig::class, VaultFile::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SafeCompDatabase : RoomDatabase() {
    abstract fun geofenceConfigDao(): GeofenceConfigDao
    abstract fun vaultFileDao(): VaultFileDao

    companion object {
        fun build(context: Context): SafeCompDatabase =
            Room.databaseBuilder(context, SafeCompDatabase::class.java, "safecomp.db").build()
    }
}
