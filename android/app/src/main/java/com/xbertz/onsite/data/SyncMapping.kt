package com.xbertz.onsite.data

import androidx.room.Entity

/**
 * Links a local Room row to the backend's UUID for the same row, per entity type. Its
 * presence is also how the sync engine tells "already synced, might need an update" apart
 * from "created locally, needs to be pushed for the first time".
 */
@Entity(tableName = "sync_mapping", primaryKeys = ["entityType", "localId"])
data class SyncMapping(
    val entityType: String,
    val localId: Long,
    val remoteId: String,
    val lastSyncedAtMillis: Long
)
