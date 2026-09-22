package com.xbertz.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.backend.AccountMembershipDto
import com.xbertz.onsite.backend.BackendApi
import com.xbertz.onsite.backend.ConnectionDto
import com.xbertz.onsite.backend.ConnectionInviteDto
import com.xbertz.onsite.backend.ConnectionPlannedJobRequest
import com.xbertz.onsite.backend.ConnectionSessionDto
import com.xbertz.onsite.backend.InviteDto
import com.xbertz.onsite.backend.MemberDto
import com.xbertz.onsite.backend.SessionStore
import com.xbertz.onsite.backend.SupabaseAuth
import com.xbertz.onsite.backend.SupabaseAuthException
import com.xbertz.onsite.backend.SupabaseAuthResult
import com.xbertz.onsite.backend.runSync
import com.xbertz.onsite.backend.wipeLocalDomainData
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.log.AppLog
import com.xbertz.onsite.log.logFailure
import com.xbertz.onsite.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object CheckingSession : AuthUiState
    data class LoggedOut(val error: UiMessage? = null, val info: UiMessage? = null) : AuthUiState
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
 * Independent of [AuthUiState] since it drives a dialog layered on top of the sign-in/sign-up
 * screen rather than replacing it - the two shouldn't fight over a shared error/loading field.
 */
sealed interface PasswordResetUiState {
    data object Hidden : PasswordResetUiState
    data class EnteringEmail(val loading: Boolean = false, val error: UiMessage? = null) : PasswordResetUiState
    data class EnteringCode(
        val email: String,
        val loading: Boolean = false,
        val error: UiMessage? = null,
    ) : PasswordResetUiState
}

/**
 * The account created for this user at signup, as opposed to one they later joined by
 * invite. Every account (a personal workspace or a team one) has the same "PERSONAL"
 * accountKind today, and /v1/me's membership order isn't guaranteed, so the one reliable
 * signal left is that an account's name is set to its creator's email at creation time.
 */
private fun List<AccountMembershipDto>.mine(email: String): AccountMembershipDto =
    firstOrNull { it.accountName == email } ?: first()

