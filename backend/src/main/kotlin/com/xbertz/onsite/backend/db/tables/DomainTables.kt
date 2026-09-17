package com.xbertz.onsite.backend.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Companies : Table("companies") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(Accounts.id)
    val name = text("name")
    val abn = text("abn").nullable()
    val phone = text("phone").nullable()
    val email = text("email").nullable()
    val createdAtMillis = long("created_at_millis")
    val hourlyRate = double("hourly_rate").nullable()
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Sites : Table("sites") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(Accounts.id)
    val label = text("label")
    val address = text("address").nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val createdAtMillis = long("created_at_millis")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object JobTypes : Table("job_types") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(Accounts.id)
    val name = text("name")
    val createdAtMillis = long("created_at_millis")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Invoices : Table("invoices") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(Accounts.id)
    val number = text("number")
    val companyName = text("company_name")
    val periodStartEpochDay = long("period_start_epoch_day")
    val periodEndEpochDay = long("period_end_epoch_day")
    val issueDateEpochDay = long("issue_date_epoch_day")
    val totalHours = double("total_hours")
    val totalAmount = double("total_amount").nullable()
    val hourlyRate = double("hourly_rate").nullable()
    val status = text("status")
    val sentAtMillis = long("sent_at_millis").nullable()
    val paidAtMillis = long("paid_at_millis").nullable()
    val pdfPath = text("pdf_path").nullable()
    val notes = text("notes").nullable()
    val createdAtMillis = long("created_at_millis")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    const val STATUS_DRAFT = "DRAFT"
    const val STATUS_SENT = "SENT"
    const val STATUS_PAID = "PAID"
    const val STATUS_VOID = "VOID"
}

object TrackingSessions : Table("tracking_sessions") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(Accounts.id)
    val companyName = text("company_name").nullable()
    val siteLabel = text("site_label").nullable()
    val jobTypeLabel = text("job_type_label").nullable()
    val startTimestampMillis = long("start_timestamp_millis")
    val startLatitude = double("start_latitude")
    val startLongitude = double("start_longitude")
    val stopTimestampMillis = long("stop_timestamp_millis").nullable()
    val stopLatitude = double("stop_latitude").nullable()
    val stopLongitude = double("stop_longitude").nullable()
    val hourlyRate = double("hourly_rate").nullable()
    val invoiceId = uuid("invoice_id").references(Invoices.id).nullable()
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object PlannedJobs : Table("planned_jobs") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(Accounts.id)
    val dateEpochDay = long("date_epoch_day")
    val startMinute = integer("start_minute")
    val endMinute = integer("end_minute").nullable()
    val companyName = text("company_name").nullable()
    val siteLabel = text("site_label").nullable()
    val jobTypeLabel = text("job_type_label").nullable()
    val notes = text("notes").nullable()
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
