package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.Clients
import com.xbertz.onsite.backend.identity.requestedAccountId
import com.xbertz.onsite.backend.identity.resolveActiveAccount
import com.xbertz.onsite.backend.identity.resolveActiveAccountId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class ClientDto(
    val id: String,
    val name: String,
    val abn: String?,
    val phone: String?,
    val email: String?,
    val createdAtMillis: Long,
    val hourlyRate: Double?,
    val updatedAt: String,
)

@Serializable
data class ClientRequest(
    val id: String,
    val name: String,
    val abn: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val createdAtMillis: Long,
    val hourlyRate: Double? = null,
)

private fun ResultRow.toDto() = ClientDto(
    id = this[Clients.id].toString(),
    name = this[Clients.name],
    abn = this[Clients.abn],
    phone = this[Clients.phone],
    email = this[Clients.email],
    createdAtMillis = this[Clients.createdAtMillis],
    hourlyRate = this[Clients.hourlyRate],
    updatedAt = this[Clients.updatedAt].toString(),
)

class ClientsRepository {
    fun list(accountId: UUID): List<ClientDto> = transaction {
        Clients.selectAll()
            .where { Clients.accountId eq accountId }
            .andWhere { Clients.deletedAt.isNull() }
            .orderBy(Clients.name, SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(accountId: UUID, req: ClientRequest): ClientDto = transaction {
        val id = UUID.fromString(req.id)
        val now = Instant.now()
        Clients.insert {
            it[Clients.id] = id
            it[Clients.accountId] = accountId
            it[name] = req.name
            it[abn] = req.abn
            it[phone] = req.phone
            it[email] = req.email
            it[createdAtMillis] = req.createdAtMillis
            it[hourlyRate] = req.hourlyRate
            it[updatedAt] = now
        }
        Clients.selectAll().where { Clients.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, req: ClientRequest): ClientDto? = transaction {
        val updated = Clients.update({ (Clients.id eq id) and (Clients.accountId eq accountId) }) {
            it[name] = req.name
            it[abn] = req.abn
            it[phone] = req.phone
            it[email] = req.email
            it[hourlyRate] = req.hourlyRate
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null else Clients.selectAll().where { Clients.id eq id }.single().toDto()
    }

    fun softDelete(accountId: UUID, id: UUID): Boolean = transaction {
        Clients.update({ (Clients.id eq id) and (Clients.accountId eq accountId) }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.clientRoutes(repository: ClientsRepository) {
    authenticate(*AUTH_JWT) {
        route("/v1/clients") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                call.respond(withContext(Dispatchers.IO) { repository.list(accountId) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val req = call.receive<ClientRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(accountId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<ClientRequest>()
                val result = withContext(Dispatchers.IO) { repository.update(accountId, id, req) }
                if (result == null) call.respond(HttpStatusCode.NotFound) else call.respond(result)
            }
            delete("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val id = UUID.fromString(call.parameters["id"])
                val deleted = withContext(Dispatchers.IO) { repository.softDelete(accountId, id) }
                call.respond(if (deleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }
        }
    }
}
