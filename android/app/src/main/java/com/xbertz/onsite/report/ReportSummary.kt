package com.xbertz.onsite.report

import com.xbertz.onsite.data.Site
import com.xbertz.onsite.data.TrackingSession
import com.xbertz.onsite.durationMillis
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Quick period presets on the Reports screen; CUSTOM keeps whatever dates the user picked. */
enum class ReportPeriod {
    THIS_WEEK, FORTNIGHT, THIS_MONTH, LAST_MONTH, CUSTOM;

    /** Inclusive date range for the preset; CUSTOM returns null (the caller keeps its own dates). */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate>? = when (this) {
        THIS_WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday to monday.plusDays(6)
        }
        // This week plus the one before it: a Monday-aligned 14-day window, like a fortnightly pay period.
        FORTNIGHT -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday.minusWeeks(1) to monday.plusDays(6)
        }
        THIS_MONTH -> YearMonth.from(today).let { it.atDay(1) to it.atEndOfMonth() }
        LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        CUSTOM -> null
    }
}

/** Headline numbers for a set of sessions; amounts are null when no session has a usable rate. */
data class ReportSummary(
    val totalMillis: Long,
    val workedDays: Int,
    val estimatedAmount: Double?,
    val unbilledMillis: Long,
    val unbilledAmount: Double?
)

/** One bar of the weekly chart: the Monday that starts the week and the hours tracked in it. */
data class WeekTotal(val weekStart: LocalDate, val millis: Long)

/** One row of an "hours by X" breakdown: a label (site, client or service) and the hours behind it. */
data class LabeledTotal(val label: String, val millis: Long)

private fun TrackingSession.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(startTimestampMillis).atZone(zone).toLocalDate()

/** Rate that applies to a session: its own override, else the company default, else none. */
fun TrackingSession.effectiveRate(ratesByCompany: Map<String, Double?>): Double? =
    hourlyRate ?: companyName?.let { ratesByCompany[it] }

private fun amountOf(sessions: List<TrackingSession>, ratesByCompany: Map<String, Double?>): Double? {
    val priced = sessions.mapNotNull { s -> s.effectiveRate(ratesByCompany)?.let { rate -> (s.durationMillis ?: 0L) / 3_600_000.0 * rate } }
    if (priced.isEmpty()) return null
    return Math.round(priced.sum() * 100) / 100.0
}

fun summarize(
    sessions: List<TrackingSession>,
    ratesByCompany: Map<String, Double?>,
    zone: ZoneId = ZoneId.systemDefault()
): ReportSummary {
    val completed = sessions.filter { it.stopTimestampMillis != null }
    val unbilled = completed.filter { it.invoiceId == null }
    return ReportSummary(
        totalMillis = completed.sumOf { it.durationMillis ?: 0L },
        workedDays = completed.map { it.localDate(zone) }.distinct().size,
        estimatedAmount = amountOf(completed, ratesByCompany),
        unbilledMillis = unbilled.sumOf { it.durationMillis ?: 0L },
        unbilledAmount = amountOf(unbilled, ratesByCompany)
    )
}

/** Hours per Monday-based week covering [start]..[end], including empty weeks so the chart has no gaps. */
fun weeklyTotals(
    sessions: List<TrackingSession>,
    start: LocalDate,
    end: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<WeekTotal> {
    val byWeek = sessions
        .filter { it.stopTimestampMillis != null }
        .groupBy { it.localDate(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .mapValues { (_, list) -> list.sumOf { it.durationMillis ?: 0L } }
    val firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return generateSequence(firstMonday) { it.plusWeeks(1) }
        .takeWhile { it <= end }
        .map { monday -> WeekTotal(monday, byWeek[monday] ?: 0L) }
        .toList()
}

/** Hours grouped by [keyOf], biggest first; sessions with a blank key are grouped under an empty label. */
private fun totalsBy(sessions: List<TrackingSession>, keyOf: (TrackingSession) -> String?): List<LabeledTotal> =
    sessions
        .filter { it.stopTimestampMillis != null }
        .groupBy { keyOf(it).orEmpty() }
        .map { (label, list) -> LabeledTotal(label, list.sumOf { it.durationMillis ?: 0L }) }
        .sortedByDescending { it.millis }

/** Hours per site, biggest first; sessions without a site label are grouped under an empty name. */
fun totalsBySite(sessions: List<TrackingSession>): List<LabeledTotal> = totalsBy(sessions) { it.siteLabel }

/** Hours per client, biggest first. */
fun totalsByCompany(sessions: List<TrackingSession>): List<LabeledTotal> = totalsBy(sessions) { it.companyName }

/** Hours per service, biggest first. */
fun totalsByJobType(sessions: List<TrackingSession>): List<LabeledTotal> = totalsBy(sessions) { it.jobTypeLabel }

/**
 * CSV with the same columns as the on-screen table (one row per session), plus the rate and
 * amount when known. Labels come from the caller so they follow the app language.
 */
fun buildCsv(
    sessions: List<TrackingSession>,
    columns: List<ReportColumn>,
    columnLabels: Map<ReportColumn, String>,
    rateLabel: String,
    amountLabel: String,
    sitesByLabel: Map<String, Site>,
    ratesByCompany: Map<String, Double?>,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    fun cell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    val header = columns.map { columnLabels.getValue(it) } + listOf(rateLabel, amountLabel)
    val rows = sessions.filter { it.stopTimestampMillis != null }.sortedBy { it.startTimestampMillis }.map { session ->
        val start = Instant.ofEpochMilli(session.startTimestampMillis).atZone(zone)
        val stop = Instant.ofEpochMilli(session.stopTimestampMillis!!).atZone(zone)
        val hours = (session.durationMillis ?: 0L) / 3_600_000.0
        val rate = session.effectiveRate(ratesByCompany)
        val values = columns.map { column ->
            when (column) {
                ReportColumn.DATE -> String.format(java.util.Locale.ENGLISH, "%04d-%02d-%02d", start.year, start.monthValue, start.dayOfMonth)
                ReportColumn.SITE -> session.siteLabel.orEmpty()
                ReportColumn.ADDRESS -> sitesByLabel[session.siteLabel]?.address.orEmpty()
                ReportColumn.COMPANY -> session.companyName.orEmpty()
                ReportColumn.JOB_TYPE -> session.jobTypeLabel.orEmpty()
                ReportColumn.START_TIME -> String.format(java.util.Locale.ENGLISH, "%02d:%02d", start.hour, start.minute)
                ReportColumn.END_TIME -> String.format(java.util.Locale.ENGLISH, "%02d:%02d", stop.hour, stop.minute)
                ReportColumn.HOURS -> String.format(java.util.Locale.ENGLISH, "%.2f", hours)
            }
        } + listOf(
            rate?.let { String.format(java.util.Locale.ENGLISH, "%.2f", it) }.orEmpty(),
            rate?.let { String.format(java.util.Locale.ENGLISH, "%.2f", hours * it) }.orEmpty()
        )
        values.joinToString(",") { cell(it) }
    }
    return (listOf(header.joinToString(",") { cell(it) }) + rows).joinToString("\r\n") + "\r\n"
}
