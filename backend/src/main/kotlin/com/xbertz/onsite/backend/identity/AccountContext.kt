package com.xbertz.onsite.backend.identity

import com.xbertz.onsite.backend.db.tables.Memberships
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.header
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Every domain route acts against exactly one account. Until Phase 4 adds real account
 * switching in the clients, the header is optional and falls back to the caller's first
 * membership (today always their personal account) - but the account_id is never trusted
 * straight from the client, only a membership row proves the caller may act as it.
 */
class AccountAccessDenied : Exception("caller has no membership in the requested account")

fun ApplicationCall.requestedAccountId(): UUID? =
    request.header("X-Account-Id")?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
            ?: throw BadRequestException("X-Account-Id must be a UUID")
    }

fun resolveActiveAccountId(userId: UUID, requestedAccountId: UUID?): UUID = transaction {
    val memberships = Memberships.selectAll().where { Memberships.userId eq userId }.toList()
    if (memberships.isEmpty()) {
        throw AccountAccessDenied()
    }
    if (requestedAccountId == null) {
        return@transaction memberships.first()[Memberships.accountId]
    }
    val membership = memberships.firstOrNull { it[Memberships.accountId] == requestedAccountId }
        ?: throw AccountAccessDenied()
    membership[Memberships.accountId]
}
