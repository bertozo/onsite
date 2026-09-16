package com.rodolfobertozo.geotracker

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodolfobertozo.geotracker.data.AppDatabase
import com.rodolfobertozo.geotracker.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ProfileUiState(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val role: String = "",
    val abn: String = "",
    val photoPath: String? = null,
    val bankBsb: String = "",
    val bankAccount: String = "",
    val isSaved: Boolean = false
) {
    val isValid: Boolean
        get() = Validators.phoneOk(phone) && Validators.emailOk(email) && Validators.abnOk(abn) &&
            Validators.bsbOk(bankBsb) && Validators.accountNumberOk(bankAccount)
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).profileDao()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            dao.get().collect { profile ->
                if (profile != null) {
                    _uiState.update {
                        it.copy(
                            name = profile.name,
                            phone = profile.phone.orEmpty(),
                            email = profile.email.orEmpty(),
                            role = profile.role.orEmpty(),
                            abn = profile.abn.orEmpty(),
                            photoPath = profile.photoPath,
                            bankBsb = profile.bankBsb.orEmpty(),
                            bankAccount = profile.bankAccount.orEmpty()
                        )
                    }
                }
            }
        }
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value, isSaved = false) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value, isSaved = false) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, isSaved = false) }
    fun onRoleChanged(value: String) = _uiState.update { it.copy(role = value, isSaved = false) }
    fun onAbnChanged(value: String) = _uiState.update { it.copy(abn = value, isSaved = false) }
    fun onBankBsbChanged(value: String) = _uiState.update { it.copy(bankBsb = value, isSaved = false) }
    fun onBankAccountChanged(value: String) = _uiState.update { it.copy(bankAccount = value, isSaved = false) }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                try {
                    val application = getApplication<Application>()
                    val outFile = File(application.filesDir, "profile_photo.jpg")
                    application.contentResolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outFile.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            if (savedPath != null) {
                _uiState.update { it.copy(photoPath = savedPath, isSaved = false) }
            }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.name.isBlank() || !state.isValid) return

        viewModelScope.launch {
            dao.upsert(
                Profile(
                    name = state.name.trim(),
                    phone = state.phone.trim().ifBlank { null },
                    email = state.email.trim().ifBlank { null },
                    role = state.role.trim().ifBlank { null },
                    abn = state.abn.trim().ifBlank { null },
                    photoPath = state.photoPath,
                    bankBsb = state.bankBsb.trim().ifBlank { null },
                    bankAccount = state.bankAccount.trim().ifBlank { null }
                )
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
