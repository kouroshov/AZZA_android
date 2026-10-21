package com.azza.feature.settings

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.azza.core.ui.isIranMobile
import com.azza.core.ui.ChoiceMenu
import androidx.activity.compose.BackHandler
import com.azza.core.ui.AppUnlock
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.common.AppError
import com.azza.core.data.ThemeChoice
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.errorMessage
import com.azza.core.ui.formatInstantDate
import com.azza.core.ui.loadingMoreFooter
import kotlinx.serialization.Serializable

/** The top bar's "more" menu (the web's settings popover), as a page. */
@Serializable data object SettingsRoute
@Serializable data object EditProfileRoute
@Serializable data object SupportRoute
@Serializable data object NewTicketRoute
@Serializable data class TicketRoute(val ticketId: String)

fun NavGraphBuilder.settingsScreens(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onEditProfile: () -> Unit,
    onSupport: () -> Unit,
    onNewTicket: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onTicketCreated: (String) -> Unit,
    onEmail: () -> Unit,
    onAddresses: () -> Unit,
    onEditAddress: (String?) -> Unit,
) {
    // Full screen, sliding in from the edge the ⋮ sits on (the end edge: right in English, left in Persian
    // and Kurdish) across to the other, and back out the same way.
    composable<SettingsRoute>(
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
    ) { SettingsScreen(onBack, onSignIn, onEditProfile, onSupport, onEmail, onAddresses) }
    composable<EmailRoute> { EmailScreen(onBack) }
    composable<AddressesRoute> { AddressesScreen(onBack, onEditAddress) }
    composable<AddressEditRoute> { AddressEditScreen(onBack) }
    composable<EditProfileRoute> { EditProfileScreen(onBack) }
    composable<SupportRoute> { SupportListScreen(onBack, onNewTicket, onOpenTicket) }
    composable<NewTicketRoute> { NewTicketScreen(onBack, onTicketCreated) }
    composable<TicketRoute> { TicketScreen(onBack) }
}

@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onEditProfile: () -> Unit,
    onSupport: () -> Unit,
    onEmail: () -> Unit,
    onAddresses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val phoneNotifications by viewModel.phoneNotifications.collectAsStateWithLifecycle()
    val appLock by viewModel.appLock.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lockAvailable = androidx.compose.runtime.remember { AppUnlock.available(context) }
    val promptTitle = stringResource(R.string.settings_app_lock_confirm)
    val promptCancel = stringResource(R.string.support_cancel)
    // The per-app language, as Android stores it (AppCompat keeps it below Android 13).
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    SettingsPage(stringResource(R.string.settings_title), onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            if (signedIn) {
                Row(stringResource(R.string.settings_edit_profile), Icons.Outlined.Edit, onEditProfile)
                Row(stringResource(R.string.account_email_title), Icons.Outlined.Email, onEmail)
                Row(stringResource(R.string.account_addresses_title), Icons.Outlined.Home, onAddresses)
                Row(stringResource(R.string.settings_support), Icons.AutoMirrored.Outlined.HelpOutline, onSupport)
                HorizontalDivider(color = AzzaTheme.colors.navDivider, modifier = Modifier.padding(vertical = 8.dp))
            }
            Section(stringResource(R.string.settings_language)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to R.string.settings_language_system, "en" to R.string.settings_language_en, "fa" to R.string.settings_language_fa, "ckb" to R.string.settings_language_ckb)
                        .forEach { (tag, label) ->
                            FilterChip(
                                selected = current == tag,
                                onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                }
            }
            Section(stringResource(R.string.settings_theme)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeChoice.DARK to R.string.settings_theme_dark, ThemeChoice.LIGHT to R.string.settings_theme_light, ThemeChoice.SYSTEM to R.string.settings_theme_system)
                        .forEach { (choice, label) ->
                            FilterChip(theme == choice, { viewModel.setTheme(choice) }, { Text(stringResource(label)) })
                        }
                }
            }
            if (signedIn) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable { viewModel.setPhoneNotifications(!phoneNotifications) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_phone_notifications), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.settings_phone_notifications_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                    }
                    Switch(checked = phoneNotifications, onCheckedChange = viewModel::setPhoneNotifications)
                }
                // The lock: switching it on or off is itself confirmed, so it cannot be turned off by whoever holds the open phone.
                if (lockAvailable || appLock) {
                    val toggle = { on: Boolean -> AppUnlock.prompt(context, promptTitle, promptCancel) { ok -> if (ok) viewModel.setAppLock(on) } }
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().clickable { toggle(!appLock) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_app_lock), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.settings_app_lock_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                        }
                        Switch(checked = appLock, onCheckedChange = { toggle(it) })
                    }
                }
            }
            HorizontalDivider(color = AzzaTheme.colors.navDivider, modifier = Modifier.padding(vertical = 8.dp))
            if (signedIn) {
                Row(stringResource(R.string.settings_sign_out), Icons.AutoMirrored.Outlined.Logout) { viewModel.signOut(); onBack() }
            } else {
                Button(onClick = onSignIn, modifier = Modifier.padding(16.dp).fillMaxWidth()) { Text(stringResource(R.string.settings_sign_in)) }
            }
        }
    }
}

