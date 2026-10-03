package com.safecomp.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceConfigDao {
    @Query("SELECT * FROM geofence_config WHERE id = 0")
    fun observe(): Flow<GeofenceConfig?>

    @Query("SELECT * FROM geofence_config WHERE id = 0")
    suspend fun get(): GeofenceConfig?

    @Upsert
    suspend fun upsert(config: GeofenceConfig)

    @Query("DELETE FROM geofence_config")
    suspend fun clear()
}
