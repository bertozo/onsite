package com.xbertz.onsite.backend.identity

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.Accounts
import com.xbertz.onsite.backend.db.tables.Invites
import com.xbertz.onsite.backend.db.tables.Memberships
import com.xbertz.onsite.backend.db.tables.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class CreateInviteRequest(val email: String)

@Serializable
data class InviteDto(
    val id: String,
    val accountId: String,
    val accountName: String,
    val email: String,
    val role: String,
    val status: String,
)

class InviteRepository {
    fun createInvite(accountId: UUID, email: String): InviteDto = transaction {
        val id = UUID.randomUUID()
        val normalizedEmail = email.trim().lowercase()
        Invites.insert {
            it[Invites.id] = id
            it[Invites.accountId] = accountId
            it[Invites.email] = normalizedEmail
            it[role] = Memberships.ROLE_WORKER
            it[status] = Invites.STATUS_PENDING
            it[createdAt] = Instant.now()
        }
        loadInvite(id)
    }

    fun pendingInvitesFor(email: String): List<InviteDto> = transaction {
        (Invites innerJoin Accounts)
            .select(Invites.id, Invites.accountId, Accounts.name, Invites.email, Invites.role, Invites.status)
            .where { (Invites.email eq email.trim().lowercase()) and (Invites.status eq Invites.STATUS_PENDING) }
            .map { it.toDto() }
    }

    fun accept(inviteId: UUID, callerEmail: String): InviteDto = transaction {
        val invite = Invites.selectAll().where { Invites.id eq inviteId }.singleOrNull()
            ?: throw NotFoundException("invite not found")
        if (invite[Invites.email] != callerEmail.trim().lowercase() || invite[Invites.status] != Invites.STATUS_PENDING) {
            throw NotFoundException("invite not found")
        }

        val userId = Users
            .selectAll().where { Users.email eq callerEmail.trim().lowercase() }
            .single()[Users.id]

        val now = Instant.now()
        Memberships.insert {
            it[id] = UUID.randomUUID()
            it[Memberships.userId] = userId
            it[accountId] = invite[Invites.accountId]
            it[role] = invite[Invites.role]
            it[createdAt] = now
            it[updatedAt] = now
        }
        Invites.update({ Invites.id eq inviteId }) {
            it[status] = Invites.STATUS_ACCEPTED
            it[acceptedAt] = now
        }
        loadInvite(inviteId)
    }

    private fun loadInvite(id: UUID): InviteDto =
        (Invites innerJoin Accounts)
            .select(Invites.id, Invites.accountId, Accounts.name, Invites.email, Invites.role, Invites.status)
            .where { Invites.id eq id }
            .single()
            .toDto()

    private fun ResultRow.toDto() = InviteDto(
        id = this[Invites.id].toString(),
        accountId = this[Invites.accountId].toString(),
        accountName = this[Accounts.name],
        email = this[Invites.email],
        role = this[Invites.role],
        status = this[Invites.status],
    )
}

fun Route.inviteRoutes(repository: InviteRepository) {
    authenticate(*AUTH_JWT) {
        post("/v1/accounts/{accountId}/invites") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val accountId = UUID.fromString(call.parameters["accountId"])
            val active = call.activeAccount(user.userId, accountId)
            active.requireOwner()
            val req = call.receive<CreateInviteRequest>()
            val invite = withContext(Dispatchers.IO) { repository.createInvite(accountId, req.email) }
            call.respond(HttpStatusCode.Created, invite)
        }

        route("/v1/me/invites") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                call.respond(withContext(Dispatchers.IO) { repository.pendingInvitesFor(user.email) })
            }
            post("/{inviteId}/accept") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val inviteId = UUID.fromString(call.parameters["inviteId"])
                val invite = withContext(Dispatchers.IO) { repository.accept(inviteId, user.email) }
                call.respond(invite)
            }
        }
    }
}
