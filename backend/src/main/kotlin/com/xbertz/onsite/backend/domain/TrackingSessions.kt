package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.TrackingSessions
import com.xbertz.onsite.backend.identity.requestedAccountId
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
data class TrackingSessionDto(
    val id: String,
    val companyName: String?,
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
    val updatedAt: String,
)

@Serializable
data class TrackingSessionRequest(
    val id: String,
    val companyName: String? = null,
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
    companyName = this[TrackingSessions.companyName],
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
    updatedAt = this[TrackingSessions.updatedAt].toString(),
)

class TrackingSessionsRepository {
    fun list(accountId: UUID): List<TrackingSessionDto> = transaction {
        TrackingSessions.selectAll()
            .where { TrackingSessions.accountId eq accountId }
            .andWhere { TrackingSessions.deletedAt.isNull() }
            .orderBy(TrackingSessions.startTimestampMillis, SortOrder.DESC)
            .map { it.toDto() }
    }

    fun create(accountId: UUID, req: TrackingSessionRequest): TrackingSessionDto = transaction {
        val id = UUID.fromString(req.id)
        TrackingSessions.insert {
            it[TrackingSessions.id] = id
            it[TrackingSessions.accountId] = accountId
            it[companyName] = req.companyName
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
        TrackingSessions.selectAll().where { TrackingSessions.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, req: TrackingSessionRequest): TrackingSessionDto? = transaction {
        val updated = TrackingSessions.update({ (TrackingSessions.id eq id) and (TrackingSessions.accountId eq accountId) }) {
            it[companyName] = req.companyName
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

    fun softDelete(accountId: UUID, id: UUID): Boolean = transaction {
        TrackingSessions.update({ (TrackingSessions.id eq id) and (TrackingSessions.accountId eq accountId) }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.trackingSessionRoutes(repository: TrackingSessionsRepository) {
    authenticate(AUTH_JWT) {
        route("/v1/sessions") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                call.respond(withContext(Dispatchers.IO) { repository.list(accountId) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                val req = call.receive<TrackingSessionRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(accountId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<TrackingSessionRequest>()
                val result = withContext(Dispatchers.IO) { repository.update(accountId, id, req) }
                if (result == null) call.respond(HttpStatusCode.NotFound) else call.respond(result)
            }
            delete("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                val id = UUID.fromString(call.parameters["id"])
                val deleted = withContext(Dispatchers.IO) { repository.softDelete(accountId, id) }
                call.respond(if (deleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }
        }
    }
}
