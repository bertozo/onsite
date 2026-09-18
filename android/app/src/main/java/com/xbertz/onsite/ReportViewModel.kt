package com.xbertz.onsite

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import com.xbertz.onsite.report.ReportColumn
import com.xbertz.onsite.report.ReportPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDate

/** How the Reports screen presents the filtered sessions. */
enum class ReportViewMode { LIST, INSIGHTS }

/** Period, filters and column selection of the Reports screen. */
data class ReportFilters(
    val period: ReportPeriod = ReportPeriod.THIS_WEEK,
    /** Dates used when [period] is CUSTOM; kept in sync with the preset otherwise. */
    val customStart: LocalDate = LocalDate.now().minusDays(6),
    val customEnd: LocalDate = LocalDate.now(),
    /** Company name to restrict the report to; null = every company. */
    val company: String? = null,
    val unbilledOnly: Boolean = false,
    val viewMode: ReportViewMode = ReportViewMode.LIST
) {
    /** Inclusive date range the report covers. */
    val range: Pair<LocalDate, LocalDate>
        get() = period.range(LocalDate.now()) ?: (customStart to customEnd)
}

/**
 * Holds the user's column selection for reports and invoices (persisted so every report uses the
 * same layout) and the current report filters (kept in memory so other screens can preset them,
 * e.g. the company sheet opening the report for one client).
 */
class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _columns = MutableStateFlow(loadColumns())
    val columns: StateFlow<Set<ReportColumn>> = _columns

    private val _filters = MutableStateFlow(ReportFilters())
    val filters: StateFlow<ReportFilters> = _filters

    fun setColumnEnabled(column: ReportColumn, enabled: Boolean) {
        if (column.alwaysShown) return
        val updated = if (enabled) _columns.value + column else _columns.value - column
        prefs.edit().putString(KEY_COLUMNS, updated.joinToString(",") { it.name }).apply()
        _columns.value = updated
    }

    fun setPeriod(period: ReportPeriod) {
        _filters.update { current ->
            // Entering CUSTOM starts from the preset's dates so the pickers show something sensible.
            val preset = current.period.range(LocalDate.now())
            if (period == ReportPeriod.CUSTOM && preset != null) {
                current.copy(period = period, customStart = preset.first, customEnd = preset.second)
            } else {
                current.copy(period = period)
            }
        }
    }

    fun setCustomStart(date: LocalDate) {
        _filters.update { it.copy(period = ReportPeriod.CUSTOM, customStart = date, customEnd = maxOf(it.customEnd, date)) }
    }

    fun setCustomEnd(date: LocalDate) {
        _filters.update { it.copy(period = ReportPeriod.CUSTOM, customEnd = date, customStart = minOf(it.customStart, date)) }
    }

    fun setCompany(name: String?) = _filters.update { it.copy(company = name) }

    fun setUnbilledOnly(enabled: Boolean) = _filters.update { it.copy(unbilledOnly = enabled) }

    fun setViewMode(mode: ReportViewMode) = _filters.update { it.copy(viewMode = mode) }

    /** Writes [csv] into the shared cache folder and returns a content URI for the share sheet. */
    fun writeCsv(csv: String, start: LocalDate, end: LocalDate): Uri {
        val application = getApplication<Application>()
        val dir = File(application.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "OnSite_report_${start}_$end.csv")
        file.writeText(csv, Charsets.UTF_8)
        return FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
    }

    private fun loadColumns(): Set<ReportColumn> {
        val stored = prefs.getString(KEY_COLUMNS, null) ?: return ReportColumn.DEFAULT
        val parsed = stored.split(',')
            .mapNotNull { name -> ReportColumn.entries.firstOrNull { it.name == name } }
            .toSet()
        return parsed + ReportColumn.entries.filter { it.alwaysShown }
    }

    private companion object {
        const val KEY_COLUMNS = "report_columns"
    }
}
