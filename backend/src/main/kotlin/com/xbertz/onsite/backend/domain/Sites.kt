package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.Sites
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
data class SiteDto(
    val id: String,
    val label: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val createdAtMillis: Long,
    val updatedAt: String,
)

@Serializable
data class SiteRequest(
    val id: String,
    val label: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val createdAtMillis: Long,
)

private fun ResultRow.toDto() = SiteDto(
    id = this[Sites.id].toString(),
    label = this[Sites.label],
    address = this[Sites.address],
    latitude = this[Sites.latitude],
    longitude = this[Sites.longitude],
    createdAtMillis = this[Sites.createdAtMillis],
    updatedAt = this[Sites.updatedAt].toString(),
)

class SitesRepository {
    fun list(accountId: UUID): List<SiteDto> = transaction {
        Sites.selectAll()
            .where { Sites.accountId eq accountId }
            .andWhere { Sites.deletedAt.isNull() }
            .orderBy(Sites.label, SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(accountId: UUID, req: SiteRequest): SiteDto = transaction {
        val id = UUID.fromString(req.id)
        Sites.insert {
            it[Sites.id] = id
            it[Sites.accountId] = accountId
            it[label] = req.label
            it[address] = req.address
            it[latitude] = req.latitude
            it[longitude] = req.longitude
            it[createdAtMillis] = req.createdAtMillis
            it[updatedAt] = Instant.now()
        }
        Sites.selectAll().where { Sites.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, req: SiteRequest): SiteDto? = transaction {
        val updated = Sites.update({ (Sites.id eq id) and (Sites.accountId eq accountId) }) {
            it[label] = req.label
            it[address] = req.address
            it[latitude] = req.latitude
            it[longitude] = req.longitude
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null else Sites.selectAll().where { Sites.id eq id }.single().toDto()
    }

    fun softDelete(accountId: UUID, id: UUID): Boolean = transaction {
        Sites.update({ (Sites.id eq id) and (Sites.accountId eq accountId) }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.siteRoutes(repository: SitesRepository) {
    authenticate(AUTH_JWT) {
        route("/v1/sites") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                call.respond(withContext(Dispatchers.IO) { repository.list(accountId) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val req = call.receive<SiteRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(accountId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<SiteRequest>()
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
