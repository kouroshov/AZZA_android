package com.azza.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.errorMessage

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onSignedIn() }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthForm(title = stringResource(R.string.auth_sign_in_title)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorLine(state.error)
        SubmitButton(
            text = stringResource(R.string.auth_sign_in),
            busy = state.busy,
            enabled = email.isNotBlank() && password.isNotEmpty(),
            onClick = { viewModel.login(email, password) },
        )
        TextButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_no_account))
        }
    }
}

@Composable
fun RegisterScreen(
    onSignedIn: () -> Unit,
    onHaveAccount: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onSignedIn() }

    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthForm(title = stringResource(R.string.auth_sign_up_title)) {
        Field(firstName, { firstName = it }, R.string.auth_first_name)
        Field(lastName, { lastName = it }, R.string.auth_last_name)
        Field(username, { username = it }, R.string.auth_username)
        Field(email, { email = it }, R.string.auth_email, KeyboardType.Email)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            supportingText = { Text(stringResource(R.string.auth_password_rule)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorLine(state.error)
        SubmitButton(
            text = stringResource(R.string.auth_sign_up),
            busy = state.busy,
            enabled = listOf(firstName, lastName, username, email, password).all { it.isNotBlank() },
            onClick = { viewModel.register(email, username, password, firstName, lastName) },
        )
        TextButton(onClick = onHaveAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_have_account))
        }
    }
}

@Composable
private fun AuthForm(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.auth_brand),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, color = AzzaTheme.colors.textSecondary)
        content()
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorLine(error: AppError?) {
    if (error == null) return
    val text = when ((error as? AppError.Api)?.code) {
        // apps/api's login answers one code for "no such account", "wrong
        // password" and a locked account alike (no enumeration); say it the same way.
        "AUTH_INVALID_CREDENTIALS" -> stringResource(R.string.auth_error_credentials)
        "AUTH_EMAIL_TAKEN" -> stringResource(R.string.auth_error_email_taken)
        "USERNAME_TAKEN" -> stringResource(R.string.auth_error_username_taken)
        "ACCOUNT_NOT_ACTIVE" -> stringResource(R.string.auth_error_not_active)
        else -> errorMessage(error)
    }
    Text(text, color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SubmitButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
        } else {
            Text(text)
        }
    }
}
