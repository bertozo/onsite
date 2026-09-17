package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.backend.BackendApi
import com.xbertz.onsite.backend.SessionStore
import com.xbertz.onsite.backend.uploadLocalDataToBackend
import com.xbertz.onsite.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object CheckingSession : AuthUiState
    data class LoggedOut(val error: UiMessage? = null) : AuthUiState
    data object LoggingIn : AuthUiState
    data object MigratingData : AuthUiState
    data class LoggedIn(val email: String, val accountId: String) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.CheckingSession)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        val token = sessionStore.token
        val email = sessionStore.email
        val accountId = sessionStore.accountId
        _state.value = if (token != null && email != null && accountId != null) {
            AuthUiState.LoggedIn(email, accountId)
        } else {
            AuthUiState.LoggedOut()
        }
    }

    fun login(email: String) {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) {
            _state.value = AuthUiState.LoggedOut(UiMessage(R.string.login_error_invalid_email))
            return
        }
        _state.value = AuthUiState.LoggingIn
        viewModelScope.launch {
            try {
                val token = BackendApi.devLogin(trimmed)
                val me = BackendApi.bootstrap(token)
                val personalAccount = me.memberships.first()
                sessionStore.save(token, me.email, personalAccount.accountId)

                if (!sessionStore.hasMigratedLocalData) {
                    _state.value = AuthUiState.MigratingData
                    uploadLocalDataToBackend(db, token)
                    sessionStore.markMigrated()
                }

                _state.value = AuthUiState.LoggedIn(me.email, personalAccount.accountId)
            } catch (e: Exception) {
                _state.value = AuthUiState.LoggedOut(UiMessage(R.string.login_error_failed))
            }
        }
    }

    fun logout() {
        sessionStore.clear()
        _state.value = AuthUiState.LoggedOut()
    }
}
