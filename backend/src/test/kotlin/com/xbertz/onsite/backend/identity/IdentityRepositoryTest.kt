package com.xbertz.onsite.backend.identity

import com.xbertz.onsite.backend.config.AppConfig
import com.xbertz.onsite.backend.db.DatabaseFactory
import com.xbertz.onsite.backend.db.tables.Accounts
import com.xbertz.onsite.backend.db.tables.Memberships
import com.xbertz.onsite.backend.db.tables.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * Exercises IdentityRepository against a real Postgres - the same local instance
 * `docker compose up -d` starts (see README.md) - since bootstrap's job is transactional
 * DB behaviour that isn't worth mocking. Every test uses its own randomly-generated email
 * so runs never collide with real data or each other, and cleans its own rows up after.
 *
 * Reads DB_URL/DB_USER/DB_PASSWORD from the environment when set (CI can point this at a
 * throwaway database) and otherwise falls back to the docker-compose defaults every other
 * script in this repo assumes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityRepositoryTest {
    private val repository = IdentityRepository()
    private val createdUserIds = mutableListOf<UUID>()

    @BeforeAll
    fun connect() {
        DatabaseFactory.connect(
            AppConfig(
                dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/onsite",
                dbUser = System.getenv("DB_USER") ?: "onsite",
                dbPassword = System.getenv("DB_PASSWORD") ?: "onsite_dev_only",
                supabaseJwtSecret = "test-secret",
                supabaseJwtIssuer = "test",
                supabaseProjectUrl = "https://example.supabase.co",
                port = 8080,
                devAuthEnabled = false,
            )
        )
    }

    @AfterEach
    fun cleanup() {
        if (createdUserIds.isEmpty()) return
        transaction {
            val accountIds = Memberships.selectAll().where { Memberships.userId inList createdUserIds }.map { it[Memberships.accountId] }
            Memberships.deleteWhere { Memberships.userId inList createdUserIds }
            if (accountIds.isNotEmpty()) Accounts.deleteWhere { Accounts.id inList accountIds }
            Users.deleteWhere { Users.id inList createdUserIds }
        }
        createdUserIds.clear()
    }

    private fun uniqueEmail() = "identity-repo-test-${UUID.randomUUID()}@example.com"

    @Test
    fun `bootstrap is idempotent for the same user id`() {
        val userId = UUID.randomUUID()
        createdUserIds += userId
        val email = uniqueEmail()

        val first = repository.bootstrap(userId, email)
        val second = repository.bootstrap(userId, email)

        assertEquals(first.memberships.single().accountId, second.memberships.single().accountId)
        assertEquals(1, second.memberships.size)

        // A third call must not create a second personal account for the same user.
        val third = repository.bootstrap(userId, email)
        assertEquals(1, third.memberships.size)
    }

    @Test
    fun `bootstrap refreshes a changed email without touching membership`() {
        val userId = UUID.randomUUID()
        createdUserIds += userId
        val first = repository.bootstrap(userId, uniqueEmail())
        val accountId = first.memberships.single().accountId

        val newEmail = uniqueEmail()
        val updated = repository.bootstrap(userId, newEmail)

        assertEquals(newEmail, updated.email)
        assertEquals(accountId, updated.memberships.single().accountId)
    }

    @Test
    fun `bootstrap on the same email under a different user id fails clearly instead of a raw constraint violation`() {
        val email = uniqueEmail()
        val firstUserId = UUID.randomUUID()
        createdUserIds += firstUserId
        repository.bootstrap(firstUserId, email)

        // Reproduces mixing a real Supabase-issued token with the dev-only login's token for
        // the same address: each mints its own UUID, so the second bootstrap call sees a
        // "new" user id whose email is already taken.
        val secondUserId = UUID.randomUUID()
        val error = assertThrows(EmailAlreadyRegistered::class.java) {
            repository.bootstrap(secondUserId, email)
        }
        assertEquals(email, error.email)

        // Must have failed before inserting anything for the second id.
        val secondUserRow = transaction { Users.selectAll().where { Users.id eq secondUserId }.singleOrNull() }
        assertEquals(null, secondUserRow)
    }

    @Test
    fun `profile save is last-write-wins on the client's edit time`() {
        val userId = UUID.randomUUID()
        createdUserIds += userId
        repository.bootstrap(userId, uniqueEmail())
        assertEquals(null, repository.findProfile(userId))

        val phone = ProfileDto(name = "From phone", abn = "11111111111", updatedAtMillis = 2_000)
        assertEquals(phone, repository.saveProfile(userId, phone))

        // An older edit arriving later (a device that synced late) must not clobber the newer one,
        // and the caller gets the winning version back.
        val staleWeb = ProfileDto(name = "From web", updatedAtMillis = 1_000)
        assertEquals(phone, repository.saveProfile(userId, staleWeb))
        assertEquals("From phone", repository.findProfile(userId)?.name)

        val newerWeb = ProfileDto(name = "From web again", bankBsb = "062-000", updatedAtMillis = 3_000)
        assertEquals(newerWeb, repository.saveProfile(userId, newerWeb))
        assertEquals(newerWeb, repository.findProfile(userId))
    }
}
