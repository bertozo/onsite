package com.xbertz.onsite.backend.domain

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.db.tables.Invoices
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
data class InvoiceDto(
    val id: String,
    val number: String,
    val companyName: String,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val issueDateEpochDay: Long,
    val totalHours: Double,
    val totalAmount: Double?,
    val hourlyRate: Double?,
    val status: String,
    val sentAtMillis: Long?,
    val paidAtMillis: Long?,
    val pdfPath: String?,
    val notes: String?,
    val createdAtMillis: Long,
    val updatedAt: String,
)

@Serializable
data class InvoiceRequest(
    val id: String,
    val number: String,
    val companyName: String,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val issueDateEpochDay: Long,
    val totalHours: Double,
    val totalAmount: Double? = null,
    val hourlyRate: Double? = null,
    val status: String = Invoices.STATUS_DRAFT,
    val sentAtMillis: Long? = null,
    val paidAtMillis: Long? = null,
    val pdfPath: String? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
)

private fun ResultRow.toDto() = InvoiceDto(
    id = this[Invoices.id].toString(),
    number = this[Invoices.number],
    companyName = this[Invoices.companyName],
    periodStartEpochDay = this[Invoices.periodStartEpochDay],
    periodEndEpochDay = this[Invoices.periodEndEpochDay],
    issueDateEpochDay = this[Invoices.issueDateEpochDay],
    totalHours = this[Invoices.totalHours],
    totalAmount = this[Invoices.totalAmount],
    hourlyRate = this[Invoices.hourlyRate],
    status = this[Invoices.status],
    sentAtMillis = this[Invoices.sentAtMillis],
    paidAtMillis = this[Invoices.paidAtMillis],
    pdfPath = this[Invoices.pdfPath],
    notes = this[Invoices.notes],
    createdAtMillis = this[Invoices.createdAtMillis],
    updatedAt = this[Invoices.updatedAt].toString(),
)

class InvoicesRepository {
    fun list(accountId: UUID): List<InvoiceDto> = transaction {
        Invoices.selectAll()
            .where { Invoices.accountId eq accountId }
            .andWhere { Invoices.deletedAt.isNull() }
            .orderBy(Invoices.issueDateEpochDay, SortOrder.DESC)
            .map { it.toDto() }
    }

    fun create(accountId: UUID, req: InvoiceRequest): InvoiceDto = transaction {
        val id = UUID.fromString(req.id)
        Invoices.insert {
            it[Invoices.id] = id
            it[Invoices.accountId] = accountId
            it[number] = req.number
            it[companyName] = req.companyName
            it[periodStartEpochDay] = req.periodStartEpochDay
            it[periodEndEpochDay] = req.periodEndEpochDay
            it[issueDateEpochDay] = req.issueDateEpochDay
            it[totalHours] = req.totalHours
            it[totalAmount] = req.totalAmount
            it[hourlyRate] = req.hourlyRate
            it[status] = req.status
            it[sentAtMillis] = req.sentAtMillis
            it[paidAtMillis] = req.paidAtMillis
            it[pdfPath] = req.pdfPath
            it[notes] = req.notes
            it[createdAtMillis] = req.createdAtMillis
            it[updatedAt] = Instant.now()
        }
        Invoices.selectAll().where { Invoices.id eq id }.single().toDto()
    }

    fun update(accountId: UUID, id: UUID, req: InvoiceRequest): InvoiceDto? = transaction {
        val updated = Invoices.update({ (Invoices.id eq id) and (Invoices.accountId eq accountId) }) {
            it[number] = req.number
            it[companyName] = req.companyName
            it[periodStartEpochDay] = req.periodStartEpochDay
            it[periodEndEpochDay] = req.periodEndEpochDay
            it[issueDateEpochDay] = req.issueDateEpochDay
            it[totalHours] = req.totalHours
            it[totalAmount] = req.totalAmount
            it[hourlyRate] = req.hourlyRate
            it[status] = req.status
            it[sentAtMillis] = req.sentAtMillis
            it[paidAtMillis] = req.paidAtMillis
            it[pdfPath] = req.pdfPath
            it[notes] = req.notes
            it[updatedAt] = Instant.now()
        }
        if (updated == 0) null else Invoices.selectAll().where { Invoices.id eq id }.single().toDto()
    }

    fun softDelete(accountId: UUID, id: UUID): Boolean = transaction {
        Invoices.update({ (Invoices.id eq id) and (Invoices.accountId eq accountId) }) {
            it[deletedAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }
}

fun Route.invoiceRoutes(repository: InvoicesRepository) {
    authenticate(AUTH_JWT) {
        route("/v1/invoices") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccountId(user.userId, call.requestedAccountId())
                call.respond(withContext(Dispatchers.IO) { repository.list(accountId) })
            }
            post {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val req = call.receive<InvoiceRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { repository.create(accountId, req) })
            }
            put("/{id}") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val accountId = resolveActiveAccount(user.userId, call.requestedAccountId()).apply { requireOwner() }.accountId
                val id = UUID.fromString(call.parameters["id"])
                val req = call.receive<InvoiceRequest>()
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
