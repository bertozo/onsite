package com.xbertz.onsite.backend.connections

import com.xbertz.onsite.backend.db.tables.Accounts
import com.xbertz.onsite.backend.db.tables.Clients
import com.xbertz.onsite.backend.db.tables.ConnectionInvites
import com.xbertz.onsite.backend.db.tables.Connections
import com.xbertz.onsite.backend.db.tables.PlannedJobs
import com.xbertz.onsite.backend.db.tables.TrackingSessions
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class ConnectionAccessDenied : Exception("caller is not party to this connection")

/**
 * The bridge between a worker's own account and a client's real account, without the worker
 * ever becoming a member of it: a `connections` row just says "this Client in the worker's
 * account is this client's account". Every cross-account action below re-derives the
 * client's Client *name* for the worker from that row rather than trusting anything the
 * caller sends, and every read is scoped to just that one client's sessions - never the
 * worker's whole account.
 */
class ConnectionsRepository {
    fun createInvite(employerAccountId: UUID, email: String): ConnectionInviteDto = transaction {
        val id = UUID.randomUUID()
        ConnectionInvites.insert {
            it[ConnectionInvites.id] = id
            it[ConnectionInvites.employerAccountId] = employerAccountId
            it[ConnectionInvites.email] = email.trim().lowercase()
            it[status] = ConnectionInvites.STATUS_PENDING
            it[createdAt] = Instant.now()
        }
        loadInvite(id)
    }

    fun pendingInvitesFor(email: String): List<ConnectionInviteDto> = transaction {
        (ConnectionInvites innerJoin Accounts)
            .select(ConnectionInvites.id, ConnectionInvites.employerAccountId, Accounts.name, ConnectionInvites.email, ConnectionInvites.status)
            .where { (ConnectionInvites.email eq email.trim().lowercase()) and (ConnectionInvites.status eq ConnectionInvites.STATUS_PENDING) }
            .map { it.toInviteDto() }
    }

    fun acceptInvite(inviteId: UUID, callerEmail: String, workerAccountId: UUID, workerUserId: UUID, clientId: UUID): ConnectionDto = transaction {
        val invite = ConnectionInvites.selectAll().where { ConnectionInvites.id eq inviteId }.singleOrNull()
            ?: throw NotFoundException("invite not found")
        if (invite[ConnectionInvites.email] != callerEmail.trim().lowercase() || invite[ConnectionInvites.status] != ConnectionInvites.STATUS_PENDING) {
            throw NotFoundException("invite not found")
        }
        val clientBelongsToCaller = Clients.selectAll()
            .where { (Clients.id eq clientId) and (Clients.accountId eq workerAccountId) and Clients.deletedAt.isNull() }
            .any()
        if (!clientBelongsToCaller) throw NotFoundException("client not found in this account")

        val id = UUID.randomUUID()
        val now = Instant.now()
        Connections.insert {
            it[Connections.id] = id
            it[employerAccountId] = invite[ConnectionInvites.employerAccountId]
            it[Connections.workerAccountId] = workerAccountId
            it[Connections.workerUserId] = workerUserId
            it[workerClientId] = clientId
            it[status] = Connections.STATUS_ACTIVE
            it[createdAt] = now
        }
        ConnectionInvites.update({ ConnectionInvites.id eq inviteId }) {
            it[status] = ConnectionInvites.STATUS_ACCEPTED
            it[acceptedAt] = now
        }
        loadConnection(id)
    }

    fun connectionsForEmployer(employerAccountId: UUID): List<ConnectionDto> = transaction {
        activeConnectionsQuery()
            .andWhere { Connections.employerAccountId eq employerAccountId }
            .map { it.toConnectionDto() }
    }

    fun connectionsForWorker(workerUserId: UUID): List<ConnectionDto> = transaction {
        activeConnectionsQuery()
            .andWhere { Connections.workerUserId eq workerUserId }
            .map { it.toConnectionDto() }
    }

    /** Either side of the connection may revoke it. */
    fun revoke(connectionId: UUID, callerAccountId: UUID, callerUserId: UUID): Boolean = transaction {
        val row = Connections.selectAll().where { Connections.id eq connectionId }.singleOrNull() ?: return@transaction false
        val isEmployer = row[Connections.employerAccountId] == callerAccountId
        val isWorker = row[Connections.workerUserId] == callerUserId
        if (!isEmployer && !isWorker) throw ConnectionAccessDenied()
        Connections.update({ Connections.id eq connectionId }) {
            it[status] = Connections.STATUS_REVOKED
            it[revokedAt] = Instant.now()
        } > 0
    }

