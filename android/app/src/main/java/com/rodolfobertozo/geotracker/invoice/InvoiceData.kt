package com.rodolfobertozo.geotracker.invoice

import com.rodolfobertozo.geotracker.data.Company
import com.rodolfobertozo.geotracker.data.Profile
import com.rodolfobertozo.geotracker.data.Site
import com.rodolfobertozo.geotracker.data.TrackingSession
import com.rodolfobertozo.geotracker.durationMillis
import com.rodolfobertozo.geotracker.report.ReportColumn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One invoice line = one worked day (all sessions of that day merged). */
data class InvoiceLine(
    val date: LocalDate,
    val sites: List<String>,
    val addresses: List<String>,
    val companies: List<String>,
    val jobTypes: List<String>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val durationMillis: Long
) {
    /** Hours rounded to 2 decimals, as printed on the invoice and used for amounts. */
    val hours: Double
        get() = Math.round(durationMillis / 3_600_000.0 * 100) / 100.0

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
    /** Optional hourly rate; when null the invoice lists hours only, without amounts. */
    val hourlyRate: Double?
) {
    val totalHours: Double get() = Math.round(lines.sumOf { it.hours } * 100) / 100.0
    val totalAmount: Double? get() = hourlyRate?.let { rate -> Math.round(totalHours * rate * 100) / 100.0 }
}

/**
 * Groups completed sessions into one [InvoiceLine] per calendar day, oldest first.
 * Sessions only store the site label, so [sitesByLabel] supplies the address column.
 */
fun buildInvoiceLines(
    sessions: List<TrackingSession>,
    sitesByLabel: Map<String, Site> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault()
): List<InvoiceLine> {
    return sessions
        .filter { it.stopTimestampMillis != null }
        .groupBy { Instant.ofEpochMilli(it.startTimestampMillis).atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, daySessions) ->
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
                durationMillis = sorted.sumOf { it.durationMillis ?: 0L }
            )
        }
}
