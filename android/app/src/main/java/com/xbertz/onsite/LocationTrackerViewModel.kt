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

    init {
        viewModelScope.launch {
            val active = dao.getActiveSession()
            _uiState.update { it.copy(isSessionActive = active != null, activeSessionId = active?.id) }
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

            val id = dao.insert(
                TrackingSession(
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
            )
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
            _uiState.update {
                it.copy(activeSessionId = null, isProcessing = false, lastCompletedSession = updated)
            }
        }
    }

    fun dismissSessionSummary() {
        _uiState.update { it.copy(lastCompletedSession = null) }
    }

    /** Saves a session typed in by hand. Both ends use the site's registered coordinates, since no GPS fix was taken. */
    fun addManualSession(company: Company, site: Site, jobType: JobType, startMillis: Long, stopMillis: Long) {
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
                    stopLongitude = site.longitude
                )
            )
        }
    }

    fun updateSessionDuration(session: TrackingSession, newDurationMillis: Long) {
        viewModelScope.launch {
            dao.update(session.copy(stopTimestampMillis = session.startTimestampMillis + newDurationMillis))
        }
    }

    fun deleteSession(session: TrackingSession) {
        viewModelScope.launch { dao.delete(session) }
    }
}
