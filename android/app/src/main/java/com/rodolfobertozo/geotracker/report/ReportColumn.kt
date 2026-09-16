package com.rodolfobertozo.geotracker.report

import androidx.annotation.StringRes
import com.rodolfobertozo.geotracker.R

/**
 * The single column template shared by the on-screen report table and the invoice PDF.
 *
 * Enum order is the display order — the user can only turn columns on or off, never
 * reorder them, so every report and invoice keeps the same layout. [DATE] and [HOURS]
 * are always shown.
 */
enum class ReportColumn(@StringRes val labelRes: Int, val alwaysShown: Boolean = false) {
    DATE(R.string.date, alwaysShown = true),
    SITE(R.string.site),
    ADDRESS(R.string.address),
    COMPANY(R.string.company),
    JOB_TYPE(R.string.job_type),
    START_TIME(R.string.start_time),
    END_TIME(R.string.end_time),
    HOURS(R.string.hours, alwaysShown = true);

    companion object {
        val DEFAULT: Set<ReportColumn> = setOf(DATE, SITE, HOURS)

        /** Normalises any selection: forces the always-shown columns in and returns template order. */
        fun ordered(selection: Set<ReportColumn>): List<ReportColumn> =
            entries.filter { it.alwaysShown || it in selection }
    }
}
