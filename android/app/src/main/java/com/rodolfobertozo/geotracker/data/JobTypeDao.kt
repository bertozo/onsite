package com.rodolfobertozo.geotracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobTypeDao {
    @Insert
    suspend fun insert(jobType: JobType)

    @Update
    suspend fun update(jobType: JobType)

    @Delete
    suspend fun delete(jobType: JobType)

    @Query("SELECT * FROM job_types ORDER BY name ASC")
    fun getAll(): Flow<List<JobType>>
}
