package com.xbertz.onsite.backend.identity

import com.xbertz.onsite.backend.db.tables.Memberships
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.header
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Every domain route acts against exactly one account. The header is optional and falls
 * back to the caller's first membership (their personal account, for a user who hasn't
 * been invited anywhere) - but the account_id is never trusted straight from the client,
 * only a membership row proves the caller may act as it, and that same row's role is what
 * write routes check before letting a WORKER touch the shared catalog.
 */
class AccountAccessDenied : Exception("caller has no membership in the requested account")
class OwnerRequired : Exception("this action requires the OWNER role")

data class ActiveAccount(val accountId: UUID, val role: String) {
    val isOwner: Boolean get() = role == Memberships.ROLE_OWNER

    fun requireOwner() {
        if (!isOwner) throw OwnerRequired()
    }
}

fun ApplicationCall.requestedAccountId(): UUID? =
    request.header("X-Account-Id")?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
            ?: throw BadRequestException("X-Account-Id must be a UUID")
    }

fun resolveActiveAccount(userId: UUID, requestedAccountId: UUID?): ActiveAccount = transaction {
    val memberships = Memberships.selectAll().where { Memberships.userId eq userId }.toList()
    if (memberships.isEmpty()) {
        throw AccountAccessDenied()
    }
    val membership = if (requestedAccountId == null) {
        memberships.first()
    } else {
        memberships.firstOrNull { it[Memberships.accountId] == requestedAccountId } ?: throw AccountAccessDenied()
    }
    ActiveAccount(membership[Memberships.accountId], membership[Memberships.role])
}

fun resolveActiveAccountId(userId: UUID, requestedAccountId: UUID?): UUID =
    resolveActiveAccount(userId, requestedAccountId).accountId