private const val TAG = "Auth"

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.CheckingSession)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _pendingInvites = MutableStateFlow<List<InviteDto>>(emptyList())
    val pendingInvites: StateFlow<List<InviteDto>> = _pendingInvites.asStateFlow()

    private val _pendingConnectionInvites = MutableStateFlow<List<ConnectionInviteDto>>(emptyList())
    val pendingConnectionInvites: StateFlow<List<ConnectionInviteDto>> = _pendingConnectionInvites.asStateFlow()

    private val _connections = MutableStateFlow<List<ConnectionDto>>(emptyList())
    val connections: StateFlow<List<ConnectionDto>> = _connections.asStateFlow()

    private val _members = MutableStateFlow<List<MemberDto>>(emptyList())
    val members: StateFlow<List<MemberDto>> = _members.asStateFlow()

    /** The contractors connected *to* this account, as seen by an OWNER (client side of the bridge). */
    private val _employerConnections = MutableStateFlow<List<ConnectionDto>>(emptyList())
    val employerConnections: StateFlow<List<ConnectionDto>> = _employerConnections.asStateFlow()

    private val _passwordReset = MutableStateFlow<PasswordResetUiState>(PasswordResetUiState.Hidden)
    val passwordReset: StateFlow<PasswordResetUiState> = _passwordReset.asStateFlow()

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
                loadPendingConnectionInvites()
                loadConnections()
            }
        }
    }

    /** A pull can add, move or remove planned jobs, so the reminder alarms are re-armed after every sync. */
    private suspend fun syncAndReschedule(token: String) {
        runCatching { runSync(db, token) }.logFailure(TAG, "sync")
        runCatching { ReminderScheduler.reschedule(getApplication()) }.logFailure(TAG, "reminder reschedule")
    }

    private suspend fun refreshMe(token: String, email: String) {
        val me = runCatching { BackendApi.me(token) }.logFailure(TAG, "GET /v1/me").getOrNull()
        if (me == null) {
            // Offline on cold start: fall back to the cached personal account rather than
            // blocking the whole app on a network call.
            val accountId = sessionStore.accountId
            // Worth a line of its own: this is the branch behind "the app forgot which
            // account I was on" and "my team's data vanished", and it is otherwise silent.
            AppLog.w(TAG, "identity unavailable, falling back to cache cachedAccount=${accountId != null}")
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

    private fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        return if (trimmed.isEmpty() || !trimmed.contains("@")) null else trimmed
    }

    fun signIn(email: String, password: String) {
        val trimmed = validateEmail(email)
        if (trimmed == null) {
            _state.value = AuthUiState.LoggedOut(error = UiMessage(R.string.login_error_invalid_email))
            return
        }
        if (password.isEmpty()) {
            _state.value = AuthUiState.LoggedOut(error = UiMessage(R.string.login_error_password_required))
            return
        }
        _state.value = AuthUiState.LoggingIn
        viewModelScope.launch {
            try {
                val result = SupabaseAuth.signIn(trimmed, password)
                completeLogin(result.accessToken)
            } catch (e: SupabaseAuthException) {
                // The code, never the email or password: a log that quotes credentials is
                // one nobody can safely share back to us.
                AppLog.w(TAG, "sign-in rejected code=${e.code}")
                _state.value = AuthUiState.LoggedOut(error = mapAuthError(e))
            } catch (e: Exception) {
                AppLog.e(TAG, "sign-in failed", e)
                _state.value = AuthUiState.LoggedOut(error = UiMessage(R.string.login_error_failed))
            }
        }
    }

    fun signUp(email: String, password: String) {
        val trimmed = validateEmail(email)
        if (trimmed == null) {
            _state.value = AuthUiState.LoggedOut(error = UiMessage(R.string.login_error_invalid_email))
            return
        }
        if (password.length < 6) {
            _state.value = AuthUiState.LoggedOut(error = UiMessage(R.string.login_error_password_too_short))
            return
        }
        _state.value = AuthUiState.LoggingIn
        viewModelScope.launch {
            try {
                when (val result = SupabaseAuth.signUp(trimmed, password)) {
                    is SupabaseAuthResult.SignedIn -> completeLogin(result.accessToken)
                    SupabaseAuthResult.ConfirmationEmailSent ->
                        _state.value = AuthUiState.LoggedOut(info = UiMessage(R.string.signup_confirmation_sent))
                }
            } catch (e: SupabaseAuthException) {
                AppLog.w(TAG, "sign-up rejected code=${e.code}")
                _state.value = AuthUiState.LoggedOut(error = mapAuthError(e))
            } catch (e: Exception) {
                AppLog.e(TAG, "sign-up failed", e)
                _state.value = AuthUiState.LoggedOut(error = UiMessage(R.string.login_error_failed))
            }
        }
    }

    fun openPasswordReset() {
        _passwordReset.value = PasswordResetUiState.EnteringEmail()
    }

    fun dismissPasswordReset() {
        _passwordReset.value = PasswordResetUiState.Hidden
    }

    /**
     * Moves on to the code step regardless of whether the email is registered, so this can't
     * be used to probe accounts - except when Supabase rejects the request for being rate
     * limited, which reveals nothing about the account and, left silent, sends the user to
     * type in a code from an email that was never actually sent (see CLAUDE.md: requesting a
     * new code invalidates whatever token they were already holding). The recovery email
     * carries a 6-digit code (the project's template shows {{ .Token }} instead of a link -
     * see [SupabaseAuth.recover]), so there's no deep link for the app to catch; the user
     * copies the code back in here.
     */
    fun sendPasswordResetCode(email: String) {
        val trimmed = validateEmail(email)
        if (trimmed == null) {
            _passwordReset.value = PasswordResetUiState.EnteringEmail(error = UiMessage(R.string.login_error_invalid_email))
            return
        }
        _passwordReset.value = PasswordResetUiState.EnteringEmail(loading = true)
        viewModelScope.launch {
            val error = runCatching { SupabaseAuth.recover(trimmed) }
                .logFailure(TAG, "password recovery request")
                .exceptionOrNull() as? SupabaseAuthException
            if (error?.code == "over_email_send_rate_limit") {
                _passwordReset.value = PasswordResetUiState.EnteringEmail(error = mapAuthError(error))
                return@launch
            }
            _passwordReset.value = PasswordResetUiState.EnteringCode(email = trimmed)
        }
    }

    /** Verifies the emailed code, sets the new password, and signs the user straight in. */
    fun confirmPasswordReset(code: String, newPassword: String) {
        val current = _passwordReset.value as? PasswordResetUiState.EnteringCode ?: return
        if (code.isBlank()) {
            _passwordReset.value = current.copy(error = UiMessage(R.string.reset_password_error_code_required))
            return
        }
        if (newPassword.length < 6) {
            _passwordReset.value = current.copy(error = UiMessage(R.string.login_error_password_too_short))
            return
        }
        _passwordReset.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val token = SupabaseAuth.verifyRecovery(current.email, code.trim())
                SupabaseAuth.updatePassword(token, newPassword)
                _passwordReset.value = PasswordResetUiState.Hidden
                completeLogin(token)
            } catch (e: SupabaseAuthException) {
                _passwordReset.value = current.copy(loading = false, error = mapAuthError(e, fallback = R.string.reset_password_error_invalid_code))
            } catch (e: Exception) {
                _passwordReset.value = current.copy(loading = false, error = UiMessage(R.string.login_error_failed))
            }
        }
    }

    private fun mapAuthError(e: SupabaseAuthException, fallback: Int = R.string.login_error_failed): UiMessage = when (e.code) {
        "user_already_exists" -> UiMessage(R.string.login_error_email_already_registered)
        "invalid_credentials", "invalid_grant" -> UiMessage(R.string.login_error_invalid_credentials)
        "email_not_confirmed" -> UiMessage(R.string.login_error_email_not_confirmed)
        "weak_password" -> UiMessage(R.string.login_error_password_too_short)
        "otp_expired", "otp_disabled" -> UiMessage(R.string.reset_password_error_invalid_code)
        "over_email_send_rate_limit" -> UiMessage(R.string.reset_password_error_rate_limited)
        else -> UiMessage(fallback)
    }

    private suspend fun completeLogin(token: String) {
        val me = BackendApi.bootstrap(token)
        val personalAccount = me.memberships.mine(me.email)
        sessionStore.save(token, me.email, personalAccount.accountId)
        BackendApi.activeAccountId = null

        AppLog.i(TAG, "signed in account=${personalAccount.accountId} memberships=${me.memberships.size}")

        _state.value = AuthUiState.SyncingData
        syncAndReschedule(token)

        _state.value = AuthUiState.LoggedIn(
            me.email, personalAccount.accountId, personalAccount.accountName, personalAccount.role, me.memberships
        )
        loadPendingInvites()
        loadPendingConnectionInvites()
        loadConnections()
    }

    /** Best-effort, silent: called opportunistically (e.g. app resume) while already signed in. */
    fun syncNow() {
        val token = sessionStore.token ?: return
        if (_state.value !is AuthUiState.LoggedIn) return
        viewModelScope.launch {
            syncAndReschedule(token)
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

        AppLog.i(TAG, "switching account ${current.activeAccountId} -> $accountId")
        _state.value = AuthUiState.SyncingData
        viewModelScope.launch {
            syncAndReschedule(token)
            wipeLocalDomainData(db)
            BackendApi.activeAccountId = accountId
            sessionStore.setActiveAccount(accountId)
            syncAndReschedule(token)
            _state.value = AuthUiState.LoggedIn(
                current.email, target.accountId, target.accountName, target.role, current.memberships
            )
        }
    }

    fun loadPendingInvites() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            _pendingInvites.value = runCatching { BackendApi.listMyInvites(token) }.logFailure(TAG, "GET /v1/me/invites").getOrDefault(emptyList())
        }
    }

    fun acceptInvite(inviteId: String) {
        val token = sessionStore.token ?: return
        val email = sessionStore.email ?: return
        viewModelScope.launch {
            runCatching { BackendApi.acceptInvite(token, inviteId) }.logFailure(TAG, "POST /v1/me/invites/accept")
            loadPendingInvites()
            refreshMe(token, email)
        }
    }

    fun inviteWorker(email: String, onResult: (success: Boolean) -> Unit) {
        val token = sessionStore.token ?: return
        val current = _state.value as? AuthUiState.LoggedIn ?: return
        viewModelScope.launch {
            val result = runCatching { BackendApi.createInvite(token, current.activeAccountId, email.trim()) }.logFailure(TAG, "POST /v1/accounts/invites")
            onResult(result.isSuccess)
        }
    }

    fun loadPendingConnectionInvites() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            _pendingConnectionInvites.value = runCatching { BackendApi.listMyConnectionInvites(token) }.logFailure(TAG, "GET /v1/me/connection-invites").getOrDefault(emptyList())
        }
    }

    fun loadConnections() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            _connections.value = runCatching { BackendApi.listMyConnections(token) }.logFailure(TAG, "GET /v1/me/connections").getOrDefault(emptyList())
        }
    }

    fun loadEmployerConnections() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            _employerConnections.value = runCatching { BackendApi.listEmployerConnections(token) }.logFailure(TAG, "GET /v1/connections").getOrDefault(emptyList())
        }
    }

    /** OWNER-only on the backend; called from screens with an "assign to" picker (e.g. Planning). */
    fun loadMembers() {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            _members.value = runCatching { BackendApi.listAccountMembers(token) }.logFailure(TAG, "GET /v1/me/account-members").getOrDefault(emptyList())
        }
    }

    /** Employer schedules a job straight onto the connected worker's own calendar. */
    fun scheduleConnectionJob(connectionId: String, req: ConnectionPlannedJobRequest, onResult: (success: Boolean) -> Unit) {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            val result = runCatching { BackendApi.createConnectionPlannedJob(token, connectionId, req) }.logFailure(TAG, "POST /v1/connections/planned-jobs")
            onResult(result.isSuccess)
        }
    }

    /** Employer reads the hours the connected worker has logged against this connection's client. */
    fun loadConnectionSessions(connectionId: String, onResult: (List<ConnectionSessionDto>) -> Unit) {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            onResult(runCatching { BackendApi.listConnectionSessions(token, connectionId) }.logFailure(TAG, "GET /v1/connections/sessions").getOrDefault(emptyList()))
        }
    }

    /**
     * [localClientId] is one of the caller's own clients (their Room row id) - which of
     * their clients this link is. The backend only knows clients by their synced UUID, so
     * this resolves that first; it fails if the client hasn't synced yet.
     */
    fun acceptConnectionInvite(inviteId: String, localClientId: Long, onResult: (success: Boolean) -> Unit) {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            val remoteClientId = db.syncDao().mappingsFor("client").firstOrNull { it.localId == localClientId }?.remoteId
            val result = if (remoteClientId == null) {
                Result.failure(IllegalStateException("client not synced yet"))
            } else {
                runCatching { BackendApi.acceptConnectionInvite(token, inviteId, remoteClientId) }.logFailure(TAG, "POST /v1/me/connection-invites/accept")
            }
            loadPendingConnectionInvites()
            loadConnections()
            onResult(result.isSuccess)
        }
    }

    fun revokeConnection(connectionId: String) {
        val token = sessionStore.token ?: return
        viewModelScope.launch {
            runCatching { BackendApi.revokeConnection(token, connectionId) }.logFailure(TAG, "DELETE /v1/connections")
            loadConnections()
        }
    }

    /** A client invites one of their contractors by email, linking a client the contractor picks. */
    fun inviteContractor(email: String, onResult: (success: Boolean) -> Unit) {
        val token = sessionStore.token ?: return
        val current = _state.value as? AuthUiState.LoggedIn ?: return
        viewModelScope.launch {
            val result = runCatching { BackendApi.createConnectionInvite(token, current.activeAccountId, email.trim()) }.logFailure(TAG, "POST /v1/accounts/connection-invites")
            onResult(result.isSuccess)
        }
    }

    fun logout() {
        AppLog.i(TAG, "signing out, local data kept")
        sessionStore.clear()
        BackendApi.activeAccountId = null
        _pendingInvites.value = emptyList()
        _pendingConnectionInvites.value = emptyList()
        _connections.value = emptyList()
        _members.value = emptyList()
        _employerConnections.value = emptyList()
        _state.value = AuthUiState.LoggedOut()
    }
}
