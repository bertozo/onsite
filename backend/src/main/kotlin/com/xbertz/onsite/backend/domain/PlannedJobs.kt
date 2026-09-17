package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.PlannedJobs
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
data class PlannedJobDto(
    val id: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int?,
    val companyName: String?,
    val siteLabel: String?,
    val jobTypeLabel: String?,
    val notes: String?,
    val updatedAt: String,
)

@Serializable
data class PlannedJobRequest(
    val id: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int? = null,
    val companyName: String? = null,
    val siteLabel: String? = null,
    val jobTypeLabel: String? = null,
    val notes: String? = null,
)

private fun ResultRow.toDto() = PlannedJobDto(
    id = this[PlannedJobs.id].toString(),
    dateEpochDay = this[PlannedJobs.dateEpochDay],
    startMinute = this[PlannedJobs.startMinute],
    endMinute = this[PlannedJobs.endMinute],
    companyName = this[PlannedJobs.companyName],
    siteLabel = this[PlannedJobs.siteLabel],
    jobTypeLabel = this[PlannedJobs.jobTypeLabel],
    notes = this[PlannedJobs.notes],
    updatedAt = this[PlannedJobs.updatedAt].toString(),
)

class PlannedJobsRepository {
    fun list(accountId: UUID): List<PlannedJobDto> = transaction {
        PlannedJobs.selectAll()
            .where { PlannedJobs.accountId eq accountId }
            .andWhere { PlannedJobs.deletedAt.isNull() }
            .orderBy(PlannedJobs.dateEpochDay, SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(accountId: UUID, req: PlannedJobRequest): PlannedJobDto = transaction {
        val id = UUID.fromString(req.id)
        PlannedJobs.insert {
            it[PlannedJobs.id] = id
            it[PlannedJobs.accountId] = accountId
            it[dateEpochDay] = req.dateEpochDay
            it[startMinute] = req.startMinute
            it[endMinute] = req.endMinute
            it[companyName] = req.companyName
            it[siteLabel] = req.siteLabel
            it[jobTypeLabel] = req.jobTypeLabel
            it[notes] = req.notes
            it[updatedAt] = Instant.now()
        }
        PlannedJobs.selectAll().where { PlannedJobs.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, req: PlannedJobRequest): PlannedJobDto? = transaction {
        val updated = PlannedJobs.update({ (PlannedJobs.id eq id) and (PlannedJobs.accountId eq accountId) }) {
            it[dateEpochDay] = req.dateEpochDay
            it[startMinute] = req.startMinute
            it[endMinute] = req.endMinute
            it[companyName] = req.companyName
            it[siteLabel] = req.siteLabel
            it[jobTypeLabel] = req.jobTypeLabel
            it[notes] = req.notes
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null else PlannedJobs.selectAll().where { PlannedJobs.id eq id }.single().toDto()
    }

    fun softDelete(accountId: UUID, id: UUID): Boolean = transaction {
        PlannedJobs.update({ (PlannedJobs.id eq id) and (PlannedJobs.accountId eq accountId) }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.plannedJobRoutes(repository: PlannedJobsRepository) {
    authenticate(AUTH_JWT) {
        route("/v1/planned-jobs") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                call.respond(withContext(Dispatchers.IO) { repository.list(accountId) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                val req = call.receive<PlannedJobRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(accountId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<PlannedJobRequest>()
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
