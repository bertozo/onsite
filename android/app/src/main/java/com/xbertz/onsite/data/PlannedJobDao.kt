package com.xbertz.onsite.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedJobDao {
    @Insert
    suspend fun insert(job: PlannedJob): Long

    @Update
    suspend fun update(job: PlannedJob)

    @Delete
    suspend fun delete(job: PlannedJob)

    @Query("SELECT * FROM planned_jobs ORDER BY dateEpochDay ASC, startMinute ASC, id ASC")
    fun getAll(): Flow<List<PlannedJob>>

    /** Same as [TrackingSessionDao.renameCompany]: plans store the company by name. */
    @Query("UPDATE planned_jobs SET companyName = :newName WHERE companyName = :oldName")
    suspend fun renameCompany(oldName: String, newName: String)

    @Query("UPDATE planned_jobs SET jobTypeLabel = :newName WHERE jobTypeLabel = :oldName")
    suspend fun renameJobType(oldName: String, newName: String)
}
