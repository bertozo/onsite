package com.rodolfobertozo.onsite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companies")
data class Company(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val abn: String?,
    val phone: String?,
    val email: String?,
    val createdAtMillis: Long
)
