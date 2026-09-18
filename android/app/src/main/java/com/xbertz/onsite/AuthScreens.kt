package com.xbertz.onsite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared by every password field so show/hide behaves identically everywhere it appears. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = when {
            imeAction == ImeAction.Next -> nextFieldActions(focusManager)
            onDone != null -> KeyboardActions(onDone = { onDone(); focusManager.clearFocus() })
            else -> doneFieldActions(focusManager)
        },
        modifier = modifier,
        isError = isError,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (visible) R.string.password_hide else R.string.password_show)
                )
            }
        }
    )
}

private enum class AuthMode { SIGN_IN, SIGN_UP }

@Composable
internal fun AuthScreen(
    loading: Boolean,
    error: UiMessage?,
    info: UiMessage?,
    passwordReset: PasswordResetUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onOpenPasswordReset: () -> Unit,
    onDismissPasswordReset: () -> Unit,
    onSendPasswordResetCode: (email: String) -> Unit,
    onConfirmPasswordReset: (code: String, newPassword: String) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val emailFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { emailFocusRequester.requestFocus() }

    val confirmMismatch = mode == AuthMode.SIGN_UP && confirmPassword.isNotEmpty() && confirmPassword != password
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !confirmMismatch &&
        (mode == AuthMode.SIGN_IN || confirmPassword.isNotBlank())
    val submit = {
        if (canSubmit && !loading) {
            if (mode == AuthMode.SIGN_IN) onSignIn(email, password) else onSignUp(email, password)
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.login_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(if (mode == AuthMode.SIGN_IN) R.string.login_subtitle else R.string.signup_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.login_email_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = nextFieldActions(focusManager),
                modifier = Modifier.fillMaxWidth().focusRequester(emailFocusRequester),
                isError = error != null
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.login_password_label),
                modifier = Modifier.fillMaxWidth(),
                isError = error != null,
                imeAction = if (mode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done,
                onDone = submit
            )
            if (mode == AuthMode.SIGN_UP) {
                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = stringResource(R.string.signup_confirm_password_label),
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmMismatch,
                    onDone = submit
                )
                if (confirmMismatch) {
                    Text(
                        stringResource(R.string.login_error_passwords_dont_match),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (mode == AuthMode.SIGN_IN) {
                TextButton(onClick = onOpenPasswordReset) {
                    Text(stringResource(R.string.login_forgot_password))
                }
            }
            if (error != null) {
                Text(
                    stringResource(error.resId, *error.args.toTypedArray()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (info != null) {
                Text(
                    stringResource(info.resId, *info.args.toTypedArray()),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = submit,
                enabled = !loading && canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 2.dp))
                } else {
                    Text(stringResource(if (mode == AuthMode.SIGN_IN) R.string.login_button else R.string.signup_button))
                }
            }
            TextButton(
                onClick = {
                    mode = if (mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN
                    confirmPassword = ""
                },
                enabled = !loading
            ) {
                Text(stringResource(if (mode == AuthMode.SIGN_IN) R.string.login_toggle_to_signup else R.string.login_toggle_to_signin))
            }
        }
    }

    when (passwordReset) {
        PasswordResetUiState.Hidden -> Unit
        is PasswordResetUiState.EnteringEmail -> RequestResetCodeDialog(
            initialEmail = email,
            loading = passwordReset.loading,
            error = passwordReset.error,
            onDismiss = onDismissPasswordReset,
            onSend = onSendPasswordResetCode,
        )
        is PasswordResetUiState.EnteringCode -> ConfirmResetCodeDialog(
            email = passwordReset.email,
            loading = passwordReset.loading,
            error = passwordReset.error,
            onDismiss = onDismissPasswordReset,
            onResend = { onSendPasswordResetCode(passwordReset.email) },
            onConfirm = onConfirmPasswordReset,
        )
    }
}

@Composable
private fun RequestResetCodeDialog(
    initialEmail: String,
    loading: Boolean,
    error: UiMessage?,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    val focusManager = LocalFocusManager.current
    val emailFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { emailFocusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forgot_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.forgot_password_message), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.login_email_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (email.isNotBlank()) onSend(email)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(emailFocusRequester),
                    isError = error != null
                )
                if (error != null) {
                    Text(
                        stringResource(error.resId, *error.args.toTypedArray()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(email) }, enabled = !loading && email.isNotBlank()) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 2.dp))
                } else {
                    Text(stringResource(R.string.forgot_password_send_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ConfirmResetCodeDialog(
    email: String,
    loading: Boolean,
    error: UiMessage?,
    onDismiss: () -> Unit,
    onResend: () -> Unit,
    onConfirm: (code: String, newPassword: String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val confirmMismatch = confirmPassword.isNotEmpty() && confirmPassword != newPassword
    val focusManager = LocalFocusManager.current
    val codeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { codeFocusRequester.requestFocus() }
    val submit = {
        if (!loading && code.isNotBlank() && newPassword.isNotBlank() && !confirmMismatch && confirmPassword.isNotBlank()) {
            onConfirm(code, newPassword)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_password_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.reset_password_code_message, email),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.reset_password_code_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = nextFieldActions(focusManager),
                    modifier = Modifier.fillMaxWidth().focusRequester(codeFocusRequester)
                )
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = stringResource(R.string.reset_password_new_password_label),
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Next
                )
                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = stringResource(R.string.reset_password_confirm_password_label),
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmMismatch,
                    onDone = submit
                )
                if (confirmMismatch) {
                    Text(
                        stringResource(R.string.login_error_passwords_dont_match),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = onResend, enabled = !loading) {
                    Text(stringResource(R.string.reset_password_resend_button))
                }
                if (error != null) {
                    Text(
                        stringResource(error.resId, *error.args.toTypedArray()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = !loading && code.isNotBlank() && newPassword.isNotBlank() && !confirmMismatch && confirmPassword.isNotBlank()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 2.dp))
                } else {
                    Text(stringResource(R.string.reset_password_confirm_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun FullScreenProgress(title: String? = null, subtitle: String? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
