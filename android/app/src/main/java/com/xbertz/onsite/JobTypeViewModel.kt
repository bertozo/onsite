package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.JobType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobTypeFormUiState(
    val name: String = ""
)

data class JobTypeEditUiState(
    val original: JobType,
    val name: String
)

class JobTypeViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).jobTypeDao()
    private val sessionDao = AppDatabase.getInstance(application).trackingSessionDao()

    private val _uiState = MutableStateFlow(JobTypeFormUiState())
    val uiState: StateFlow<JobTypeFormUiState> = _uiState

    val jobTypes: StateFlow<List<JobType>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editState = MutableStateFlow<JobTypeEditUiState?>(null)
    val editState: StateFlow<JobTypeEditUiState?> = _editState

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value) }

    fun saveJobType() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            dao.insert(
                JobType(
                    name = state.name.trim(),
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            _uiState.update { JobTypeFormUiState() }
        }
    }

    fun deleteJobType(jobType: JobType) {
        viewModelScope.launch { dao.delete(jobType) }
    }

    fun startEditingJobType(jobType: JobType) {
        _editState.value = JobTypeEditUiState(original = jobType, name = jobType.name)
    }

    fun onEditNameChanged(value: String) = _editState.update { it?.copy(name = value) }

    fun cancelEditingJobType() {
        _editState.value = null
    }

    fun saveEditedJobType() {
        val state = _editState.value ?: return
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val newName = state.name.trim()
            dao.update(state.original.copy(name = newName))
            // Sessions store the job type by name, so keep past sessions pointing at the renamed type.
            if (newName != state.original.name) {
                sessionDao.renameJobType(oldName = state.original.name, newName = newName)
            }
            _editState.value = null
        }
    }
}
