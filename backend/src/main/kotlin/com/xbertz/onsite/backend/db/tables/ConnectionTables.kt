package com.xbertz.onsite.backend.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ConnectionInvites : Table("connection_invites") {
    val id = uuid("id")
    val employerAccountId = uuid("employer_account_id").references(Accounts.id)
    val email = text("email")
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val acceptedAt = timestamp("accepted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_REVOKED = "REVOKED"
}

object Connections : Table("connections") {
    val id = uuid("id")
    val employerAccountId = uuid("employer_account_id").references(Accounts.id)
    val workerAccountId = uuid("worker_account_id").references(Accounts.id)
    val workerUserId = uuid("worker_user_id").references(Users.id)
    val workerCompanyId = uuid("worker_company_id").references(Companies.id)
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)

    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_REVOKED = "REVOKED"
}
