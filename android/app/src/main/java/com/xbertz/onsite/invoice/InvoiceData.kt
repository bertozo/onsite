package com.xbertz.onsite.invoice

import com.xbertz.onsite.data.Company
import com.xbertz.onsite.data.Profile
import com.xbertz.onsite.data.Site
import com.xbertz.onsite.data.TrackingSession
import com.xbertz.onsite.durationMillis
import com.xbertz.onsite.report.ReportColumn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One invoice line = one worked day at one rate (all sessions of that day merged). A day whose
 * sessions carry different rate overrides becomes one line per rate.
 */
data class InvoiceLine(
    val date: LocalDate,
    val sites: List<String>,
    val addresses: List<String>,
    val companies: List<String>,
    val jobTypes: List<String>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val durationMillis: Long,
    /** Effective rate for this line (session override or the invoice default); null = hours only. */
    val hourlyRate: Double? = null,
    /** The sessions merged into this line, so the invoice can link them once generated. */
    val sessionIds: List<Long> = emptyList()
) {
    /** Hours rounded to 2 decimals, as printed on the invoice and used for amounts. */
    val hours: Double
        get() = Math.round(durationMillis / 3_600_000.0 * 100) / 100.0

    /** Line amount at [hourlyRate], rounded to cents; null when the line has no rate. */
    val amount: Double?
        get() = hourlyRate?.let { rate -> Math.round(hours * rate * 100) / 100.0 }

    /** Text for one of the template columns; the PDF and the on-screen table share this. */
    fun valueFor(column: ReportColumn): String = when (column) {
        ReportColumn.DATE -> date.format(DATE_FORMAT)
        ReportColumn.SITE -> sites.joinToString(", ")
        ReportColumn.ADDRESS -> addresses.joinToString(" | ")
        ReportColumn.COMPANY -> companies.joinToString(", ")
        ReportColumn.JOB_TYPE -> jobTypes.joinToString(", ")
        ReportColumn.START_TIME -> startTime.format(TIME_FORMAT)
        ReportColumn.END_TIME -> endTime.format(TIME_FORMAT)
        ReportColumn.HOURS -> String.format(Locale.ENGLISH, "%.2f", hours)
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    }
}

/** Everything the PDF template needs; built by [InvoiceViewModel], rendered by [InvoicePdfTemplate]. */
data class InvoiceData(
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val provider: Profile,
    val client: Company,
    val lines: List<InvoiceLine>,
    /** Which template columns to print, in template order (see [ReportColumn.ordered]). */
    val columns: List<ReportColumn>,
    /** Default hourly rate applied to lines without an override; null lists hours only. */
    val hourlyRate: Double?,
    /** Free text printed above the payment details, e.g. a PO number or a thank-you note. */
    val notes: String? = null
) {
    val totalHours: Double get() = Math.round(lines.sumOf { it.hours } * 100) / 100.0

    /** True when at least one line carries a rate, so Rate/Amount columns and a total due are printed. */
    val showAmounts: Boolean get() = lines.any { it.hourlyRate != null }

    /** Sum of the line amounts, or null for an hours-only invoice. */
    val totalAmount: Double? get() = if (showAmounts) Math.round(lines.sumOf { it.amount ?: 0.0 } * 100) / 100.0 else null

    /** True when every line uses the same rate, so a single "Hourly rate" summary line makes sense. */
    val hasUniformRate: Boolean get() = showAmounts && lines.map { it.hourlyRate }.distinct().size == 1
}

/**
 * Groups completed sessions into one [InvoiceLine] per calendar day (and rate), oldest first.
 * Sessions only store the site label, so [sitesByLabel] supplies the address column.
 * [defaultRate] applies to sessions without their own [TrackingSession.hourlyRate].
 */
fun buildInvoiceLines(
    sessions: List<TrackingSession>,
    sitesByLabel: Map<String, Site> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault(),
    defaultRate: Double? = null
): List<InvoiceLine> {
    return sessions
        .filter { it.stopTimestampMillis != null }
        .groupBy { Instant.ofEpochMilli(it.startTimestampMillis).atZone(zone).toLocalDate() to (it.hourlyRate ?: defaultRate) }
        .entries
        .sortedWith(compareBy({ it.key.first }, { it.key.second ?: -1.0 }))
        .map { (key, daySessions) ->
            val (date, rate) = key
            val sorted = daySessions.sortedBy { it.startTimestampMillis }
            val sites = sorted.mapNotNull { it.siteLabel?.takeIf { s -> s.isNotBlank() } }.distinct()
            InvoiceLine(
                date = date,
                sites = sites,
                addresses = sites.mapNotNull { label -> sitesByLabel[label]?.address?.takeIf { it.isNotBlank() } }.distinct(),
                companies = sorted.mapNotNull { it.companyName?.takeIf { s -> s.isNotBlank() } }.distinct(),
                jobTypes = sorted.mapNotNull { it.jobTypeLabel?.takeIf { s -> s.isNotBlank() } }.distinct(),
                startTime = Instant.ofEpochMilli(sorted.first().startTimestampMillis).atZone(zone).toLocalTime(),
                endTime = Instant.ofEpochMilli(sorted.last().stopTimestampMillis!!).atZone(zone).toLocalTime(),
                durationMillis = sorted.sumOf { it.durationMillis ?: 0L },
                hourlyRate = rate,
                sessionIds = sorted.map { it.id }
            )
        }
}
