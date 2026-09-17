package com.xbertz.onsite.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Lifecycle of an issued invoice; stored as text so the schema stays readable. */
enum class InvoiceStatus { DRAFT, SENT, PAID, VOID }

/**
 * An invoice that was generated as a PDF. Sessions billed by it carry its id in
 * [TrackingSession.invoiceId]; voiding clears that link so the days can be billed again.
 * The client is referenced by name like everywhere else in the app.
 */
@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val companyName: String,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val issueDateEpochDay: Long,
    val totalHours: Double,
    /** Null when the invoice listed hours only, without amounts. */
    val totalAmount: Double?,
    /** Default rate used for days without their own override; null for hours-only invoices. */
    val hourlyRate: Double?,
    val status: String = InvoiceStatus.DRAFT.name,
    val sentAtMillis: Long? = null,
    val paidAtMillis: Long? = null,
    /** Absolute path of the PDF in the app's files directory. */
    val pdfPath: String,
    val notes: String? = null,
    val createdAtMillis: Long
) {
    val periodStart: LocalDate get() = LocalDate.ofEpochDay(periodStartEpochDay)
    val periodEnd: LocalDate get() = LocalDate.ofEpochDay(periodEndEpochDay)
    val issueDate: LocalDate get() = LocalDate.ofEpochDay(issueDateEpochDay)
    val statusEnum: InvoiceStatus get() = InvoiceStatus.entries.firstOrNull { it.name == status } ?: InvoiceStatus.DRAFT
}
