package com.azza.feature.stores

import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.common.AppError
import com.azza.core.data.Appeal
import com.azza.core.data.ManagedStore
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.Avatar
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.errorMessage
import com.azza.core.ui.formatInstantDate
import com.azza.core.ui.handle
import com.azza.core.ui.loadingMoreFooter
import kotlinx.serialization.Serializable

@Serializable data object MyStoresRoute
@Serializable data object CreateStoreRoute
@Serializable data class StoreEditRoute(val storeId: String)
@Serializable data class StoreHomeRoute(val storeId: String)
@Serializable data class StoreFollowersRoute(val storeId: String)
@Serializable data class BranchRoute(val storeId: String, val branchId: String)
@Serializable data class BranchStockRoute(val storeId: String, val branchId: String)
@Serializable data class BranchLocationRoute(val storeId: String, val branchId: String)
@Serializable data class TimetableRoute(val storeId: String)
/** A calendar's week of appointments; [storeId] null = the personal calendar. */
@Serializable data class CalendarWeekRoute(val storeId: String?)
@Serializable data class StoreProductsRoute(val storeId: String)
@Serializable data class BookingSettingsRoute(val productId: String)

/** Where the store screens lead outside this module (the app wires them). */
class StoreNavigation(
    val back: () -> Unit,
    val open: (Any) -> Unit,
    /** After a create: the new store replaces the form. */
    val openReplacingForm: (Any) -> Unit,
    val openPublicStore: (String) -> Unit,
    val openUser: (String) -> Unit,
    val openListing: (String) -> Unit,
    val postFromStore: (String) -> Unit,
    val editListing: (String) -> Unit = openListing,
)

fun NavGraphBuilder.storeScreens(nav: StoreNavigation) {
    composable<MyStoresRoute> { MyStoresScreen(nav) }
    composable<CreateStoreRoute> { StoreFormScreen(nav) }
    composable<StoreEditRoute> { StoreFormScreen(nav) }
    composable<StoreHomeRoute> { StoreHomeScreen(nav) }
    composable<StoreFollowersRoute> { StoreFollowersScreen(nav) }
    composable<BranchRoute> { BranchScreen(nav) }
    composable<BranchStockRoute> { StockScreen(nav) }
    composable<BranchLocationRoute> { LocationScreen(nav) }
    composable<TimetableRoute> { TimetableScreen(nav) }
    composable<CalendarWeekRoute> { CalendarWeekScreen(nav) }
    composable<StoreProductsRoute> { StoreProductsScreen(nav) }
    composable<BookingSettingsRoute> { BookingSettingsScreen(nav) }
    composable<RestaurantRoute> { RestaurantScreen(nav) }
    composable<DispatchRoute> { DispatchScreen(nav) }
}

@Composable
internal fun StorePage(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.stores_back)) } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

@Composable
internal fun language(): String = LocalConfiguration.current.locales[0].language

/** The errors these screens can meet, in words; the rest through the shared table. */
@Composable
internal fun storeError(e: AppError): String = when ((e as? AppError.Api)?.code) {
    "SELLER_SUBSCRIPTION_REQUIRED" -> stringResource(R.string.stores_need_subscription)
    "STORE_SLUG_CONFLICT", "USERNAME_TAKEN" -> stringResource(R.string.stores_handle_taken)
    "STORE_MODERATED" -> stringResource(R.string.stores_error_moderated)
    "HOURS_ORDER" -> stringResource(R.string.stores_error_hours)
    "APPEAL_DUPLICATE" -> stringResource(R.string.stores_appeal_duplicate)
    "STORE_LOCATION_OUTSIDE_AREA" -> stringResource(R.string.stores_error_outside_city)
    else -> errorMessage(e)
}

// ---------------------------------------------------------------- my stores

