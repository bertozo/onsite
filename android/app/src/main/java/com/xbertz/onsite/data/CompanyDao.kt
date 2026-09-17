package com.xbertz.onsite.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Insert
    suspend fun insert(company: Company): Long

    @Update
    suspend fun update(company: Company)

    @Delete
    suspend fun delete(company: Company)

    @Query("DELETE FROM companies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM companies ORDER BY name ASC")
    fun getAll(): Flow<List<Company>>
}
