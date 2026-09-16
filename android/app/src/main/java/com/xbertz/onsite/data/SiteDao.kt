package com.xbertz.onsite.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Insert
    suspend fun insert(site: Site)

    @Update
    suspend fun update(site: Site)

    @Delete
    suspend fun delete(site: Site)

    @Query("SELECT * FROM sites ORDER BY label ASC")
    fun getAll(): Flow<List<Site>>
}
