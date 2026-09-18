package com.xbertz.onsite

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.Company
import com.xbertz.onsite.data.JobType
import com.xbertz.onsite.data.Site
import com.xbertz.onsite.data.TrackingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class TrackerUiState(
    val isSessionActive: Boolean = false,
    val activeSessionId: Long? = null,
    val selectedCompany: Company? = null,
    val selectedSite: Site? = null,
    val selectedJobType: JobType? = null,
    val isProcessing: Boolean = false,
    @StringRes val locationErrorRes: Int? = null,
    val lastCompletedSession: TrackingSession? = null
)

val TrackingSession.durationMillis: Long?
    get() = stopTimestampMillis?.let { it - startTimestampMillis }

/** A company + site + job type triple, the three choices every tracked session needs. */
data class SessionCombo(val company: Company, val site: Site, val jobType: JobType)

private const val MAX_RECENT_COMBOS = 4

private suspend fun fetchCurrentLocation(context: Context): Location? {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()
    return fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationTokenSource.token
    ).await()
}

class LocationTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).trackingSessionDao()
    private val companyDao = AppDatabase.getInstance(application).companyDao()
    private val siteDao = AppDatabase.getInstance(application).siteDao()
    private val jobTypeDao = AppDatabase.getInstance(application).jobTypeDao()

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState

    val companies: StateFlow<List<Company>> = companyDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sites: StateFlow<List<Site>> = siteDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val jobTypes: StateFlow<List<JobType>> = jobTypeDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allSessions: StateFlow<List<TrackingSession>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedSessions: StateFlow<List<TrackingSession>> = allSessions
        .map { list -> list.filter { it.stopTimestampMillis != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The running session, if any; the home screen shows it with a live chronometer. */
    val activeSession: StateFlow<TrackingSession?> = dao.getActiveSessionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * The most recently used company/site/job type combinations, newest first, resolved against
     * the current registers so a deleted record drops out. Shown as one-tap chips on the tracker.
     */
    val recentCombos: StateFlow<List<SessionCombo>> = combine(completedSessions, companies, sites, jobTypes) { sessions, cs, ss, js ->
        sessions.asSequence()
            .map { Triple(it.companyName, it.siteLabel, it.jobTypeLabel) }
            .distinct()
            .mapNotNull { (c, s, j) ->
                val company = cs.firstOrNull { it.name == c } ?: return@mapNotNull null
                val site = ss.firstOrNull { it.label == s } ?: return@mapNotNull null
                val jobType = js.firstOrNull { it.name == j } ?: return@mapNotNull null
                SessionCombo(company, site, jobType)
            }
            .take(MAX_RECENT_COMBOS)
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Most recent completed session, used for the one-tap "resume" on the home screen. */
    val lastSession: StateFlow<TrackingSession?> = completedSessions
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val active = dao.getActiveSession()
            _uiState.update { it.copy(isSessionActive = active != null, activeSessionId = active?.id) }
            // Re-post the chronometer notification in case the process was killed while tracking.
            if (active != null) SessionNotification.show(getApplication(), active)
        }
    }

    /**
     * Starts a new session with the same company/site/job type as [lastSession]. Names are resolved
     * against the current registers, so a deleted record simply prevents the resume.
     */
    fun resumeLastSession() {
        val last = lastSession.value ?: return
        if (_uiState.value.isSessionActive) return
        viewModelScope.launch {
            val company = companyDao.getAll().first().firstOrNull { it.name == last.companyName } ?: return@launch
            val site = siteDao.getAll().first().firstOrNull { it.label == last.siteLabel } ?: return@launch
            val jobType = jobTypeDao.getAll().first().firstOrNull { it.name == last.jobTypeLabel } ?: return@launch
            _uiState.update { it.copy(selectedCompany = company, selectedSite = site, selectedJobType = jobType) }
            startTracking()
        }
    }

    fun selectCompany(company: Company) {
        _uiState.update { it.copy(selectedCompany = company) }
    }

    fun selectSite(site: Site) {
        _uiState.update { it.copy(selectedSite = site) }
    }

    fun selectJobType(jobType: JobType) {
        _uiState.update { it.copy(selectedJobType = jobType) }
    }

    fun selectCombo(combo: SessionCombo) {
        _uiState.update { it.copy(selectedCompany = combo.company, selectedSite = combo.site, selectedJobType = combo.jobType) }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val state = _uiState.value
        if (state.isSessionActive) return
        val site = state.selectedSite ?: return
        val company = state.selectedCompany ?: return
        val jobType = state.selectedJobType ?: return

        // Flip synchronously so a rapid double-tap can't launch a second coroutine.
        _uiState.update { it.copy(isSessionActive = true, isProcessing = true, locationErrorRes = null) }

        viewModelScope.launch {
            val location = fetchCurrentLocation(getApplication())
            if (location == null) {
                _uiState.update {
                    it.copy(
                        isSessionActive = false,
                        isProcessing = false,
                        locationErrorRes = R.string.location_error
                    )
                }
                return@launch
            }

            val session = TrackingSession(
                companyName = company.name,
                siteLabel = site.label,
                jobTypeLabel = jobType.name,
                startTimestampMillis = System.currentTimeMillis(),
                startLatitude = location.latitude,
                startLongitude = location.longitude,
                stopTimestampMillis = null,
                stopLatitude = null,
                stopLongitude = null
            )
            val id = dao.insert(session)
            SessionNotification.show(getApplication(), session.copy(id = id))
            _uiState.update { it.copy(activeSessionId = id, isProcessing = false) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopTracking() {
        val state = _uiState.value
        if (!state.isSessionActive) return

        // Flip synchronously so a rapid double-tap can't launch a second coroutine.
        _uiState.update { it.copy(isSessionActive = false, isProcessing = true, locationErrorRes = null) }

        viewModelScope.launch {
            val activeSession = state.activeSessionId?.let { dao.getById(it) } ?: dao.getActiveSession()
            if (activeSession == null) {
                _uiState.update { it.copy(isProcessing = false) }
                return@launch
            }

            val location = fetchCurrentLocation(getApplication())
            if (location == null) {
                _uiState.update {
                    it.copy(
                        isSessionActive = true,
                        isProcessing = false,
                        locationErrorRes = R.string.location_error
                    )
                }
                return@launch
            }

            val updated = activeSession.copy(
                stopTimestampMillis = System.currentTimeMillis(),
                stopLatitude = location.latitude,
                stopLongitude = location.longitude
            )
            dao.update(updated)
            SessionNotification.cancel(getApplication())
            _uiState.update {
                it.copy(activeSessionId = null, isProcessing = false, lastCompletedSession = updated)
            }
        }
    }

    fun dismissSessionSummary() {
        _uiState.update { it.copy(lastCompletedSession = null) }
    }

    /** Saves a session typed in by hand. Both ends use the site's registered coordinates, since no GPS fix was taken. */
    fun addManualSession(
        company: Company,
        site: Site,
        jobType: JobType,
        startMillis: Long,
        stopMillis: Long,
        hourlyRate: Double? = null
    ) {
        if (stopMillis <= startMillis) return
        viewModelScope.launch {
            dao.insert(
                TrackingSession(
                    companyName = company.name,
                    siteLabel = site.label,
                    jobTypeLabel = jobType.name,
                    startTimestampMillis = startMillis,
                    startLatitude = site.latitude,
                    startLongitude = site.longitude,
                    stopTimestampMillis = stopMillis,
                    stopLatitude = site.latitude,
                    stopLongitude = site.longitude,
                    hourlyRate = hourlyRate
                )
            )
        }
    }

    /** Edits a completed session: new duration (end = start + duration) and its optional rate override. */
    fun updateSession(session: TrackingSession, newDurationMillis: Long, hourlyRate: Double?) {
        viewModelScope.launch {
            dao.update(
                session.copy(
                    stopTimestampMillis = session.startTimestampMillis + newDurationMillis,
                    hourlyRate = hourlyRate
                )
            )
        }
    }

    fun deleteSession(session: TrackingSession) {
        viewModelScope.launch { dao.delete(session) }
    }
}
