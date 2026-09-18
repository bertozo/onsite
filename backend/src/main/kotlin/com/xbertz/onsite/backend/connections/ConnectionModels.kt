package com.xbertz.onsite.backend.connections

import kotlinx.serialization.Serializable

@Serializable
data class CreateConnectionInviteRequest(val email: String)

@Serializable
data class ConnectionInviteDto(
    val id: String,
    val employerAccountId: String,
    val employerAccountName: String,
    val email: String,
    val status: String,
)

@Serializable
data class AcceptConnectionInviteRequest(val companyId: String)

@Serializable
data class ConnectionDto(
    val id: String,
    val employerAccountId: String,
    val employerAccountName: String,
    val workerAccountId: String,
    val workerCompanyId: String,
    val workerCompanyName: String,
    val status: String,
)

@Serializable
data class ConnectionPlannedJobRequest(
    val id: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int? = null,
    val siteLabel: String? = null,
    val jobTypeLabel: String? = null,
    val notes: String? = null,
)

@Serializable
data class ConnectionSessionDto(
    val id: String,
    val siteLabel: String?,
    val jobTypeLabel: String?,
    val startTimestampMillis: Long,
    val stopTimestampMillis: Long?,
)
