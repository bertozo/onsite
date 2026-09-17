package com.xbertz.onsite.data

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
    val stopLongitude: Double?,
    /** Rate for this session only, when it differs from the company default. Null = use the company's. */
    val hourlyRate: Double? = null,
    /** Set once the session has been billed on an invoice (see [Invoice]). */
    val invoiceId: Long? = null
)
