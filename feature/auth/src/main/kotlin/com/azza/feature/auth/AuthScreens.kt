package com.azza.feature.auth

import com.azza.core.ui.isIranMobile
import com.azza.core.ui.ChoiceMenu
import com.azza.core.ui.Avatar
import com.azza.core.data.UsernameCheck
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    var forgot by rememberSaveable { mutableStateOf(false) }

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
            keyboardActions = KeyboardActions(onDone = {
                if (email.isNotBlank() && password.isNotEmpty()) viewModel.login(email, password)
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorLine(state.error)
        SubmitButton(
            text = stringResource(R.string.auth_sign_in),
            busy = state.busy,
            enabled = email.isNotBlank() && password.isNotEmpty(),
            onClick = { viewModel.login(email, password) },
        )
        TextButton(onClick = { forgot = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_forgot))
        }
        TextButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_no_account))
        }
    }
    if (forgot) ForgotPasswordDialog(initialEmail = email, onDismiss = { forgot = false })
}

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    val close = { viewModel.reset(); onDismiss() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.auth_forgot_title)) },
        text = {
            if (state.sent) {
                Text(stringResource(R.string.auth_forgot_sent))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.auth_forgot_intro))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.auth_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ErrorLine(state.error)
                }
            }
        },
        confirmButton = {
            if (state.sent) {
                TextButton(onClick = close) { Text(stringResource(R.string.auth_close)) }
            } else {
                TextButton(onClick = { viewModel.send(email) }, enabled = !state.sending && email.isNotBlank()) {
                    Text(stringResource(R.string.auth_forgot_send))
                }
            }
        },
        dismissButton = { if (!state.sent) TextButton(onClick = close) { Text(stringResource(R.string.auth_cancel)) } },
    )
}

