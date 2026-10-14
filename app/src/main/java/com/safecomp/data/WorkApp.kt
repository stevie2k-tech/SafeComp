package com.safecomp.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One app the employee chose as a "work app" for Focus Mode. Only the package name is stored:
 * no usage, no timestamps of when it was opened. It never leaves the phone.
 */
@Entity(tableName = "work_apps")
data class WorkApp(
    @PrimaryKey val packageName: String,
)

@Dao
interface WorkAppDao {
    @Query("SELECT packageName FROM work_apps ORDER BY packageName")
    fun observe(): Flow<List<String>>

    @Query("SELECT packageName FROM work_apps ORDER BY packageName")
    suspend fun all(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: WorkApp)

    @Query("DELETE FROM work_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM work_apps")
    suspend fun clear()
}
