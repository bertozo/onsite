package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.Company
import com.xbertz.onsite.data.JobType
import com.xbertz.onsite.data.PlannedJob
import com.xbertz.onsite.data.Site
import com.xbertz.onsite.data.TrackingSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Planning screen: the calendar plus the jobs scheduled for the selected day. */
data class PlanningUiState(
    val calendar: CalendarUiState,
    /** Jobs of the selected day, by start time. */
    val dayJobs: List<PlannedJob> = emptyList(),
    /** Completed sessions of the selected day, oldest first. */
    val daySessions: List<TrackingSession> = emptyList()
) {
    val dayTotalMillis: Long get() = daySessions.sumOf { it.durationMillis ?: 0L }
}

class PlanningViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.plannedJobDao()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val navigator = CalendarNavigator(zone)

    val uiState: StateFlow<PlanningUiState> = combine(
        navigator.state, dao.getAll(), db.trackingSessionDao().getAll()
    ) { nav, jobs, sessions ->
        val jobsByDay = jobs.groupBy { it.date }
        val sessionsOfDay = sessions
            .filter { it.stopTimestampMillis != null }
            .filter { Instant.ofEpochMilli(it.startTimestampMillis).atZone(zone).toLocalDate() == nav.selectedDate }
            .sortedBy { it.startTimestampMillis }
        PlanningUiState(
            calendar = nav.copy(markedDays = jobsByDay.keys),
            dayJobs = jobsByDay[nav.selectedDate].orEmpty(),
            daySessions = sessionsOfDay
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanningUiState(navigator.state.value))

    // Pick lists for the job dialog; the tracker's ViewModel exposes the same flows but is scoped to its own screen.
    val companies: StateFlow<List<Company>> = db.companyDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sites: StateFlow<List<Site>> = db.siteDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val jobTypes: StateFlow<List<JobType>> = db.jobTypeDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: LocalDate) = navigator.selectDate(date)
    fun toggleExpanded() = navigator.toggleExpanded()
    fun shift(forward: Boolean) = navigator.shift(forward)
    fun goToToday() = navigator.goToToday()

    /** Inserts when [job.id] is 0, updates otherwise; the dialog builds the row either way. */
    fun saveJob(job: PlannedJob) {
        viewModelScope.launch {
            if (job.id == 0L) dao.insert(job) else dao.update(job)
        }
    }

    fun deleteJob(job: PlannedJob) {
        viewModelScope.launch { dao.delete(job) }
    }
}