    fun createPlannedJobForConnection(
        connectionId: UUID,
        callerEmployerAccountId: UUID,
        createdByUserId: UUID,
        req: ConnectionPlannedJobRequest,
    ): PlannedJobDtoForConnection = transaction {
        val connection = activeConnection(connectionId)
        if (connection[Connections.employerAccountId] != callerEmployerAccountId) throw ConnectionAccessDenied()
        val clientName = Clients.selectAll().where { Clients.id eq connection[Connections.workerClientId] }.single()[Clients.name]

        val id = UUID.fromString(req.id)
        PlannedJobs.insert {
            it[PlannedJobs.id] = id
            it[accountId] = connection[Connections.workerAccountId]
            it[dateEpochDay] = req.dateEpochDay
            it[startMinute] = req.startMinute
            it[endMinute] = req.endMinute
            it[PlannedJobs.clientName] = clientName
            it[siteLabel] = req.siteLabel
            it[jobTypeLabel] = req.jobTypeLabel
            it[notes] = req.notes
            it[PlannedJobs.createdByUserId] = createdByUserId
            it[assignedUserId] = connection[Connections.workerUserId]
            it[updatedAt] = Instant.now()
        }
        PlannedJobDtoForConnection(id.toString(), req.dateEpochDay, req.startMinute, req.endMinute, req.siteLabel, req.jobTypeLabel, req.notes)
    }

    fun sessionsForConnection(connectionId: UUID, callerEmployerAccountId: UUID): List<ConnectionSessionDto> = transaction {
        val connection = activeConnection(connectionId)
        if (connection[Connections.employerAccountId] != callerEmployerAccountId) throw ConnectionAccessDenied()
        val clientName = Clients.selectAll().where { Clients.id eq connection[Connections.workerClientId] }.single()[Clients.name]

        TrackingSessions.selectAll()
            .where { TrackingSessions.accountId eq connection[Connections.workerAccountId] }
            .andWhere { TrackingSessions.clientName eq clientName }
            .andWhere { TrackingSessions.deletedAt.isNull() }
            .map {
                ConnectionSessionDto(
                    id = it[TrackingSessions.id].toString(),
                    siteLabel = it[TrackingSessions.siteLabel],
                    jobTypeLabel = it[TrackingSessions.jobTypeLabel],
                    startTimestampMillis = it[TrackingSessions.startTimestampMillis],
                    stopTimestampMillis = it[TrackingSessions.stopTimestampMillis],
                )
            }
    }

    private fun activeConnection(connectionId: UUID): ResultRow =
        Connections.selectAll()
            .where { (Connections.id eq connectionId) and (Connections.status eq Connections.STATUS_ACTIVE) }
            .singleOrNull() ?: throw NotFoundException("connection not found")

    private fun activeConnectionsQuery() =
        // Connections has two FKs into accounts (employer and worker), so the join target
        // column has to be spelled out - Exposed's auto-FK join would otherwise be ambiguous.
        Connections
            .join(Accounts, JoinType.INNER, onColumn = Connections.employerAccountId, otherColumn = Accounts.id)
            .join(Clients, JoinType.INNER, onColumn = Connections.workerClientId, otherColumn = Clients.id)
            .select(
                Connections.id, Connections.employerAccountId, Accounts.name,
                Connections.workerAccountId, Connections.workerClientId, Clients.name, Connections.status
            )
            .where { Connections.status eq Connections.STATUS_ACTIVE }

    private fun loadInvite(id: UUID): ConnectionInviteDto =
        (ConnectionInvites innerJoin Accounts)
            .select(ConnectionInvites.id, ConnectionInvites.employerAccountId, Accounts.name, ConnectionInvites.email, ConnectionInvites.status)
            .where { ConnectionInvites.id eq id }
            .single()
            .toInviteDto()

    private fun loadConnection(id: UUID): ConnectionDto =
        activeConnectionsQuery()
            .andWhere { Connections.id eq id }
            .single()
            .toConnectionDto()

    private fun ResultRow.toInviteDto() = ConnectionInviteDto(
        id = this[ConnectionInvites.id].toString(),
        employerAccountId = this[ConnectionInvites.employerAccountId].toString(),
        employerAccountName = this[Accounts.name],
        email = this[ConnectionInvites.email],
        status = this[ConnectionInvites.status],
    )

    private fun ResultRow.toConnectionDto() = ConnectionDto(
        id = this[Connections.id].toString(),
        employerAccountId = this[Connections.employerAccountId].toString(),
        employerAccountName = this[Accounts.name],
        workerAccountId = this[Connections.workerAccountId].toString(),
        workerClientId = this[Connections.workerClientId].toString(),
        workerClientName = this[Clients.name],
        status = this[Connections.status],
    )
}

@Serializable
data class PlannedJobDtoForConnection(
    val id: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int?,
    val siteLabel: String?,
    val jobTypeLabel: String?,
    val notes: String?,
)
