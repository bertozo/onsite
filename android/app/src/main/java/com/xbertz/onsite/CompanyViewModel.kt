package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.Company
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanyFormUiState(
    val name: String = "",
    val abn: String = "",
    val phone: String = "",
    val email: String = "",
    /** Default hourly rate as typed; blank means "not set". */
    val hourlyRate: String = ""
)

data class CompanyEditUiState(
    val original: Company,
    val name: String,
    val abn: String,
    val phone: String,
    val email: String,
    val hourlyRate: String
)

class CompanyViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).companyDao()
    private val sessionDao = AppDatabase.getInstance(application).trackingSessionDao()
    private val plannedJobDao = AppDatabase.getInstance(application).plannedJobDao()
    private val invoiceDao = AppDatabase.getInstance(application).invoiceDao()

    private val _uiState = MutableStateFlow(CompanyFormUiState())
    val uiState: StateFlow<CompanyFormUiState> = _uiState

    val companies: StateFlow<List<Company>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editState = MutableStateFlow<CompanyEditUiState?>(null)
    val editState: StateFlow<CompanyEditUiState?> = _editState

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value) }
    fun onAbnChanged(value: String) = _uiState.update { it.copy(abn = value) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value) }
    fun onHourlyRateChanged(value: String) = _uiState.update { it.copy(hourlyRate = value) }

    fun saveCompany() {
        val state = _uiState.value
        if (state.name.isBlank() || !isCompanyInputValid(state.abn, state.phone, state.email, state.hourlyRate)) return

        viewModelScope.launch {
            dao.insert(
                Company(
                    name = state.name.trim(),
                    abn = state.abn.trim().ifBlank { null },
                    phone = state.phone.trim().ifBlank { null },
                    email = state.email.trim().ifBlank { null },
                    createdAtMillis = System.currentTimeMillis(),
                    hourlyRate = Validators.parseAmount(state.hourlyRate)
                )
            )
            _uiState.update { CompanyFormUiState() }
        }
    }

    fun deleteCompany(company: Company) {
        viewModelScope.launch { dao.delete(company) }
    }

    fun startEditingCompany(company: Company) {
        _editState.value = CompanyEditUiState(
            original = company,
            name = company.name,
            abn = company.abn.orEmpty(),
            phone = company.phone.orEmpty(),
            email = company.email.orEmpty(),
            hourlyRate = company.hourlyRate?.let { formatRateInput(it) }.orEmpty()
        )
    }

    fun onEditNameChanged(value: String) = _editState.update { it?.copy(name = value) }
    fun onEditAbnChanged(value: String) = _editState.update { it?.copy(abn = value) }
    fun onEditPhoneChanged(value: String) = _editState.update { it?.copy(phone = value) }
    fun onEditEmailChanged(value: String) = _editState.update { it?.copy(email = value) }
    fun onEditHourlyRateChanged(value: String) = _editState.update { it?.copy(hourlyRate = value) }

    fun cancelEditingCompany() {
        _editState.value = null
    }

    fun saveEditedCompany() {
        val state = _editState.value ?: return
        if (state.name.isBlank() || !isCompanyInputValid(state.abn, state.phone, state.email, state.hourlyRate)) return

        viewModelScope.launch {
            val newName = state.name.trim()
            dao.update(
                state.original.copy(
                    name = newName,
                    abn = state.abn.trim().ifBlank { null },
                    phone = state.phone.trim().ifBlank { null },
                    email = state.email.trim().ifBlank { null },
                    hourlyRate = Validators.parseAmount(state.hourlyRate)
                )
            )
            if (newName != state.original.name) {
                sessionDao.renameCompany(oldName = state.original.name, newName = newName)
                plannedJobDao.renameCompany(oldName = state.original.name, newName = newName)
                invoiceDao.renameCompany(oldName = state.original.name, newName = newName)
            }
            _editState.value = null
        }
    }

    private fun isCompanyInputValid(abn: String, phone: String, email: String, hourlyRate: String) =
        Validators.abnOk(abn) && Validators.phoneOk(phone) && Validators.emailOk(email) && Validators.amountOk(hourlyRate)
}

/** Rate as shown in an input field: no trailing ".00", at most two decimals. */
fun formatRateInput(rate: Double): String {
    val text = String.format(java.util.Locale.ENGLISH, "%.2f", rate)
    return text.trimEnd('0').trimEnd('.')
}
