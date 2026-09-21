package com.xbertz.onsite.backend.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Accounts : Table("accounts") {
    // Generated in application code (UUID.randomUUID()) on insert, not left to the
    // database default, so Exposed never has to guess a generated id back out.
    val id = uuid("id")
    val kind = varchar("kind", 16)
    val name = text("name")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    const val KIND_PERSONAL = "PERSONAL"
    const val KIND_BUSINESS = "BUSINESS"
}

object Users : Table("users") {
    // Not auto-generated: this id is the Supabase Auth user id (the JWT "sub" claim).
    val id = uuid("id")
    val email = text("email").uniqueIndex()
    val displayName = text("display_name").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object UserProfiles : Table("user_profiles") {
    val userId = uuid("user_id").references(Users.id)
    val name = text("name")
    val role = text("role").nullable()
    val phone = text("phone").nullable()
    val email = text("email").nullable()
    val abn = text("abn").nullable()
    val bankBsb = text("bank_bsb").nullable()
    val bankAccount = text("bank_account").nullable()
    val updatedAtMillis = long("updated_at_millis")

    override val primaryKey = PrimaryKey(userId)
}

object Memberships : Table("memberships") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val accountId = uuid("account_id").references(Accounts.id)
    val role = varchar("role", 16)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    const val ROLE_OWNER = "OWNER"
    const val ROLE_WORKER = "WORKER"
}

object Invites : Table("invites") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(Accounts.id)
    val email = text("email")
    val role = varchar("role", 16)
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val acceptedAt = timestamp("accepted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_REVOKED = "REVOKED"
}
