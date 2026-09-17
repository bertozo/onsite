package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.Companies
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
data class CompanyDto(
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
data class CompanyRequest(
    val id: String,
    val name: String,
    val abn: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val createdAtMillis: Long,
    val hourlyRate: Double? = null,
)

private fun ResultRow.toDto() = CompanyDto(
    id = this[Companies.id].toString(),
    name = this[Companies.name],
    abn = this[Companies.abn],
    phone = this[Companies.phone],
    email = this[Companies.email],
    createdAtMillis = this[Companies.createdAtMillis],
    hourlyRate = this[Companies.hourlyRate],
    updatedAt = this[Companies.updatedAt].toString(),
)

class CompaniesRepository {
    fun list(accountId: UUID): List<CompanyDto> = transaction {
        Companies.selectAll()
            .where { Companies.accountId eq accountId }
            .andWhere { Companies.deletedAt.isNull() }
            .orderBy(Companies.name, SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(accountId: UUID, req: CompanyRequest): CompanyDto = transaction {
        val id = UUID.fromString(req.id)
        val now = Instant.now()
        Companies.insert {
            it[Companies.id] = id
            it[Companies.accountId] = accountId
            it[name] = req.name
            it[abn] = req.abn
            it[phone] = req.phone
            it[email] = req.email
            it[createdAtMillis] = req.createdAtMillis
            it[hourlyRate] = req.hourlyRate
            it[updatedAt] = now
        }
        Companies.selectAll().where { Companies.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, req: CompanyRequest): CompanyDto? = transaction {
        val updated = Companies.update({ (Companies.id eq id) and (Companies.accountId eq accountId) }) {
            it[name] = req.name
            it[abn] = req.abn
            it[phone] = req.phone
            it[email] = req.email
            it[hourlyRate] = req.hourlyRate
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null else Companies.selectAll().where { Companies.id eq id }.single().toDto()
    }

    fun softDelete(accountId: UUID, id: UUID): Boolean = transaction {
        Companies.update({ (Companies.id eq id) and (Companies.accountId eq accountId) }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.companyRoutes(repository: CompaniesRepository) {
    authenticate(AUTH_JWT) {
        route("/v1/companies") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                call.respond(withContext(Dispatchers.IO) { repository.list(accountId) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val req = call.receive<CompanyRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(accountId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<CompanyRequest>()
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
