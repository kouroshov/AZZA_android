package com.azza.feature.stores

import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ContentCopy
import com.azza.core.ui.clock
import com.azza.core.ui.dayName
import com.azza.core.ui.weekOrder
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azza.core.data.DayHours
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Area
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// ---------------------------------------------------------------- small shared pieces


/** A time chooser: [onPick] gets minutes from midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60 % 24, initialMinute = initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.stores_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stores_cancel)) } },
    )
}

/** A date chooser: [onPick] gets yyyy-MM-dd (the calendar's own day, not a moment). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                    onPick(String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)))
                }
            }, enabled = state.selectedDateMillis != null) { Text(stringResource(R.string.stores_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stores_cancel)) } },
    ) { DatePicker(state) }
}

// ---------------------------------------------------------------- a branch

@Composable
fun BranchScreen(nav: StoreNavigation, viewModel: BranchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }
    var picking by remember { mutableStateOf<Pair<DayHours, Boolean>?>(null) } // day, opening?
    var addingClosure by remember { mutableStateOf(false) }
    StorePage(state.name.ifBlank { stringResource(R.string.stores_branch) }, nav.back) {
        when (val b = state.branch) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(b.error, onRetry = { viewModel.load() })
            is Load.Ready -> Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(state.name, { v -> viewModel.edit { copy(name = v.take(120)) } }, R.string.stores_branch_name)
                FormField(state.phone, { v -> viewModel.edit { copy(phone = v.take(30)) } }, R.string.stores_phone, KeyboardType.Phone)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.stores_branch_active), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.stores_branch_active_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                    }
                    Switch(checked = state.active, onCheckedChange = { v -> viewModel.edit { copy(active = v) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.open(BranchLocationRoute(viewModel.storeId, viewModel.branchId)) }) {
                        Icon(Icons.Outlined.Place, null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(if (b.value.location == null) R.string.stores_set_location else R.string.stores_change_location))
                    }
                    OutlinedButton(onClick = { nav.open(BranchStockRoute(viewModel.storeId, viewModel.branchId)) }) {
                        Icon(Icons.Outlined.Inventory2, null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.stores_stock))
                    }
                }
                b.value.location?.let { Text(it.address, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary) }

                HorizontalDivider(color = AzzaTheme.colors.navDivider)
                Text(stringResource(R.string.stores_hours), style = MaterialTheme.typography.titleSmall)
                val byDay = state.week.associateBy { it.dayOfWeek }
                weekOrder().forEach { d ->
                    val day = byDay[d] ?: DayHours(d)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayName(d), modifier = Modifier.width(96.dp))
                        if (day.closed) {
                            Text(stringResource(R.string.stores_closed_all_day), color = AzzaTheme.colors.textMuted, modifier = Modifier.weight(1f))
                        } else {
                            TextButton(onClick = { picking = day to true }) { Text(clock(day.opens)) }
                            Text("–")
                            TextButton(onClick = { picking = day to false }) { Text(clock(day.closes)) }
                            Box(Modifier.weight(1f))
                            IconButton(onClick = { viewModel.applyToAll(day) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.stores_apply_all_days)) }
                        }
                        Switch(checked = !day.closed, onCheckedChange = { open -> viewModel.setDay(day.copy(closed = !open)) })
                    }
                }

                HorizontalDivider(color = AzzaTheme.colors.navDivider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stores_closures), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addingClosure = true }) { Text(stringResource(R.string.stores_add_closure)) }
                }
                if (state.closures.isEmpty()) Text(stringResource(R.string.stores_no_closures), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                state.closures.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(c.date, c.reason?.takeIf { it.isNotBlank() }).joinToString(" · "), modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeClosure(c.date) }) { Icon(Icons.Outlined.Close, stringResource(R.string.stores_remove)) }
                    }
                }

                state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                if (state.saved) Text(stringResource(R.string.stores_saved), color = AzzaTheme.colors.primary, style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::save, enabled = !state.saving && state.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stores_save)) }
            }
        }
    }
    picking?.let { (day, opening) ->
        TimeDialog(if (opening) day.opens else day.closes, onDismiss = { picking = null }) { m ->
            viewModel.setDay(if (opening) day.copy(opens = m) else day.copy(closes = m))
            picking = null
        }
    }
    if (addingClosure) ClosureDialog(onDismiss = { addingClosure = false }) { date, reason ->
        viewModel.addClosure(date, reason)
        addingClosure = false
    }
}

@Composable
private fun ClosureDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf(true) }
    if (choosing) {
        DateDialog(onDismiss = { if (date == null) onDismiss() else choosing = false }) { date = it; choosing = false }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.orEmpty()) },
        text = { FormField(reason, { reason = it.take(200) }, R.string.stores_closure_reason) },
        confirmButton = { TextButton(onClick = { date?.let { onAdd(it, reason) } }) { Text(stringResource(R.string.stores_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stores_cancel)) } },
    )
}

// ---------------------------------------------------------------- stock at a branch

@Composable
fun StockScreen(nav: StoreNavigation, viewModel: StockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }
    var editing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    StorePage(stringResource(R.string.stores_stock), nav.back) {
        when (val s = state.lines) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(s.error, onRetry = { viewModel.load() })
            is Load.Ready -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(stringResource(R.string.stores_stock_intro), color = AzzaTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                    if (s.value.size > 5) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.stores_stock_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
                if (s.value.isEmpty()) item { Text(stringResource(R.string.stores_stock_empty), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(16.dp)) }
                val shown = s.value.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
                items(shown, key = { it.productId }) { line ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = line.productId to line.quantity }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { nav.openListing(line.productId) })
                            if (line.reserved > 0) Text(stringResource(R.string.stores_stock_reserved, line.reserved), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                            // Stock can be kept for a draft, but nobody can buy it yet.
                            if (!line.published) Text(stringResource(R.string.stores_status_inactive), style = MaterialTheme.typography.labelSmall, color = AzzaTheme.colors.warning)
                        }
                        Text(if (state.savingId == line.productId) "…" else line.quantity.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = AzzaTheme.colors.navDivider)
                }
            }
        }
    }
    editing?.let { (id, qty) ->
        // Opens with the number selected: typing replaces it.
        var field by remember(id) { mutableStateOf(TextFieldValue(qty.toString(), TextRange(0, qty.toString().length))) }
        val text = field.text
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.stores_stock_set)) },
            text = {
                OutlinedTextField(
                    value = field,
                    onValueChange = { v -> field = v.copy(text = v.text.filter(Char::isDigit).take(6)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = { text.toIntOrNull()?.let { viewModel.setQuantity(id, it) }; editing = null }, enabled = text.toIntOrNull() != null) {
                    Text(stringResource(R.string.stores_save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.stores_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- where a branch is

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val IRAN = LatLng(32.4, 53.4)

/** A pin fixed at the centre of the map; the place is wherever the map is moved to. City and address are chosen below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(nav: StoreNavigation, viewModel: LocationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) nav.back() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(STYLE_URL))
            map = m
        }
    }
    // Choosing a province, then a city, brings the map there; the pin is then placed by hand.
    LaunchedEffect(map, state.province, state.city) {
        val m = map ?: return@LaunchedEffect
        val area = state.city ?: state.province ?: return@LaunchedEffect
        val lat = area.latitude ?: return@LaunchedEffect
        val lng = area.longitude ?: return@LaunchedEffect
        m.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), if (state.city != null) 12.0 else 7.0))
    }
    LaunchedEffect(map, state.start) {
        val m = map ?: return@LaunchedEffect
        val at = state.start?.let { LatLng(it.latitude, it.longitude) }
        m.cameraPosition = CameraPosition.Builder().target(at ?: IRAN).zoom(if (at != null) 15.0 else 4.5).build()
    }

    StorePage(stringResource(R.string.stores_location_title), nav.back) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                // The pin's point sits on the map's centre.
                Icon(Icons.Outlined.Place, null, tint = Color(0xFFE53935), modifier = Modifier.size(44.dp).padding(bottom = 22.dp))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stores_location_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
                AreaMenu(stringResource(R.string.stores_province), state.province, state.provinces, viewModel::chooseProvince)
                AreaMenu(stringResource(R.string.stores_city), state.city, state.cities, viewModel::chooseCity)
                FormField(state.address, viewModel::editAddress, R.string.stores_address)
                // Or the exact point, typed (e.g. copied from another maps app).
                var lat by rememberSaveable { mutableStateOf("") }
                var lng by rememberSaveable { mutableStateOf("") }
                val typed = lat.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { a -> lng.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { a to it } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(lat, { lat = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, label = { Text(stringResource(R.string.stores_latitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(lng, { lng = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, label = { Text(stringResource(R.string.stores_longitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = typed != null && map != null,
                        onClick = { typed?.let { (a, o) -> map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(a, o), 16.0)) } },
                    ) { Text(stringResource(R.string.stores_go_to_point)) }
                }
                state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = { map?.cameraPosition?.target?.let { viewModel.save(it.latitude, it.longitude) } },
                    enabled = state.city != null && state.address.isNotBlank() && !state.saving && map != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.stores_location_save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaMenu(label: String, chosen: Area?, options: List<Area>, onChoose: (Area) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it && options.isNotEmpty() }) {
        OutlinedTextField(
            value = chosen?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { onChoose(a); open = false }) }
        }
    }
}
