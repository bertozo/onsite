package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClientFormUiState(
    val name: String = "",
    val abn: String = "",
    val phone: String = "",
    val email: String = "",
    /** Default hourly rate as typed; blank means "not set". */
    val hourlyRate: String = ""
)

data class ClientEditUiState(
    val original: Client,
    val name: String,
    val abn: String,
    val phone: String,
    val email: String,
    val hourlyRate: String
)

class ClientViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).clientDao()
    private val sessionDao = AppDatabase.getInstance(application).trackingSessionDao()
    private val plannedJobDao = AppDatabase.getInstance(application).plannedJobDao()
    private val invoiceDao = AppDatabase.getInstance(application).invoiceDao()

    private val _uiState = MutableStateFlow(ClientFormUiState())
    val uiState: StateFlow<ClientFormUiState> = _uiState

    val clients: StateFlow<List<Client>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editState = MutableStateFlow<ClientEditUiState?>(null)
    val editState: StateFlow<ClientEditUiState?> = _editState

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value) }
    fun onAbnChanged(value: String) = _uiState.update { it.copy(abn = value) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value) }
    fun onHourlyRateChanged(value: String) = _uiState.update { it.copy(hourlyRate = value) }

    fun saveClient() {
        val state = _uiState.value
        if (state.name.isBlank() || !isClientInputValid(state.abn, state.phone, state.email, state.hourlyRate)) return

        viewModelScope.launch {
            dao.insert(
                Client(
                    name = state.name.trim(),
                    abn = state.abn.trim().ifBlank { null },
                    phone = state.phone.trim().ifBlank { null },
                    email = state.email.trim().ifBlank { null },
                    createdAtMillis = System.currentTimeMillis(),
                    hourlyRate = Validators.parseAmount(state.hourlyRate)
                )
            )
            _uiState.update { ClientFormUiState() }
        }
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch { dao.delete(client) }
    }

    fun startEditingClient(client: Client) {
        _editState.value = ClientEditUiState(
            original = client,
            name = client.name,
            abn = client.abn.orEmpty(),
            phone = client.phone.orEmpty(),
            email = client.email.orEmpty(),
            hourlyRate = client.hourlyRate?.let { formatRateInput(it) }.orEmpty()
        )
    }

    fun onEditNameChanged(value: String) = _editState.update { it?.copy(name = value) }
    fun onEditAbnChanged(value: String) = _editState.update { it?.copy(abn = value) }
    fun onEditPhoneChanged(value: String) = _editState.update { it?.copy(phone = value) }
    fun onEditEmailChanged(value: String) = _editState.update { it?.copy(email = value) }
    fun onEditHourlyRateChanged(value: String) = _editState.update { it?.copy(hourlyRate = value) }

    fun cancelEditingClient() {
        _editState.value = null
    }

    fun saveEditedClient() {
        val state = _editState.value ?: return
        if (state.name.isBlank() || !isClientInputValid(state.abn, state.phone, state.email, state.hourlyRate)) return

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
                sessionDao.renameClient(oldName = state.original.name, newName = newName)
                plannedJobDao.renameClient(oldName = state.original.name, newName = newName)
                invoiceDao.renameClient(oldName = state.original.name, newName = newName)
            }
            _editState.value = null
        }
    }

    private fun isClientInputValid(abn: String, phone: String, email: String, hourlyRate: String) =
        Validators.abnOk(abn) && Validators.phoneOk(phone) && Validators.emailOk(email) && Validators.amountOk(hourlyRate)
}

/** Rate as shown in an input field: no trailing ".00", at most two decimals. */
fun formatRateInput(rate: Double): String {
    val text = String.format(java.util.Locale.ENGLISH, "%.2f", rate)
    return text.trimEnd('0').trimEnd('.')
}
