package com.xbertz.onsite.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobTypeDao {
    @Insert
    suspend fun insert(jobType: JobType): Long

    @Update
    suspend fun update(jobType: JobType)

    @Delete
    suspend fun delete(jobType: JobType)

    @Query("DELETE FROM job_types WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM job_types")
    suspend fun deleteAll()

    @Query("SELECT * FROM job_types ORDER BY name ASC")
    fun getAll(): Flow<List<JobType>>
}
