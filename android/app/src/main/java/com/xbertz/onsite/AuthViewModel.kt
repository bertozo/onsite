package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.backend.AccountMembershipDto
import com.xbertz.onsite.backend.BackendApi
import com.xbertz.onsite.backend.InviteDto
import com.xbertz.onsite.backend.SessionStore
import com.xbertz.onsite.backend.runSync
import com.xbertz.onsite.backend.wipeLocalDomainData
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
    data class LoggedIn(
        val email: String,
        val activeAccountId: String,
        val activeAccountName: String,
        val activeRole: String,
        val memberships: List<AccountMembershipDto>,
    ) : AuthUiState {
        val isOwner: Boolean get() = activeRole == "OWNER"
    }
}

/**
 * The account created for this user at signup, as opposed to one they later joined by
 * invite. Every account (a personal workspace or a team one) has the same "PERSONAL"
 * accountKind today, and /v1/me's membership order isn't guaranteed, so the one reliable
 * signal left is that an account's name is set to its creator's email at creation time.
 */
private fun List<AccountMembershipDto>.mine(email: String): AccountMembershipDto =
    firstOrNull { it.accountName == email } ?: first()

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.CheckingSession)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _pendingInvites = MutableStateFlow<List<InviteDto>>(emptyList())
    val pendingInvites: StateFlow<List<InviteDto>> = _pendingInvites.asStateFlow()

    init {
        val token = sessionStore.token
        val email = sessionStore.email
        if (token == null || email == null) {
            _state.value = AuthUiState.LoggedOut()
        } else {
            BackendApi.activeAccountId = sessionStore.activeAccountId
            viewModelScope.launch {
                refreshMe(token, email)
                loadPendingInvites()
            }
        }
    }

    private suspend fun refreshMe(token: String, email: String) {
        val me = runCatching { BackendApi.me(token) }.getOrNull()
        if (me == null) {
            // Offline on cold start: fall back to the cached personal account rather than
            // blocking the whole app on a network call.
            val accountId = sessionStore.accountId
            if (accountId != null) {
                _state.value = AuthUiState.LoggedIn(email, accountId, email, "OWNER", emptyList())
            } else {
                _state.value = AuthUiState.LoggedOut()
            }
            return
        }
        val activeId = sessionStore.activeAccountId ?: me.memberships.mine(me.email).accountId
        val active = me.memberships.firstOrNull { it.accountId == activeId } ?: me.memberships.mine(me.email)
        _state.value = AuthUiState.LoggedIn(me.email, active.accountId, active.accountName, active.role, me.memberships)
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
                val personalAccount = me.memberships.mine(me.email)
                sessionStore.save(token, me.email, personalAccount.accountId)
                BackendApi.activeAccountId = null

                _state.value = AuthUiState.SyncingData
                runCatching { runSync(db, token) }

                _state.value = AuthUiState.LoggedIn(
                    me.email, personalAccount.accountId, personalAccount.accountName, personalAccount.role, me.memberships
                )
                loadPendingInvites()
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

    /**
     * Room holds one account's data at a time (see [wipeLocalDomainData]), so switching pushes
     * whatever is pending against the account being left, clears local identity, and re-syncs
     * fresh against the new one.
     */
    fun switchAccount(accountId: String) {
        val token = sessionStore.token ?: return
        val current = _state.value as? AuthUiState.LoggedIn ?: return
        if (accountId == current.activeAccountId) return
        val target = current.memberships.firstOrNull { it.accountId == accountId } ?: return

        _state.value = AuthUiState.SyncingData
        viewModelScope.launch {
            runCatching { runSync(db, token) }
            wipeLocalDomainData(db)
            BackendApi.activeAccountId = accountId
            sessionStore.setActiveAccount(accountId)
            runCatching { runSync(db, token) }
            _state.value = AuthUiState.LoggedIn(
                current.email, target.accountId, target.accountName, target.role, current.memberships
            )
        }
    }

    fun loadPendingInvites() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            _pendingInvites.value = runCatching { BackendApi.listMyInvites(token) }.getOrDefault(emptyList())
        }
    }

    fun acceptInvite(inviteId: String) {
        val token = sessionStore.token ?: return
        val email = sessionStore.email ?: return
        viewModelScope.launch {
            runCatching { BackendApi.acceptInvite(token, inviteId) }
            loadPendingInvites()
            refreshMe(token, email)
        }
    }

    fun inviteWorker(email: String, onResult: (success: Boolean) -> Unit) {
        val token = sessionStore.token ?: return
        val current = _state.value as? AuthUiState.LoggedIn ?: return
        viewModelScope.launch {
            val result = runCatching { BackendApi.createInvite(token, current.activeAccountId, email.trim()) }
            onResult(result.isSuccess)
        }
    }

    fun logout() {
        sessionStore.clear()
        BackendApi.activeAccountId = null
        _pendingInvites.value = emptyList()
        _state.value = AuthUiState.LoggedOut()
    }
}