@Composable
private fun Row(label: String, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AzzaTheme.colors.textSecondary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textSecondary)
        content()
    }
}

@Composable
fun EditProfileScreen(onBack: () -> Unit, viewModel: EditProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val provinces by viewModel.provinces.collectAsStateWithLifecycle()
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    var leaving by remember { mutableStateOf(false) }
    val tryBack = { if (viewModel.hasChanges()) leaving = true else onBack() }
    BackHandler(enabled = viewModel.hasChanges()) { leaving = true }
    if (leaving) DiscardDialog(onKeep = { leaving = false }, onDiscard = { leaving = false; onBack() })
    SettingsPage(stringResource(R.string.settings_edit_profile), tryBack) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IdentitySection()
            HorizontalDivider(color = AzzaTheme.colors.navDivider)
            Field(state.firstName, { v -> viewModel.edit { copy(firstName = v.take(60)) } }, R.string.settings_first_name)
            Field(state.lastName, { v -> viewModel.edit { copy(lastName = v.take(60)) } }, R.string.settings_last_name)
            val bioLength = state.bio.codePointCount(0, state.bio.length)
            OutlinedTextField(
                value = state.bio,
                onValueChange = { v -> if (v.codePointCount(0, v.length) <= BIO_MAX) viewModel.edit { copy(bio = v) } },
                label = { Text(stringResource(R.string.settings_bio)) },
                supportingText = { Text("$bioLength / $BIO_MAX") },
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                modifier = Modifier.fillMaxWidth(),
            )
            val phoneBad = state.phone.isNotBlank() && !isIranMobile(state.phone)
            OutlinedTextField(
                value = state.phone,
                onValueChange = { v -> viewModel.edit { copy(phone = v.take(14)) } },
                label = { Text(stringResource(R.string.settings_phone)) },
                isError = phoneBad,
                supportingText = if (phoneBad) { { Text(stringResource(R.string.settings_phone_invalid)) } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceMenu(stringResource(R.string.settings_province), state.province, provinces.map { it.name }, viewModel::chooseProvince, Modifier.fillMaxWidth())
            ChoiceMenu(stringResource(R.string.settings_city), state.city, cities.map { it.name }, { c -> viewModel.edit { copy(city = c) } }, Modifier.fillMaxWidth())
            state.error?.let { Text(errorMessage(it), color = AzzaTheme.colors.error) }
            Button(onClick = viewModel::save, enabled = !state.saving && state.firstName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_save))
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, minLines: Int = 1) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(stringResource(label)) },
    singleLine = minLines == 1,
    minLines = minLines,
    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
    modifier = Modifier.fillMaxWidth(),
)

@Composable
fun SupportListScreen(onBack: () -> Unit, onNewTicket: () -> Unit, onOpenTicket: (String) -> Unit, viewModel: SupportListViewModel = hiltViewModel()) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.pager.refresh(showSpinner = true)
        onPauseOrDispose { }
    }
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, viewModel.pager::loadMore)
    SettingsPage(stringResource(R.string.settings_support), onBack) {
        Column(Modifier.fillMaxSize()) {
            Text(stringResource(R.string.support_intro), color = AzzaTheme.colors.textSecondary, modifier = Modifier.padding(16.dp))
            Button(onClick = onNewTicket, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Text(stringResource(R.string.support_new_ticket)) }
            when {
                state.loadingFirst -> LoadingState()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { viewModel.pager.refresh() })
                state.items.isEmpty() -> EmptyState(stringResource(R.string.support_empty))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(state.items, key = { it.id }) { t ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenTicket(t.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("#${t.number} · ${t.subject}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    statusLabel(t.status),
                                    stringResource(R.string.support_new_reply).takeIf { t.awaitingYou },
                                    formatInstantDate(t.lastMessageAt),
                                ).joinToString(" · "),
                                color = if (t.awaitingYou) AzzaTheme.colors.primary else AzzaTheme.colors.textMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider(color = AzzaTheme.colors.navDivider)
                    }
                    loadingMoreFooter(state.loadingMore)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTicketScreen(onBack: () -> Unit, onCreated: (String) -> Unit, viewModel: NewTicketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    SettingsPage(stringResource(R.string.support_new_ticket_title), onBack) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.support_category), style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TICKET_CATEGORIES.forEach { c -> FilterChip(state.category == c, { viewModel.edit { copy(category = c) } }, { Text(categoryLabel(c)) }) }
            }
            Field(state.subject, { v -> viewModel.edit { copy(subject = v.take(150)) } }, R.string.support_subject)
            Field(state.body, { v -> viewModel.edit { copy(body = v.take(4000)) } }, R.string.support_body, minLines = 5)
            Text(stringResource(R.string.support_body_hint), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(ticketError(it), color = AzzaTheme.colors.error) }
            Button(
                onClick = viewModel::send,
                enabled = !state.sending && state.subject.trim().length >= 3 && state.body.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (state.sending) R.string.support_sending else R.string.support_send)) }
        }
    }
}

