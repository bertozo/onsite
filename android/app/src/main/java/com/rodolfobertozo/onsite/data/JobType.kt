package com.rodolfobertozo.onsite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "job_types")
data class JobType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMillis: Long
)
