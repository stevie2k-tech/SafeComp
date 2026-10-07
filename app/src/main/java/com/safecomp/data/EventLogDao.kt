package com.safecomp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<EventLogEntry>>

    @Insert
    suspend fun insert(entry: EventLogEntry)

    @Query("DELETE FROM event_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    /** Keeps only the newest [keep] rows. */
    @Query("DELETE FROM event_log WHERE id NOT IN (SELECT id FROM event_log ORDER BY id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM event_log")
    suspend fun clear()
}
