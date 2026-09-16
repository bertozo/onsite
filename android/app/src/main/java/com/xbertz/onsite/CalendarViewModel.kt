package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.TrackingSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Home-screen calendar: week/month navigation plus the completed sessions of the selected day. */
data class HomeCalendarUiState(
    val calendar: CalendarUiState,
    /** Completed sessions of the selected day, oldest first. */
    val daySessions: List<TrackingSession> = emptyList()
) {
    val dayTotalMillis: Long get() = daySessions.sumOf { it.durationMillis ?: 0L }
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionDao = AppDatabase.getInstance(application).trackingSessionDao()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val navigator = CalendarNavigator(zone)

    val uiState: StateFlow<HomeCalendarUiState> = combine(navigator.state, sessionDao.getAll()) { nav, sessions ->
        val byDay = sessions
            .filter { it.stopTimestampMillis != null }
            .groupBy { Instant.ofEpochMilli(it.startTimestampMillis).atZone(zone).toLocalDate() }
        HomeCalendarUiState(
            calendar = nav.copy(markedDays = byDay.keys),
            daySessions = byDay[nav.selectedDate].orEmpty().sortedBy { it.startTimestampMillis }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeCalendarUiState(navigator.state.value))

    fun selectDate(date: LocalDate) = navigator.selectDate(date)
    fun toggleExpanded() = navigator.toggleExpanded()
    fun shift(forward: Boolean) = navigator.shift(forward)
    fun goToToday() = navigator.goToToday()
}
