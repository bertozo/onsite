package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.TrackingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * State for the home-screen calendar: a Mon–Sun week strip that expands to the full month.
 *
 * [weekStart] (always a Monday) drives the collapsed strip and [visibleMonth] the expanded grid;
 * they are kept in sync when toggling so the user never "jumps" when expanding or collapsing.
 */
data class CalendarUiState(
    val selectedDate: LocalDate,
    val weekStart: LocalDate,
    val visibleMonth: YearMonth,
    val expanded: Boolean = false,
    /** Days that have at least one completed session, used for the dot markers. */
    val daysWithSessions: Set<LocalDate> = emptySet(),
    /** Completed sessions of [selectedDate], oldest first. */
    val daySessions: List<TrackingSession> = emptyList()
) {
    val dayTotalMillis: Long get() = daySessions.sumOf { it.durationMillis ?: 0L }

    /** Title month: the month of the selected day when the strip shows it, otherwise the week's majority month. */
    val titleMonth: YearMonth
        get() = if (expanded) visibleMonth
        else if (selectedDate in weekStart..weekStart.plusDays(6)) YearMonth.from(selectedDate)
        else YearMonth.from(weekStart.plusDays(3))
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionDao = AppDatabase.getInstance(application).trackingSessionDao()
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _navigation = MutableStateFlow(initialState())

    val uiState: StateFlow<CalendarUiState> = combine(_navigation, sessionDao.getAll()) { nav, sessions ->
        val byDay = sessions
            .filter { it.stopTimestampMillis != null }
            .groupBy { it.startTimestampMillis.toLocalDate() }
        nav.copy(
            daysWithSessions = byDay.keys,
            daySessions = byDay[nav.selectedDate].orEmpty().sortedBy { it.startTimestampMillis }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _navigation.value)

    fun selectDate(date: LocalDate) {
        _navigation.update {
            it.copy(
                selectedDate = date,
                weekStart = date.mondayOfWeek(),
                visibleMonth = YearMonth.from(date)
            )
        }
    }

    fun toggleExpanded() {
        _navigation.update { state ->
            if (state.expanded) {
                // Collapse onto the selected day's week when it belongs to the visible month, else the month's first week.
                val anchor = state.selectedDate.takeIf { YearMonth.from(it) == state.visibleMonth }
                    ?: state.visibleMonth.atDay(1)
                state.copy(expanded = false, weekStart = anchor.mondayOfWeek())
            } else {
                state.copy(expanded = true, visibleMonth = state.titleMonth)
            }
        }
    }

    /** Moves one week back/forward when collapsed, one month when expanded. */
    fun shift(forward: Boolean) {
        val step = if (forward) 1L else -1L
        _navigation.update { state ->
            if (state.expanded) state.copy(visibleMonth = state.visibleMonth.plusMonths(step))
            else state.copy(weekStart = state.weekStart.plusWeeks(step))
        }
    }

    fun goToToday() = selectDate(LocalDate.now(zone))

    private fun initialState(): CalendarUiState {
        val today = LocalDate.now(zone)
        return CalendarUiState(selectedDate = today, weekStart = today.mondayOfWeek(), visibleMonth = YearMonth.from(today))
    }

    private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private fun LocalDate.mondayOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