@Composable
fun TicketScreen(onBack: () -> Unit, viewModel: TicketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClose by remember { mutableStateOf(false) }
    val ticket = state.ticket
    SettingsPage(ticket?.let { "#${it.number} · ${it.subject}" }.orEmpty(), onBack) {
        when {
            state.loading -> LoadingState()
            ticket == null -> ErrorState(state.error ?: AppError.Network, onRetry = { viewModel.load() })
            else -> Column(Modifier.fillMaxSize().imePadding()) {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(statusHint(ticket.status), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(ticket.messages, key = { it.id }) { m ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromSupport) Alignment.CenterStart else Alignment.CenterEnd) {
                            Column(
                                Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (m.fromSupport) AzzaTheme.colors.surfaceRaised else AzzaTheme.colors.primary.copy(alpha = 0.18f))
                                    .padding(12.dp),
                            ) {
                                Text(stringResource(if (m.fromSupport) R.string.support_from_support else R.string.support_from_you), style = MaterialTheme.typography.labelSmall, color = AzzaTheme.colors.textMuted)
                                Text(m.body, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                                Text(formatInstantDate(m.createdAt), style = MaterialTheme.typography.labelSmall, color = AzzaTheme.colors.textMuted)
                            }
                        }
                    }
                }
                state.actionError?.let { Text(ticketError(it), color = AzzaTheme.colors.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                if (ticket.closed) {
                    Text(stringResource(R.string.support_closed_note), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(16.dp))
                } else {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.reply,
                            onValueChange = viewModel::editReply,
                            label = { Text(stringResource(R.string.support_reply)) },
                            minLines = 2,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::send, enabled = !state.sending && state.reply.isNotBlank()) { Text(stringResource(R.string.support_send)) }
                            OutlinedButton(onClick = { confirmClose = true }, enabled = !state.sending) { Text(stringResource(R.string.support_close_ticket)) }
                        }
                    }
                }
            }
        }
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            text = { Text(stringResource(R.string.support_close_confirm)) },
            confirmButton = { TextButton(onClick = { confirmClose = false; viewModel.close() }) { Text(stringResource(R.string.support_close_yes)) } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.support_cancel)) } },
        )
    }
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        "OPEN" -> R.string.support_status_open
        "PENDING_USER" -> R.string.support_status_pending_user
        "RESOLVED" -> R.string.support_status_resolved
        else -> R.string.support_status_closed
    },
)

@Composable
private fun statusHint(status: String): String = stringResource(
    when (status) {
        "OPEN" -> R.string.support_hint_open
        "PENDING_USER" -> R.string.support_hint_pending_user
        "RESOLVED" -> R.string.support_hint_resolved
        else -> R.string.support_hint_closed
    },
)

@Composable
private fun categoryLabel(c: String): String = stringResource(
    when (c) {
        "ACCOUNT" -> R.string.support_cat_account
        "ORDER" -> R.string.support_cat_order
        "PAYMENT" -> R.string.support_cat_payment
        "LISTING" -> R.string.support_cat_listing
        "STORE" -> R.string.support_cat_store
        "ABUSE" -> R.string.support_cat_abuse
        else -> R.string.support_cat_other
    },
)

@Composable
private fun ticketError(e: AppError): String = when ((e as? AppError.Api)?.code) {
    "TICKET_CLOSED" -> stringResource(R.string.support_error_closed)
    "TICKET_LIMIT_REACHED" -> stringResource(R.string.support_error_limit)
    else -> errorMessage(e)
}

/** "Leave without saving?" — what the web's unsaved-changes guard asks. */
@Composable
internal fun DiscardDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.settings_discard_title)) },
        text = { Text(stringResource(R.string.settings_discard_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.settings_discard), color = AzzaTheme.colors.error) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.settings_keep_editing)) } },
    )
}
