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

/** The invoice-header details of one user; see V6__user_profiles.sql for updatedAtMillis' role. */
@Serializable
data class ProfileDto(
    val name: String,
    val role: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val abn: String? = null,
    val bankBsb: String? = null,
    val bankAccount: String? = null,
    val updatedAtMillis: Long,
)

@Serializable
data class MemberDto(
    val userId: String,
    val email: String,
    val displayName: String?,
    val role: String,
)
