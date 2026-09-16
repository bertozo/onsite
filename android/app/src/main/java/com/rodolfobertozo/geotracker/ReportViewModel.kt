package com.rodolfobertozo.geotracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.rodolfobertozo.geotracker.report.ReportColumn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds the user's column selection for reports and invoices; persisted so every report uses the same layout. */
class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _columns = MutableStateFlow(loadColumns())
    val columns: StateFlow<Set<ReportColumn>> = _columns

    fun setColumnEnabled(column: ReportColumn, enabled: Boolean) {
        if (column.alwaysShown) return
        val updated = if (enabled) _columns.value + column else _columns.value - column
        prefs.edit().putString(KEY_COLUMNS, updated.joinToString(",") { it.name }).apply()
        _columns.value = updated
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
