package com.xbertz.onsite.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * A job scheduled for a day on the planning calendar. Like [TrackingSession] it keeps the
 * company/site/job type as text so the plan reads the same even if those records change.
 */
@Entity(tableName = "planned_jobs")
data class PlannedJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int?,
    val companyName: String?,
    val siteLabel: String?,
    val jobTypeLabel: String?,
    val notes: String?,
    /** Backend user UUID of the teammate this is scheduled for; null on a personal account or an unassigned item. */
    val assignedUserId: String? = null
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
    val startTime: LocalTime get() = LocalTime.ofSecondOfDay(startMinute * 60L)
    val endTime: LocalTime? get() = endMinute?.let { LocalTime.ofSecondOfDay(it * 60L) }
}