@Composable
fun RegisterScreen(
    onSignedIn: () -> Unit,
    onHaveAccount: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val provinces by viewModel.provinces.collectAsStateWithLifecycle()
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    val usernameCheck by viewModel.usernameCheck.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onSignedIn() }
    LaunchedEffect(Unit) { viewModel.loadProvinces() }

    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    // Errors are shown once Sign up was pressed, as the web validates on submit.
    var tried by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(province) { if (province.isNotEmpty()) viewModel.loadCities(province) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { photo = it.toString() } }

    val phoneBad = !isIranMobile(phone)
    val passwordProblem = passwordProblem(password)
    val mismatch = confirm != password
    val valid = listOf(firstName, lastName, username, email, province, city).all { it.isNotBlank() } &&
        !phoneBad && passwordProblem == null && !mismatch && usernameCheck != UsernameCheck.TAKEN && usernameCheck != UsernameCheck.INVALID

    AuthForm(title = stringResource(R.string.auth_sign_up_title)) {
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Avatar(url = photo, size = 88)
            Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.auth_photo), tint = AzzaTheme.colors.primary)
        }
        Text(stringResource(R.string.auth_photo_optional), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
        Field(firstName, { firstName = it }, R.string.auth_first_name, error = tried && firstName.isBlank())
        Field(lastName, { lastName = it }, R.string.auth_last_name, error = tried && lastName.isBlank())
        OutlinedTextField(
            value = username,
            onValueChange = { v -> username = v.lowercase().filter { !it.isWhitespace() }; viewModel.checkUsername(username) },
            label = { Text(stringResource(R.string.auth_username)) },
            singleLine = true,
            isError = usernameCheck == UsernameCheck.TAKEN || usernameCheck == UsernameCheck.INVALID || usernameCheck == UsernameCheck.RESERVED,
            supportingText = usernameCheck?.let { c ->
                {
                    Text(
                        stringResource(
                            when (c) {
                                UsernameCheck.AVAILABLE, UsernameCheck.UNCHANGED -> R.string.auth_username_free
                                UsernameCheck.TAKEN -> R.string.auth_error_username_taken
                                UsernameCheck.RESERVED -> R.string.auth_username_reserved
                                UsernameCheck.INVALID -> R.string.auth_username_invalid
                                UsernameCheck.UNKNOWN -> R.string.auth_username_unknown
                            },
                        ),
                        color = if (c == UsernameCheck.AVAILABLE) AzzaTheme.colors.success else AzzaTheme.colors.error,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Field(email, { email = it }, R.string.auth_email, KeyboardType.Email, error = tried && email.isBlank())
        Field(
            phone, { phone = it.take(14) }, R.string.auth_phone, KeyboardType.Phone,
            error = tried && phoneBad,
            hint = if (tried && phoneBad) stringResource(R.string.auth_phone_invalid) else stringResource(R.string.auth_phone_hint),
        )
        ChoiceMenu(
            label = stringResource(R.string.auth_province),
            value = province,
            options = provinces.map { it.name },
            onChoose = { if (it != province) { province = it; city = "" } },
            isError = tried && province.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceMenu(
            label = stringResource(R.string.auth_city),
            value = city,
            options = cities.map { it.name },
            onChoose = { city = it },
            isError = tried && city.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            isError = tried && passwordProblem != null,
            supportingText = { Text(passwordProblem?.takeIf { tried }?.let { stringResource(it) } ?: stringResource(R.string.auth_password_rule)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(R.string.auth_confirm_password)) },
            isError = confirm.isNotEmpty() && mismatch,
            supportingText = if (confirm.isNotEmpty() && mismatch) { { Text(stringResource(R.string.auth_password_mismatch)) } } else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorLine(state.error)
        SubmitButton(
            text = stringResource(R.string.auth_sign_up),
            busy = state.busy,
            enabled = true,
            onClick = {
                tried = true
                if (valid) viewModel.register(SignUpForm(firstName, lastName, username, email, phone, province, city, password, photo))
            },
        )
        TextButton(onClick = onHaveAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_have_account))
        }
    }
}

@Composable
fun ResetPasswordScreen(onSignIn: () -> Unit, viewModel: ResetPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val problem = passwordProblem(password)
    AuthForm(title = stringResource(R.string.auth_reset_title)) {
        if (state.done) {
            Text(stringResource(R.string.auth_reset_done), modifier = Modifier.fillMaxWidth())
            SubmitButton(stringResource(R.string.auth_sign_in), busy = false, enabled = true, onClick = onSignIn)
            return@AuthForm
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_new_password)) },
            supportingText = { Text(problem?.takeIf { password.isNotEmpty() }?.let { stringResource(it) } ?: stringResource(R.string.auth_password_rule)) },
            isError = password.isNotEmpty() && problem != null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(R.string.auth_confirm_password)) },
            isError = confirm.isNotEmpty() && confirm != password,
            supportingText = if (confirm.isNotEmpty() && confirm != password) { { Text(stringResource(R.string.auth_password_mismatch)) } } else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { TokenError(it) }
        SubmitButton(
            text = stringResource(R.string.auth_reset_save),
            busy = state.busy,
            enabled = problem == null && confirm == password,
            onClick = { viewModel.reset(password) },
        )
    }
}

@Composable
fun ConfirmEmailScreen(onSignIn: () -> Unit, viewModel: ConfirmEmailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthForm(title = stringResource(R.string.auth_confirm_email_title)) {
        when {
            state.busy -> CircularProgressIndicator()
            state.done -> {
                Text(stringResource(R.string.auth_confirm_email_done), modifier = Modifier.fillMaxWidth())
                SubmitButton(stringResource(R.string.auth_sign_in), busy = false, enabled = true, onClick = onSignIn)
            }
            else -> state.error?.let { TokenError(it) }
        }
    }
}

/** An emailed link that is used up, run out or wrong reads as such, not as a raw code. */
@Composable
private fun TokenError(error: AppError) {
    val text = when ((error as? AppError.Api)?.code) {
        "AUTH_RESET_TOKEN_INVALID", "EMAIL_CHANGE_TOKEN_INVALID" -> stringResource(R.string.auth_link_invalid)
        else -> errorMessage(error)
    }
    Text(text, color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
}

/** The web's rules, in its order: length, a letter, a digit, a symbol. */
private fun passwordProblem(p: String): Int? = when {
    p.length < 8 -> R.string.auth_password_short
    p.none { it in 'a'..'z' || it in 'A'..'Z' } -> R.string.auth_password_letter
    p.none { it.isDigit() } -> R.string.auth_password_number
    p.all { it.isLetterOrDigit() } -> R.string.auth_password_special
    else -> null
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
private fun Field(value: String, onChange: (String) -> Unit, label: Int, type: KeyboardType = KeyboardType.Text, error: Boolean = false, hint: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error,
        supportingText = hint?.let { { Text(it) } },
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
