package com.xbertz.onsite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_mapping WHERE entityType = :entityType")
    suspend fun mappingsFor(entityType: String): List<SyncMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapping(mapping: SyncMapping)

    @Query("DELETE FROM sync_mapping WHERE entityType = :entityType AND localId = :localId")
    suspend fun deleteMapping(entityType: String, localId: Long)

    @Query("DELETE FROM sync_mapping")
    suspend fun clearAll()
}
