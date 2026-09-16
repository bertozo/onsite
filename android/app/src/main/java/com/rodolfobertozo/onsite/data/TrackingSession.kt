package com.rodolfobertozo.onsite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_sessions")
data class TrackingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyName: String?,
    val siteLabel: String?,
    val jobTypeLabel: String?,
    val startTimestampMillis: Long,
    val startLatitude: Double,
    val startLongitude: Double,
    val stopTimestampMillis: Long?,
    val stopLatitude: Double?,
    val stopLongitude: Double?
)
