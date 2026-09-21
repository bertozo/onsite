package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.TrackingSessions
import com.xbertz.onsite.backend.identity.requestedAccountId
import com.xbertz.onsite.backend.identity.resolveActiveAccount
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class TrackingSessionDto(
    val id: String,
    val clientName: String?,
    val siteLabel: String?,
    val jobTypeLabel: String?,
    val startTimestampMillis: Long,
    val startLatitude: Double,
    val startLongitude: Double,
    val stopTimestampMillis: Long?,
    val stopLatitude: Double?,
    val stopLongitude: Double?,
    val hourlyRate: Double?,
    val invoiceId: String?,
    val createdByUserId: String?,
    val updatedAt: String,
)

@Serializable
data class TrackingSessionRequest(
    val id: String,
    val clientName: String? = null,
    val siteLabel: String? = null,
    val jobTypeLabel: String? = null,
    val startTimestampMillis: Long,
    val startLatitude: Double,
    val startLongitude: Double,
    val stopTimestampMillis: Long? = null,
    val stopLatitude: Double? = null,
    val stopLongitude: Double? = null,
    val hourlyRate: Double? = null,
    val invoiceId: String? = null,
)

private fun ResultRow.toDto() = TrackingSessionDto(
    id = this[TrackingSessions.id].toString(),
    clientName = this[TrackingSessions.clientName],
    siteLabel = this[TrackingSessions.siteLabel],
    jobTypeLabel = this[TrackingSessions.jobTypeLabel],
    startTimestampMillis = this[TrackingSessions.startTimestampMillis],
    startLatitude = this[TrackingSessions.startLatitude],
    startLongitude = this[TrackingSessions.startLongitude],
    stopTimestampMillis = this[TrackingSessions.stopTimestampMillis],
    stopLatitude = this[TrackingSessions.stopLatitude],
    stopLongitude = this[TrackingSessions.stopLongitude],
    hourlyRate = this[TrackingSessions.hourlyRate],
    invoiceId = this[TrackingSessions.invoiceId]?.toString(),
    createdByUserId = this[TrackingSessions.createdByUserId]?.toString(),
    updatedAt = this[TrackingSessions.updatedAt].toString(),
)

class TrackingSessionsRepository {
    /** [ownerOnlyUserId] scopes the list to one user's own rows - a WORKER's view of the account. */
    fun list(accountId: UUID, ownerOnlyUserId: UUID?): List<TrackingSessionDto> = transaction {
        var query = TrackingSessions.selectAll()
            .where { TrackingSessions.accountId eq accountId }
            .andWhere { TrackingSessions.deletedAt.isNull() }
        if (ownerOnlyUserId != null) {
            query = query.andWhere { TrackingSessions.createdByUserId eq ownerOnlyUserId }
        }
        query.orderBy(TrackingSessions.startTimestampMillis, SortOrder.DESC).map { it.toDto() }
    }

    fun create(accountId: UUID, createdByUserId: UUID, req: TrackingSessionRequest): TrackingSessionDto = transaction {
        val id = UUID.fromString(req.id)
        TrackingSessions.insert {
            it[TrackingSessions.id] = id
            it[TrackingSessions.accountId] = accountId
            it[clientName] = req.clientName
            it[siteLabel] = req.siteLabel
            it[jobTypeLabel] = req.jobTypeLabel
            it[startTimestampMillis] = req.startTimestampMillis
            it[startLatitude] = req.startLatitude
            it[startLongitude] = req.startLongitude
            it[stopTimestampMillis] = req.stopTimestampMillis
            it[stopLatitude] = req.stopLatitude
            it[stopLongitude] = req.stopLongitude
            it[hourlyRate] = req.hourlyRate
            it[invoiceId] = req.invoiceId?.let(UUID::fromString)
            it[TrackingSessions.createdByUserId] = createdByUserId
            it[updatedAt] = Instant.now()
        }
        TrackingSessions.selectAll().where { TrackingSessions.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, ownerOnlyUserId: UUID?, req: TrackingSessionRequest): TrackingSessionDto? = transaction {
        var condition = (TrackingSessions.id eq id) and (TrackingSessions.accountId eq accountId)
        if (ownerOnlyUserId != null) condition = condition and (TrackingSessions.createdByUserId eq ownerOnlyUserId)
        val updated = TrackingSessions.update({ condition }) {
            it[clientName] = req.clientName
            it[siteLabel] = req.siteLabel
            it[jobTypeLabel] = req.jobTypeLabel
            it[startTimestampMillis] = req.startTimestampMillis
            it[startLatitude] = req.startLatitude
            it[startLongitude] = req.startLongitude
            it[stopTimestampMillis] = req.stopTimestampMillis
            it[stopLatitude] = req.stopLatitude
            it[stopLongitude] = req.stopLongitude
            it[hourlyRate] = req.hourlyRate
            it[invoiceId] = req.invoiceId?.let(UUID::fromString)
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null else TrackingSessions.selectAll().where { TrackingSessions.id eq id }.single().toDto()
    }

    fun softDelete(accountId: UUID, id: UUID, ownerOnlyUserId: UUID?): Boolean = transaction {
        var condition = (TrackingSessions.id eq id) and (TrackingSessions.accountId eq accountId)
        if (ownerOnlyUserId != null) condition = condition and (TrackingSessions.createdByUserId eq ownerOnlyUserId)
        TrackingSessions.update({ condition }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.trackingSessionRoutes(repository: TrackingSessionsRepository) {
    authenticate(*AUTH_JWT) {
        route("/v1/sessions") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val active = resolveActiveAccount(user.userId, call.requestedAccountId())
                val ownOnly = if (active.isOwner) null else user.userId
                call.respond(withContext(Dispatchers.IO) { repository.list(active.accountId, ownOnly) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val active = resolveActiveAccount(user.userId, call.requestedAccountId())
                val req = call.receive<TrackingSessionRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(active.accountId, user.userId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val active = resolveActiveAccount(user.userId, call.requestedAccountId())
                val ownOnly = if (active.isOwner) null else user.userId
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<TrackingSessionRequest>()
                val result = withContext(Dispatchers.IO) { repository.update(active.accountId, id, ownOnly, req) }
                if (result == null) call.respond(HttpStatusCode.NotFound) else call.respond(result)
            }
            delete("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val active = resolveActiveAccount(user.userId, call.requestedAccountId())
                val ownOnly = if (active.isOwner) null else user.userId
                val id = UUID.fromString(call.parameters["id"])
                val deleted = withContext(Dispatchers.IO) { repository.softDelete(active.accountId, id, ownOnly) }
                call.respond(if (deleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }
        }
    }
}
