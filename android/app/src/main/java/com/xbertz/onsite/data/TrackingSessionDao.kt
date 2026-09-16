package com.xbertz.onsite.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingSessionDao {
    @Insert
    suspend fun insert(session: TrackingSession): Long

    @Update
    suspend fun update(session: TrackingSession)

    @Delete
    suspend fun delete(session: TrackingSession)

    @Query("SELECT * FROM tracking_sessions ORDER BY startTimestampMillis DESC, id DESC")
    fun getAll(): Flow<List<TrackingSession>>

    @Query(
        "SELECT * FROM tracking_sessions WHERE stopTimestampMillis IS NULL " +
            "ORDER BY startTimestampMillis DESC, id DESC LIMIT 1"
    )
    suspend fun getActiveSession(): TrackingSession?

    @Query("SELECT * FROM tracking_sessions WHERE id = :id")
    suspend fun getById(id: Long): TrackingSession?

    /** Sessions store the company by name, so a rename must be propagated to keep reports/invoices matching. */
    @Query("UPDATE tracking_sessions SET companyName = :newName WHERE companyName = :oldName")
    suspend fun renameCompany(oldName: String, newName: String)

    /** Same as [renameCompany] for the job type label. */
    @Query("UPDATE tracking_sessions SET jobTypeLabel = :newName WHERE jobTypeLabel = :oldName")
    suspend fun renameJobType(oldName: String, newName: String)
}
