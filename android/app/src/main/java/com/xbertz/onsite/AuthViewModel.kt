package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.backend.BackendApi
import com.xbertz.onsite.backend.SessionStore
import com.xbertz.onsite.backend.runSync
import com.xbertz.onsite.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object CheckingSession : AuthUiState
    data class LoggedOut(val error: UiMessage? = null) : AuthUiState
    data object LoggingIn : AuthUiState
    data object SyncingData : AuthUiState
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

                _state.value = AuthUiState.SyncingData
                runCatching { runSync(db, token) }

                _state.value = AuthUiState.LoggedIn(me.email, personalAccount.accountId)
            } catch (e: Exception) {
                _state.value = AuthUiState.LoggedOut(UiMessage(R.string.login_error_failed))
            }
        }
    }

    /** Best-effort, silent: called opportunistically (e.g. app resume) while already signed in. */
    fun syncNow() {
        val token = sessionStore.token ?: return
        if (_state.value !is AuthUiState.LoggedIn) return
        viewModelScope.launch {
            runCatching { runSync(db, token) }
        }
    }

    fun logout() {
        sessionStore.clear()
        _state.value = AuthUiState.LoggedOut()
    }
}
