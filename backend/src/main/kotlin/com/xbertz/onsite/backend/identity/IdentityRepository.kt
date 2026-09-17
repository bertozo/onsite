package com.xbertz.onsite.backend.identity

import com.xbertz.onsite.backend.db.tables.Accounts
import com.xbertz.onsite.backend.db.tables.Memberships
import com.xbertz.onsite.backend.db.tables.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Everything a client needs to know right after signing in: who it is, and which
 * account(s) it can act as (its own personal account, plus any account it was invited
 * into later, once memberships beyond the personal one exist).
 */
class IdentityRepository {

    /**
     * Idempotent: safe to call on every login. Creates the `users` row and, only the very
     * first time, a personal account with an OWNER membership. Later calls just refresh the
     * denormalized email/display name and return the current state.
     */
    fun bootstrap(userId: UUID, email: String): MeResponse = transaction {
        val now = Instant.now()
        val existingUser = Users.selectAll().where { Users.id eq userId }.singleOrNull()
        if (existingUser == null) {
            Users.insert {
                it[id] = userId
                it[Users.email] = email
                it[displayName] = null
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else if (existingUser[Users.email] != email) {
            Users.update({ Users.id eq userId }) {
                it[Users.email] = email
                it[updatedAt] = now
            }
        }

        val hasMembership = Memberships.selectAll().where { Memberships.userId eq userId }.any()
        if (!hasMembership) {
            val accountId = UUID.randomUUID()
            Accounts.insert {
                it[id] = accountId
                it[kind] = Accounts.KIND_PERSONAL
                it[name] = email
                it[createdAt] = now
                it[updatedAt] = now
            }
            Memberships.insert {
                it[id] = UUID.randomUUID()
                it[Memberships.userId] = userId
                it[Memberships.accountId] = accountId
                it[role] = Memberships.ROLE_OWNER
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        loadMe(userId)
    }

    fun findMe(userId: UUID): MeResponse? = transaction {
        val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@transaction null
        loadMeForUser(user)
    }

    private fun loadMe(userId: UUID): MeResponse {
        val user = Users.selectAll().where { Users.id eq userId }.single()
        return loadMeForUser(user)
    }

    private fun loadMeForUser(user: ResultRow): MeResponse {
        val memberships = (Memberships innerJoin Accounts)
            .select(Memberships.accountId, Memberships.role, Accounts.name, Accounts.kind)
            .where { Memberships.userId eq user[Users.id] }
            .map { row ->
                AccountMembershipDto(
                    accountId = row[Memberships.accountId].toString(),
                    accountName = row[Accounts.name],
                    accountKind = row[Accounts.kind],
                    role = row[Memberships.role],
                )
            }

        return MeResponse(
            userId = user[Users.id].toString(),
            email = user[Users.email],
            displayName = user[Users.displayName],
            memberships = memberships,
        )
    }
}
