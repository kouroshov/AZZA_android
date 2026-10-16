package com.azza.feature.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil.compose.AsyncImage
import com.azza.core.common.Outcome
import com.azza.core.data.Branch
import com.azza.core.data.MapRepository
import com.azza.core.data.StoresRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.data.BranchPreview
import com.azza.core.model.OpenHours
import com.azza.core.ui.LocalWebLinks
import com.azza.core.ui.LoadingState
import com.azza.core.ui.clock
import com.azza.core.ui.dayName
import com.azza.core.ui.format
import com.azza.core.ui.shareLink
import com.azza.core.ui.todayIndex
import com.azza.core.ui.weekOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- the store page's own sections

/** OPEN / CLOSED / UNKNOWN as a coloured word, or nothing when the store has published no hours. */
@Composable
internal fun OpenBadge(status: String) {
    val (text, color) = when (status) {
        "OPEN" -> stringResource(R.string.profile_store_open) to AzzaTheme.colors.success
        "CLOSED" -> stringResource(R.string.profile_store_closed) to AzzaTheme.colors.error
        else -> return
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AzzaTheme.colors.surface).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The web's StoreHoursCard: today's hours, and the whole week on a tap. */
@Composable
internal fun HoursCard(hours: List<OpenHours>, openStatus: String) {
    if (hours.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val byDay = hours.associateBy { it.dayOfWeek }
    val today = todayIndex()
    @Composable
    fun line(d: Int) = byDay[d]?.let { h ->
        val opens = h.opens
        val closes = h.closes
        if (h.closed || opens == null || closes == null) stringResource(R.string.profile_hours_closed) else "${clock(opens)}–${clock(closes)}"
    } ?: stringResource(R.string.profile_hours_closed)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(AzzaTheme.colors.surface)
            .clickable { expanded = !expanded }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Schedule, null, tint = AzzaTheme.colors.textMuted, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.profile_hours_today, line(today)), modifier = Modifier.weight(1f))
            OpenBadge(openStatus)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, stringResource(R.string.profile_hours_week))
        }
        if (expanded) {
            weekOrder().forEach { d ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        dayName(d),
                        fontWeight = if (d == today) FontWeight.Bold else null,
                        modifier = Modifier.width(110.dp),
                    )
                    Text(line(d), fontWeight = if (d == today) FontWeight.Bold else null, color = AzzaTheme.colors.textSecondary)
                }
            }
        }
    }
}

/** A labelled line with an icon; tappable when [onClick] is given. */
@Composable
internal fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = AzzaTheme.colors.textMuted, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = if (onClick != null) AzzaTheme.colors.primary else AzzaTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
        )
    }
}

/** The store's branches, each opening its own page. */
@Composable
internal fun BranchesSection(branches: List<Branch>, onOpen: (Branch) -> Unit) {
    if (branches.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            pluralStringResource(R.plurals.profile_branches, branches.size, branches.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        branches.forEach { b ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(b) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Storefront, null, tint = AzzaTheme.colors.textMuted)
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.bodyLarge)
                    b.location?.address?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OpenBadge(b.openStatus)
                Icon(Icons.Outlined.ChevronRight, null, tint = AzzaTheme.colors.textMuted)
            }
        }
        HorizontalDivider(color = AzzaTheme.colors.navDivider, modifier = Modifier.padding(top = 4.dp))
    }
}

internal fun dial(context: Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
}

/** The phone's own maps app at that point (with the name as the label). */
internal fun openInMaps(context: Context, latitude: Double, longitude: Double, label: String) {
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude")))
    }
}

// ---------------------------------------------------------------- a branch's public page (/stores/<store>/branches/<branch>)

@Serializable data class BranchPageRoute(val storeId: String, val branchId: String, val storeSlug: String, val storeName: String)

data class BranchPageState(
    val loading: Boolean = true,
    val branch: Branch? = null,
    val preview: BranchPreview? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class BranchPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stores: StoresRepository,
    private val map: MapRepository,
) : ViewModel() {
    val route = savedStateHandle.toRoute<BranchPageRoute>()
    private val _state = MutableStateFlow(BranchPageState())
    val state: StateFlow<BranchPageState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = BranchPageState()
        val branch = async { stores.branch(route.storeId, route.branchId) }
        // The products on hand there: the same preview the map shows (only when it has a location).
        val preview = async { map.preview(route.branchId) }
        val b = branch.await()
        _state.value = BranchPageState(
            loading = false,
            branch = (b as? Outcome.Success)?.value,
            preview = (preview.await() as? Outcome.Success)?.value,
            failed = b is Outcome.Failure,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchPageScreen(
    onBack: () -> Unit,
    onOpenStore: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    viewModel: BranchPageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val links = LocalWebLinks.current
    val route = viewModel.route
    val branch = state.branch
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(branch?.name ?: route.storeName, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.profile_back)) } },
                actions = {
                    if (branch != null && branch.slug.isNotBlank()) {
                        IconButton(onClick = { shareLink(context, branch.name, links.branch(route.storeSlug, branch.slug)) }) {
                            Icon(Icons.Outlined.Share, stringResource(com.azza.core.ui.R.string.share))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                branch == null -> Text(stringResource(R.string.profile_branch_not_found), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(24.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(branch.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                                OpenBadge(branch.openStatus)
                            }
                            Text(
                                route.storeName,
                                color = AzzaTheme.colors.primary,
                                modifier = Modifier.clickable { onOpenStore(route.storeSlug) },
                            )
                        }
                    }
                    branch.location?.let { loc ->
                        if (loc.address.isNotBlank()) item { InfoLine(Icons.Outlined.Place, loc.address) }
                        item { InfoLine(Icons.Outlined.Map, stringResource(R.string.profile_open_in_maps)) { openInMaps(context, loc.latitude, loc.longitude, branch.name) } }
                    }
                    branch.phone?.takeIf { it.isNotBlank() }?.let { phone -> item { InfoLine(Icons.Outlined.Phone, phone) { dial(context, phone) } } }
                    item {
                        HoursCard(
                            branch.week.map { OpenHours(it.dayOfWeek, it.opens.takeIf { _ -> !it.closed }, it.closes.takeIf { _ -> !it.closed }, it.closed) }
                                .takeIf { w -> w.any { !it.closed } }.orEmpty(),
                            branch.openStatus,
                        )
                    }
                    if (branch.closures.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.profile_closures), style = MaterialTheme.typography.titleSmall)
                                branch.closures.forEach { c ->
                                    Text(listOfNotNull(c.date, c.reason?.takeIf { it.isNotBlank() }).joinToString(" · "), color = AzzaTheme.colors.textSecondary)
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.profile_available_here),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    val products = state.preview?.products.orEmpty()
                    if (products.isEmpty()) {
                        item { Text(stringResource(R.string.profile_nothing_here), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                    items(products, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenProduct(p.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(AzzaTheme.colors.surface)) {
                                p.imageUrl?.let { AsyncImage(it, p.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(p.price.format(), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    val more = (state.preview?.productCount ?: 0) - products.size
                    if (more > 0) {
                        item {
                            TextButton(onClick = { onOpenStore(route.storeSlug) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(pluralStringResource(R.plurals.profile_more_available, more, more))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Opens the owner's personal page from the store page (the web's "Run by"). */
@Composable
internal fun RunBy(name: String?, username: String, onOpen: () -> Unit) {
    OutlinedButton(onClick = onOpen, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.profile_run_by, name ?: username))
    }
}
