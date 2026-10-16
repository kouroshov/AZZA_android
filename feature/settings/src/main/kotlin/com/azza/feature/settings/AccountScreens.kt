package com.azza.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.Address
import com.azza.core.data.AddressDraft
import com.azza.core.data.MyAccountRepository
import com.azza.core.data.PendingEmailChange
import com.azza.core.data.UserRepository
import com.azza.core.data.UsernameCheck
import com.azza.core.data.toDraft
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.User
import com.azza.core.ui.Avatar
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import com.azza.core.ui.errorMessage
import com.azza.core.ui.formatInstantDate
import com.azza.core.ui.formatWhen
import com.azza.core.ui.handle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object EmailRoute
@Serializable data object AddressesRoute
@Serializable data class AddressEditRoute(val addressId: String? = null)

// ---------------------------------------------------------------- photo + username

data class IdentityState(
    val photoBusy: Boolean = false,
    val photoError: AppError? = null,
    val username: String = "",
    val check: UsernameCheck? = null,
    val checking: Boolean = false,
    val renaming: Boolean = false,
    val renameError: AppError? = null,
    val renamed: Boolean = false,
)

/** The photo and the username: each saved on its own, at once — not with the name/bio form. */
@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val account: MyAccountRepository,
    users: UserRepository,
) : ViewModel() {
    val me: StateFlow<User?> = users.currentUser
    private val _state = MutableStateFlow(IdentityState(username = users.currentUser.value?.username.orEmpty()))
    val state: StateFlow<IdentityState> = _state.asStateFlow()
    private var checkJob: Job? = null

    fun setPhoto(uri: String) = photo { account.uploadAvatar(uri) }

    fun removePhoto() = photo { account.removeAvatar() }

    private fun photo(call: suspend () -> Outcome<Unit>) {
        if (_state.value.photoBusy) return
        _state.update { it.copy(photoBusy = true, photoError = null) }
        viewModelScope.launch {
            val r = call()
            _state.update { it.copy(photoBusy = false, photoError = (r as? Outcome.Failure)?.error) }
        }
    }

    /** Usernames are lowercase; apps/api is asked half a second after the typing stops. */
    fun editUsername(text: String) {
        val value = text.trim().lowercase()
        _state.update { it.copy(username = value, check = null, renameError = null, renamed = false) }
        checkJob?.cancel()
        if (value.isEmpty() || value == me.value?.username) return
        checkJob = viewModelScope.launch {
            delay(500)
            _state.update { it.copy(checking = true) }
            val r = account.checkUsername(value)
            _state.update { it.copy(checking = false, check = (r as? Outcome.Success)?.value ?: UsernameCheck.UNKNOWN) }
        }
    }

    fun rename() {
        val s = _state.value
        if (s.renaming || s.check != UsernameCheck.AVAILABLE) return
        _state.update { it.copy(renaming = true, renameError = null) }
        viewModelScope.launch {
            when (val r = account.renameUsername(s.username)) {
                is Outcome.Success -> _state.update { it.copy(renaming = false, renamed = true, check = null) }
                is Outcome.Failure -> _state.update { it.copy(renaming = false, renameError = r.error) }
            }
        }
    }
}

/**
 * apps/api allows a new username 30 days after the last change. Returns
 * when (ISO-8601, UTC), or null when a change is allowed now. java.util,
 * not java.time: the app runs from Android 7, before java.time.
 */
internal fun nextUsernameChange(changedAt: String?, now: Long = System.currentTimeMillis()): String? {
    val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val last = changedAt?.let { runCatching { iso.parse(it.take(19)) }.getOrNull() } ?: return null
    val next = last.time + 30L * 24 * 60 * 60 * 1000
    return if (next > now) iso.format(java.util.Date(next)) + "Z" else null
}

