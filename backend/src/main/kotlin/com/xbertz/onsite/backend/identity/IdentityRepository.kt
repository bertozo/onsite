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
 * Thrown by [IdentityRepository.bootstrap] when [email] already belongs to a different
 * `users.id` than the caller's token carries - see that function's kdoc for why this can
 * happen and why it's reported instead of silently patched over.
 */
class EmailAlreadyRegistered(val email: String) : Exception("email already registered under a different identity: $email")

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
     *
     * Looked up by `userId` (the token's `sub`), not by email, because identity is defined by
     * the token, not the address. That only bites when two different tokens claim the same
     * email under two different ids - in practice, mixing a real Supabase-issued token with
     * the dev-only login's token (each mints its own UUID for the same address) on the same
     * local backend. Rather than let that hit `users_email_key` and bubble up as an opaque
     * 500, it's reported as [EmailAlreadyRegistered] so the caller knows exactly what
     * happened: sign in the same way you did the first time for this email.
     */
    fun bootstrap(userId: UUID, email: String): MeResponse = transaction {
        val now = Instant.now()
        val existingUser = Users.selectAll().where { Users.id eq userId }.singleOrNull()
        if (existingUser == null) {
            val emailTakenByOther = Users.selectAll().where { Users.email eq email }.any()
            if (emailTakenByOther) throw EmailAlreadyRegistered(email)
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

    /** Every member of an account, for the OWNER's "assign to" pickers - never exposed to a WORKER. */
    fun membersOf(accountId: UUID): List<MemberDto> = transaction {
        (Memberships innerJoin Users)
            .select(Users.id, Users.email, Users.displayName, Memberships.role)
            .where { Memberships.accountId eq accountId }
            .map {
                MemberDto(
                    userId = it[Users.id].toString(),
                    email = it[Users.email],
                    displayName = it[Users.displayName],
                    role = it[Memberships.role],
                )
            }
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
