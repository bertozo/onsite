package com.xbertz.onsite.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val phone: String?,
    val email: String?,
    val role: String?,
    val abn: String?,
    /** Local file only - the photo is never synced (see SyncEngine.syncProfile). */
    val photoPath: String?,
    val bankBsb: String?,
    val bankAccount: String?,
    /** When this device last edited the profile; 0 for rows saved before sync existed. */
    @ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = 0
)