@Composable
internal fun IdentitySection(viewModel: IdentityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.setPhoto(it.toString()) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Avatar(url = me?.avatarUrl, size = 72)
                if (state.photoBusy) CircularProgressIndicator()
            }
            Column {
                TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.photoBusy) {
                    Text(stringResource(R.string.account_change_photo))
                }
                if (me?.avatarUrl != null) {
                    TextButton(onClick = viewModel::removePhoto, enabled = !state.photoBusy) { Text(stringResource(R.string.account_remove_photo)) }
                }
            }
        }
        state.photoError?.let { Text(errorMessage(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider(color = AzzaTheme.colors.navDivider)
        val next = nextUsernameChange(me?.usernameChangedAt)
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::editUsername,
            label = { Text(stringResource(R.string.account_username)) },
            prefix = { Text("@") },
            singleLine = true,
            enabled = next == null,
            supportingText = {
                Text(
                    when {
                        next != null -> stringResource(R.string.account_username_next, formatInstantDate(next))
                        state.renamed -> stringResource(R.string.account_username_done, handle(me?.username.orEmpty()))
                        state.checking -> stringResource(R.string.account_username_checking)
                        state.check == UsernameCheck.AVAILABLE -> stringResource(R.string.account_username_available)
                        state.check == UsernameCheck.TAKEN -> stringResource(R.string.account_username_taken)
                        state.check == UsernameCheck.RESERVED -> stringResource(R.string.account_username_reserved)
                        state.check == UsernameCheck.INVALID -> stringResource(R.string.account_username_invalid)
                        else -> stringResource(R.string.account_username_rule)
                    },
                )
            },
            isError = state.check in setOf(UsernameCheck.TAKEN, UsernameCheck.RESERVED, UsernameCheck.INVALID),
            modifier = Modifier.fillMaxWidth(),
        )
        state.renameError?.let { Text(usernameError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        if (state.check == UsernameCheck.AVAILABLE) {
            OutlinedButton(onClick = viewModel::rename, enabled = !state.renaming) { Text(stringResource(R.string.account_username_change)) }
        }
    }
}

@Composable
private fun usernameError(e: AppError): String = when ((e as? AppError.Api)?.code) {
    "USERNAME_CHANGE_TOO_SOON" -> stringResource(R.string.account_username_too_soon)
    "USERNAME_TAKEN" -> stringResource(R.string.account_username_taken)
    else -> errorMessage(e)
}

// ---------------------------------------------------------------- email address

data class EmailState(
    val loading: Boolean = true,
    val pending: PendingEmailChange? = null,
    val newEmail: String = "",
    val busy: Boolean = false,
    val sent: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class EmailViewModel @Inject constructor(private val account: MyAccountRepository, users: UserRepository) : ViewModel() {
    val me: StateFlow<User?> = users.currentUser
    private val _state = MutableStateFlow(EmailState())
    val state: StateFlow<EmailState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        val r = account.pendingEmailChange()
        _state.update { it.copy(loading = false, pending = (r as? Outcome.Success)?.value, error = (r as? Outcome.Failure)?.error) }
    }

    fun edit(text: String) = _state.update { it.copy(newEmail = text, error = null, sent = false) }

    /** Answered the same whether or not the address is free (no enumeration); the link goes to the new address. */
    fun request() = act { account.requestEmailChange(_state.value.newEmail) }

    fun cancel() = act { account.cancelEmailChange() }

    private fun act(call: suspend () -> Outcome<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = call()
            val pending = (account.pendingEmailChange() as? Outcome.Success)?.value
            _state.update {
                it.copy(
                    busy = false,
                    pending = pending,
                    error = (r as? Outcome.Failure)?.error,
                    sent = r is Outcome.Success && pending != null,
                    newEmail = if (r is Outcome.Success) "" else it.newEmail,
                )
            }
        }
    }
}

@Composable
fun EmailScreen(onBack: () -> Unit, viewModel: EmailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }
    SettingsPage(stringResource(R.string.account_email_title), onBack) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.account_email_current), style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textSecondary)
            Text(me?.email.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider(color = AzzaTheme.colors.navDivider)
            val pending = state.pending
            if (pending != null) {
                Text(stringResource(R.string.account_email_pending, pending.newEmail, formatWhen(pending.expiresAt, java.util.TimeZone.getDefault().id)), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = viewModel::cancel, enabled = !state.busy) { Text(stringResource(R.string.account_email_cancel)) }
            } else if (!state.loading) {
                Text(stringResource(R.string.account_email_explain), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                OutlinedTextField(
                    value = state.newEmail,
                    onValueChange = viewModel::edit,
                    label = { Text(stringResource(R.string.account_email_new)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::request,
                    enabled = !state.busy && state.newEmail.contains('@') && state.newEmail.trim() != me?.email,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.account_email_send)) }
            }
            if (state.sent) Text(stringResource(R.string.account_email_sent), color = AzzaTheme.colors.primary, style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(errorMessage(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------- delivery addresses

sealed interface AddressesUi {
    data object Loading : AddressesUi
    data class Ready(val items: List<Address>) : AddressesUi
    data class Failed(val error: AppError) : AddressesUi
}

@HiltViewModel
class AddressesViewModel @Inject constructor(private val account: MyAccountRepository) : ViewModel() {
    private val _state = MutableStateFlow<AddressesUi>(AddressesUi.Loading)
    val state: StateFlow<AddressesUi> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.value = when (val r = account.addresses()) {
            is Outcome.Success -> AddressesUi.Ready(r.value)
            is Outcome.Failure -> AddressesUi.Failed(r.error)
        }
    }
}

@Composable
fun AddressesScreen(onBack: () -> Unit, onEdit: (String?) -> Unit, viewModel: AddressesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }
    SettingsPage(stringResource(R.string.account_addresses_title), onBack) {
        when (val s = state) {
            AddressesUi.Loading -> LoadingState()
            is AddressesUi.Failed -> ErrorState(s.error, onRetry = { viewModel.load() })
            is AddressesUi.Ready -> if (s.items.isEmpty()) {
                EmptyState(stringResource(R.string.account_addresses_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(s.items, key = { it.id }) { a ->
                        Column(Modifier.fillMaxWidth().clickable { onEdit(a.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                listOfNotNull(a.label?.takeIf { it.isNotBlank() } ?: a.recipientName, stringResource(R.string.account_address_default).takeIf { a.isDefault })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(a.oneLine, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = AzzaTheme.colors.textSecondary)
                        }
                        HorizontalDivider(color = AzzaTheme.colors.navDivider)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { onEdit(null) }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Outlined.Add, stringResource(R.string.account_address_add))
        }
    }
}

data class AddressEditState(
    val loading: Boolean = false,
    val draft: AddressDraft = AddressDraft(),
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class AddressEditViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val account: MyAccountRepository) : ViewModel() {
    private val id = savedStateHandle.toRoute<AddressEditRoute>().addressId
    val isNew = id == null
    private val _state = MutableStateFlow(AddressEditState(loading = id != null))
    val state: StateFlow<AddressEditState> = _state.asStateFlow()

    init {
        if (id != null) viewModelScope.launch {
            val found = (account.addresses() as? Outcome.Success)?.value?.firstOrNull { it.id == id }
            _state.update { it.copy(loading = false, draft = found?.toDraft() ?: it.draft) }
        }
    }

    fun edit(transform: AddressDraft.() -> AddressDraft) = _state.update { it.copy(draft = it.draft.transform(), error = null) }

    fun save() = act { account.saveAddress(_state.value.draft) }

    fun delete() = act { id?.let { account.deleteAddress(it) } ?: Outcome.Success(Unit) }

    private fun act(call: suspend () -> Outcome<Unit>) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is Outcome.Success -> _state.update { it.copy(saving = false, done = true) }
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }
}

@Composable
fun AddressEditScreen(onBack: () -> Unit, viewModel: AddressEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onBack() }
    SettingsPage(stringResource(if (viewModel.isNew) R.string.account_address_add else R.string.account_address_edit), onBack) {
        if (state.loading) {
            LoadingState()
            return@SettingsPage
        }
        val d = state.draft
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AddressField(d.label, { v -> viewModel.edit { copy(label = v) } }, R.string.account_address_label)
            AddressField(d.recipientName, { v -> viewModel.edit { copy(recipientName = v) } }, R.string.account_address_recipient)
            AddressField(d.phone, { v -> viewModel.edit { copy(phone = v) } }, R.string.account_address_phone, KeyboardType.Phone)
            AddressField(d.line1, { v -> viewModel.edit { copy(line1 = v) } }, R.string.account_address_line1)
            AddressField(d.line2, { v -> viewModel.edit { copy(line2 = v) } }, R.string.account_address_line2)
            AddressField(d.city, { v -> viewModel.edit { copy(city = v) } }, R.string.account_address_city)
            AddressField(d.region, { v -> viewModel.edit { copy(region = v) } }, R.string.account_address_region)
            AddressField(d.postalCode, { v -> viewModel.edit { copy(postalCode = v) } }, R.string.account_address_postal, KeyboardType.Number)
            Row(Modifier.fillMaxWidth().clickable { viewModel.edit { copy(isDefault = !isDefault) } }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = d.isDefault, onCheckedChange = { v -> viewModel.edit { copy(isDefault = v) } })
                Text(stringResource(R.string.account_address_make_default))
            }
            state.error?.let { Text(errorMessage(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = viewModel::save, enabled = d.complete && !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_save)) }
            if (!viewModel.isNew) {
                TextButton(onClick = { confirmDelete = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_address_delete), color = AzzaTheme.colors.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.account_address_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.account_address_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.support_cancel)) } },
        )
    }
}

@Composable
private fun AddressField(value: String, onChange: (String) -> Unit, label: Int, keyboard: KeyboardType = KeyboardType.Text) = OutlinedTextField(
    value = value,
    onValueChange = { onChange(it.take(200)) },
    label = { Text(stringResource(label)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
    modifier = Modifier.fillMaxWidth(),
)
