package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.JobTypes
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
data class JobTypeDto(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAt: String,
)

@Serializable
data class JobTypeRequest(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
)

private fun ResultRow.toDto() = JobTypeDto(
    id = this[JobTypes.id].toString(),
    name = this[JobTypes.name],
    createdAtMillis = this[JobTypes.createdAtMillis],
    updatedAt = this[JobTypes.updatedAt].toString(),
)

class JobTypesRepository {
    fun list(accountId: UUID): List<JobTypeDto> = transaction {
        JobTypes.selectAll()
            .where { JobTypes.accountId eq accountId }
            .andWhere { JobTypes.deletedAt.isNull() }
            .orderBy(JobTypes.name, SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(accountId: UUID, req: JobTypeRequest): JobTypeDto = transaction {
        val id = UUID.fromString(req.id)
        JobTypes.insert {
            it[JobTypes.id] = id
            it[JobTypes.accountId] = accountId
            it[name] = req.name
            it[createdAtMillis] = req.createdAtMillis
            it[updatedAt] = Instant.now()
        }
        JobTypes.selectAll().where { JobTypes.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, req: JobTypeRequest): JobTypeDto? = transaction {
        val updated = JobTypes.update({ (JobTypes.id eq id) and (JobTypes.accountId eq accountId) }) {
            it[name] = req.name
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null else JobTypes.selectAll().where { JobTypes.id eq id }.single().toDto()
    }

    fun softDelete(accountId: UUID, id: UUID): Boolean = transaction {
        JobTypes.update({ (JobTypes.id eq id) and (JobTypes.accountId eq accountId) }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.jobTypeRoutes(repository: JobTypesRepository) {
    authenticate(*AUTH_JWT) {
        route("/v1/job-types") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                call.respond(withContext(Dispatchers.IO) { repository.list(accountId) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val req = call.receive<JobTypeRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(accountId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<JobTypeRequest>()
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