@Composable
fun MyStoresScreen(nav: StoreNavigation, viewModel: MyStoresViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }
    StorePage(stringResource(R.string.stores_title), nav.back) {
        when (val s = state.stores) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(s.error, onRetry = { viewModel.load() })
            is Load.Ready -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.stores_intro), color = AzzaTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        when (state.canCreate) {
                            true -> Button(onClick = { nav.open(CreateStoreRoute) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.AddBusiness, null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.stores_create))
                            }
                            false -> Text(stringResource(R.string.stores_need_subscription), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                            null -> Unit
                        }
                    }
                }
                if (s.value.isEmpty()) item { EmptyState(stringResource(R.string.stores_none)) }
                items(s.value, key = { it.id }) { store ->
                    StoreRow(store) { nav.open(StoreHomeRoute(store.id)) }
                    HorizontalDivider(color = AzzaTheme.colors.navDivider)
                }
            }
        }
    }
}

@Composable
private fun StoreRow(store: ManagedStore, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(url = store.avatarUrl, isStore = true, size = 48)
        Column(Modifier.weight(1f)) {
            Text(store.name, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    handle(store.slug),
                    stringResource(R.string.stores_posts_short, store.postCount),
                    stringResource(R.string.stores_suspended).takeIf { store.hold != null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (store.hold != null) AzzaTheme.colors.error else AzzaTheme.colors.textMuted,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = AzzaTheme.colors.textMuted)
    }
}

// ---------------------------------------------------------------- create / edit

@Composable
fun StoreFormScreen(nav: StoreNavigation, viewModel: StoreFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) {
        val id = state.savedId ?: return@LaunchedEffect
        if (viewModel.isNew) nav.openReplacingForm(StoreHomeRoute(id)) else nav.back()
    }
    val lang = language()
    StorePage(stringResource(if (viewModel.isNew) R.string.stores_create else R.string.stores_edit), nav.back) {
        if (state.loading) {
            LoadingState()
            return@StorePage
        }
        val d = state.draft
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(d.name, { v -> viewModel.edit { copy(name = v.take(200)) } }, R.string.stores_name)
            OutlinedTextField(
                value = d.handle,
                onValueChange = { v -> viewModel.edit { copy(handle = v.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(30)) } },
                label = { Text(stringResource(R.string.stores_handle)) },
                prefix = { Text("@") },
                supportingText = { Text(stringResource(if (viewModel.isNew) R.string.stores_handle_hint_new else R.string.stores_handle_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // What the store does (the web's occupation picker): search, then pick one.
            Text(stringResource(R.string.stores_occupation), style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textSecondary)
            d.occupation?.let { o ->
                AssistChip(onClick = { viewModel.chooseOccupation(null) }, label = { Text(o.name(lang) + "  ✕") })
            }
            OutlinedTextField(
                value = state.occupationQuery,
                onValueChange = viewModel::searchOccupation,
                placeholder = { Text(stringResource(R.string.stores_occupation_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.occupationResults.forEach { o ->
                Text(
                    o.name(lang),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.chooseOccupation(o) }.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
            FormField(d.phone, { v -> viewModel.edit { copy(phone = v.take(30)) } }, R.string.stores_phone, KeyboardType.Phone)
            FormField(d.description, { v -> viewModel.edit { copy(description = v.take(10_000)) } }, R.string.stores_description, minLines = 4)
            state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = viewModel::save, enabled = !state.saving && d.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (viewModel.isNew) R.string.stores_create_submit else R.string.stores_save))
            }
        }
    }
}

@Composable
internal fun FormField(value: String, onChange: (String) -> Unit, label: Int, keyboard: KeyboardType = KeyboardType.Text, minLines: Int = 1) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(stringResource(label)) },
    singleLine = minLines == 1,
    minLines = minLines,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
    modifier = Modifier.fillMaxWidth(),
)

// ---------------------------------------------------------------- one store (its owner's page)

@Composable
fun StoreHomeScreen(nav: StoreNavigation, viewModel: StoreHomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }
    var addingBranch by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.setPhoto(it.toString()) } }
    val store = (state.store as? Load.Ready)?.value
    StorePage(store?.name.orEmpty(), nav.back) {
        when (val s = state.store) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(s.error, onRetry = { viewModel.load() })
            is Load.Ready -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val st = s.value
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Avatar(url = st.avatarUrl, isStore = true, size = 72)
                        if (state.photoBusy) CircularProgressIndicator()
                    }
                    Column {
                        Text(handle(st.slug), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.stores_followers_count, st.followerCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = AzzaTheme.colors.textSecondary,
                            modifier = Modifier.clickable { nav.open(StoreFollowersRoute(st.id)) },
                        )
                        Row {
                            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.photoBusy) {
                                Text(stringResource(R.string.stores_change_photo))
                            }
                            if (st.avatarUrl != null) TextButton(onClick = viewModel::removePhoto, enabled = !state.photoBusy) { Text(stringResource(R.string.stores_remove_photo)) }
                        }
                    }
                }
                st.hold?.let { HoldCard(it.cause, it.reason, it.at, it.appealable, state.appeals, state.appealSending, state.appealError, viewModel::appeal) }
                state.actionError?.let { Text(storeError(it), color = AzzaTheme.colors.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                val main = state.branches.firstOrNull { it.id == st.primaryBranchId } ?: state.branches.firstOrNull()
                val noHours = main != null && main.week.all { it.closed }
                if ((!st.hasLocation || noHours) && st.hold == null && main != null) {
                    // A new store is not findable until it has a place and hours: say so, with the way there.
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)).background(AzzaTheme.colors.surface).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.stores_setup_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.stores_setup_body), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!st.hasLocation) OutlinedButton(onClick = { nav.open(BranchLocationRoute(st.id, main.id)) }) { Text(stringResource(R.string.stores_set_location)) }
                            if (noHours) OutlinedButton(onClick = { nav.open(BranchRoute(st.id, main.id)) }) { Text(stringResource(R.string.stores_set_hours)) }
                        }
                    }
                }
                HorizontalDivider(color = AzzaTheme.colors.navDivider, modifier = Modifier.padding(vertical = 8.dp))
                ActionRow(Icons.Outlined.PostAdd, stringResource(R.string.stores_post)) { nav.postFromStore(st.id) }
                ActionRow(Icons.Outlined.Inventory2, stringResource(R.string.stores_products_count, st.postCount)) { nav.open(StoreProductsRoute(st.id)) }
                ActionRow(Icons.Outlined.Storefront, stringResource(R.string.stores_public_page)) { nav.openPublicStore(st.slug) }
                ActionRow(Icons.Outlined.Edit, stringResource(R.string.stores_edit)) { nav.open(StoreEditRoute(st.id)) }
                // ✅ (2026-10-23, restaurants) delivery terms, "not taking orders", and the menu's headings.
                ActionRow(Icons.Outlined.Restaurant, stringResource(R.string.restaurant_settings_title)) { nav.open(RestaurantRoute(st.id)) }
                // ✅ (2026-10-25, shops) working days to send an order; a restaurant has its delivery time instead.
                if (st.restaurant == null) {
                    ActionRow(Icons.Outlined.LocalShipping, stringResource(R.string.shop_dispatch_title)) { nav.open(DispatchRoute(st.id)) }
                }
                ActionRow(Icons.Outlined.CalendarMonth, stringResource(R.string.stores_timetable)) { nav.open(TimetableRoute(st.id)) }
                ActionRow(Icons.Outlined.EventNote, stringResource(R.string.stores_appointments_week)) { nav.open(CalendarWeekRoute(st.id)) }
                ActionRow(Icons.Outlined.Group, stringResource(R.string.stores_followers)) { nav.open(StoreFollowersRoute(st.id)) }
                HorizontalDivider(color = AzzaTheme.colors.navDivider, modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stores_branches), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addingBranch = true }) { Text(stringResource(R.string.stores_add_branch)) }
                }
                state.branches.forEach { b ->
                    Column(
                        Modifier.fillMaxWidth().clickable { nav.open(BranchRoute(st.id, b.id)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(b.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(
                                openStatusLabel(b.openStatus),
                                stringResource(R.string.stores_branch_inactive).takeIf { b.status != "ACTIVE" },
                                b.location?.address?.takeIf { it.isNotBlank() } ?: stringResource(R.string.stores_branch_no_location),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = AzzaTheme.colors.textMuted,
                        )
                    }
                }
                Box(Modifier.padding(bottom = 24.dp))
            }
        }
    }
    if (addingBranch) NewBranchDialog(saving = state.newBranchSaving, onDismiss = { addingBranch = false }) { name, phone ->
        viewModel.addBranch(name, phone) { id ->
            addingBranch = false
            nav.open(BranchRoute(viewModel.storeId, id))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AzzaTheme.colors.textSecondary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun openStatusLabel(status: String): String? = when (status) {
    "OPEN" -> stringResource(R.string.stores_open_now)
    "CLOSED" -> stringResource(R.string.stores_closed_now)
    else -> null
}

@Composable
private fun NewBranchDialog(saving: Boolean, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_add_branch)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it.take(120) }, R.string.stores_branch_name)
                FormField(phone, { phone = it.take(30) }, R.string.stores_phone, KeyboardType.Phone)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, phone) }, enabled = name.isNotBlank() && !saving) { Text(stringResource(R.string.stores_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stores_cancel)) } },
    )
}

