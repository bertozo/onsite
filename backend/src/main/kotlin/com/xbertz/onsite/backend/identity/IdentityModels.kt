package com.xbertz.onsite.backend.identity

import kotlinx.serialization.Serializable

@Serializable
data class AccountMembershipDto(
    val accountId: String,
    val accountName: String,
    val accountKind: String,
    val role: String,
)

@Serializable
data class MeResponse(
    val userId: String,
    val email: String,
    val displayName: String?,
    val memberships: List<AccountMembershipDto>,
)

@Serializable
data class MemberDto(
    val userId: String,
    val email: String,
    val displayName: String?,
    val role: String,
)
