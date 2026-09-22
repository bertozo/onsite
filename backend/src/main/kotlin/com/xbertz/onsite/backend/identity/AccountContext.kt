package com.xbertz.onsite.backend.identity

import com.xbertz.onsite.backend.db.tables.Memberships
import com.xbertz.onsite.backend.plugins.rememberAccountForLog
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

/**
 * The route-facing form: resolves the account this call acts against and records it for
 * the request's log line (see plugins/Logging.kt). Who called is only half of "what
 * happened" - the other half is which account's data the call touched, and an X-Account-Id
 * the client may or may not have sent is not that answer.
 */
fun ApplicationCall.activeAccount(userId: UUID, accountId: UUID? = requestedAccountId()): ActiveAccount =
    resolveActiveAccount(userId, accountId).also { rememberAccountForLog(it.accountId) }

fun ApplicationCall.activeAccountId(userId: UUID): UUID = activeAccount(userId).accountId
