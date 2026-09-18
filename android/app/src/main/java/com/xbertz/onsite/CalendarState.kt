package com.xbertz.onsite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Navigation state of a calendar panel: a Mon–Sun week strip that expands to the full month.
 *
 * [weekStart] (always a Monday) drives the collapsed strip and [visibleMonth] the expanded grid;
 * they are kept in sync when toggling so the user never "jumps" when expanding or collapsing.
 */
data class CalendarUiState(
    val selectedDate: LocalDate,
    val weekStart: LocalDate,
    val visibleMonth: YearMonth,
    val expanded: Boolean = false,
    /** Days to draw a dot marker under (sessions on the home screen, planned jobs on the planning screen). */
    val markedDays: Set<LocalDate> = emptySet(),
    /** Tracked milliseconds per day, drawn as a heatmap on past days. */
    val dayHours: Map<LocalDate, Long> = emptyMap()
) {
    /** Title month: the month of the selected day when the strip shows it, otherwise the week's majority month. */
    val titleMonth: YearMonth
        get() = if (expanded) visibleMonth
        else if (selectedDate in weekStart..weekStart.plusDays(6)) YearMonth.from(selectedDate)
        else YearMonth.from(weekStart.plusDays(3))
}

/** Owns a [CalendarUiState] and the navigation rules; ViewModels combine [state] with their own data. */
class CalendarNavigator(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<CalendarUiState> = _state

    fun selectDate(date: LocalDate) {
        _state.update {
            it.copy(selectedDate = date, weekStart = date.mondayOfWeek(), visibleMonth = YearMonth.from(date))
        }
    }

    fun toggleExpanded() {
        _state.update { state ->
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
        _state.update { state ->
            if (state.expanded) state.copy(visibleMonth = state.visibleMonth.plusMonths(step))
            else state.copy(weekStart = state.weekStart.plusWeeks(step))
        }
    }

    fun goToToday() = selectDate(LocalDate.now(zone))

    private fun initialState(): CalendarUiState {
        val today = LocalDate.now(zone)
        return CalendarUiState(selectedDate = today, weekStart = today.mondayOfWeek(), visibleMonth = YearMonth.from(today))
    }

    private fun LocalDate.mondayOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
