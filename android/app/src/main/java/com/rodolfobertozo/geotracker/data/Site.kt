package com.rodolfobertozo.geotracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val createdAtMillis: Long
)
