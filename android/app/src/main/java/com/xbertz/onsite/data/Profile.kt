package com.xbertz.onsite.data

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
    val photoPath: String?,
    val bankBsb: String?,
    val bankAccount: String?
)