/** The web's store-held notice and AppealPanel (target STORE): only an admin's own decision can be appealed. */
@Composable
private fun HoldCard(
    cause: String,
    reason: String?,
    at: String,
    appealable: Boolean,
    appeals: List<Appeal>,
    sending: Boolean,
    error: AppError?,
    onAppeal: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp))
            .background(AzzaTheme.colors.error.copy(alpha = 0.10f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(if (cause == "OWNER_SUSPENDED") R.string.stores_held_owner else R.string.stores_held_admin),
            style = MaterialTheme.typography.titleSmall,
            color = AzzaTheme.colors.error,
        )
        Text(stringResource(R.string.stores_held_note), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
        reason?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.stores_held_reason, it), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)) }
        if (!appealable) return@Column
        val current = appeals.firstOrNull { it.holdAt == at }
        when (current?.status) {
            "OPEN" -> Text(stringResource(R.string.stores_appeal_waiting, formatInstantDate(current.createdAt)), style = MaterialTheme.typography.bodySmall)
            "REJECTED" -> {
                Text(stringResource(R.string.stores_appeal_rejected, formatInstantDate(current.decidedAt ?: at)), style = MaterialTheme.typography.bodySmall)
                current.decisionNote?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.stores_appeal_answer, it), style = MaterialTheme.typography.bodySmall) }
            }
            null -> {
                var open by rememberSaveable { mutableStateOf(false) }
                var text by rememberSaveable { mutableStateOf("") }
                if (!open) {
                    OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.stores_appeal_button)) }
                } else {
                    Text(stringResource(R.string.stores_appeal_one_chance), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(1000) },
                        label = { Text(stringResource(R.string.stores_appeal_label)) },
                        supportingText = { Text("${text.trim().length} / 1000") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { onAppeal(text) }, enabled = text.trim().length >= 20 && !sending) { Text(stringResource(R.string.stores_appeal_send)) }
                }
            }
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------- followers (owner-only list)

@Composable
fun StoreFollowersScreen(nav: StoreNavigation, viewModel: StoreFollowersViewModel = hiltViewModel()) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, viewModel.pager::loadMore)
    StorePage(stringResource(R.string.stores_followers), nav.back) {
        when {
            state.loadingFirst -> LoadingState()
            state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { viewModel.pager.refresh() })
            state.items.isEmpty() -> EmptyState(stringResource(R.string.stores_followers_empty))
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { f.username?.let(nav.openUser) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(url = null, size = 40)
                        Column {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                            Text(handle(f.username.orEmpty()), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                        }
                    }
                    HorizontalDivider(color = AzzaTheme.colors.navDivider)
                }
                loadingMoreFooter(state.loadingMore)
            }
        }
    }
}
