package com.safecomp.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GeofenceConfig::class, VaultFile::class, EventLogEntry::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class SafeCompDatabase : RoomDatabase() {
    abstract fun geofenceConfigDao(): GeofenceConfigDao
    abstract fun vaultFileDao(): VaultFileDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        fun build(context: Context): SafeCompDatabase =
            Room.databaseBuilder(context, SafeCompDatabase::class.java, "safecomp.db").build()
    }
}
