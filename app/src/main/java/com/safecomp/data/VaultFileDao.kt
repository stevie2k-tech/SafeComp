package com.safecomp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<VaultFile>>

    @Query("SELECT * FROM vault_files WHERE id = :id")
    suspend fun get(id: String): VaultFile?

    @Query("SELECT id FROM vault_files")
    suspend fun allIds(): List<String>

    @Insert
    suspend fun insert(file: VaultFile)

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM vault_files")
    suspend fun clear()
}
