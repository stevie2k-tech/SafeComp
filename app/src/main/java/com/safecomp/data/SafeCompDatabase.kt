package com.safecomp.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GeofenceConfig::class, VaultFile::class, EventLogEntry::class, WorkApp::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class SafeCompDatabase : RoomDatabase() {
    abstract fun geofenceConfigDao(): GeofenceConfigDao
    abstract fun vaultFileDao(): VaultFileDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun workAppDao(): WorkAppDao

    companion object {
        fun build(context: Context): SafeCompDatabase =
            Room.databaseBuilder(context, SafeCompDatabase::class.java, "safecomp.db").build()
    }
}
