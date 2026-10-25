package com.azza.feature.stores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.MenuHeading
import com.azza.core.data.RestaurantSettings
import com.azza.core.data.StoresRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Money
import com.azza.core.model.SchemaRules
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// ✅ (2026-10-23, Phase 4 — restaurants). A store's owner sets it up the way
// Snappfood asks a vendor to (the web's RestaurantSettingsCard and
// MenuSectionsEditor): is this a restaurant, what delivery costs, the
// smallest basket, how long delivery takes, a switch for "not taking orders
// right now", and the menu's headings in the order customers see them.
// Amounts are typed in toman and sent in rial; apps/api checks every value
// and checkout applies them.

@Serializable data class RestaurantRoute(val storeId: String)

data class RestaurantForm(
    val isRestaurant: Boolean = false,
    val fee: String = "",
    val minOrder: String = "",
    val minMinutes: String = "",
    val maxMinutes: String = "",
    val paused: Boolean = false,
)

data class RestaurantState(
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val storeName: String = "",
    val form: RestaurantForm = RestaurantForm(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val rangeInvalid: Boolean = false,
    val error: AppError? = null,
    val headings: List<MenuHeading> = emptyList(),
    val newHeading: String = "",
    val headingBusy: Boolean = false,
    val headingError: AppError? = null,
)

@HiltViewModel
class RestaurantViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    private val storeId = savedStateHandle.toRoute<RestaurantRoute>().storeId
    private val _state = MutableStateFlow(RestaurantState())
    val state: StateFlow<RestaurantState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, loadError = null) }
        when (val r = repo.store(storeId)) {
            is Outcome.Failure -> _state.update { it.copy(loading = false, loadError = r.error) }
            is Outcome.Success -> {
                val t = r.value.restaurant
                fun toman(m: Money) = (m.amountMinor / Money.MINOR_PER_TOMAN).takeIf { it > 0 }?.toString().orEmpty()
                _state.update {
                    it.copy(
                        loading = false,
                        storeName = r.value.name,
                        form = RestaurantForm(
                            isRestaurant = t != null,
                            fee = t?.let { x -> toman(x.deliveryFee) }.orEmpty(),
                            minOrder = t?.let { x -> toman(x.minOrder) }.orEmpty(),
                            minMinutes = t?.deliveryMinutes?.first?.toString().orEmpty(),
                            maxMinutes = t?.deliveryMinutes?.last?.toString().orEmpty(),
                            paused = t?.paused ?: false,
                        ),
                    )
                }
                loadHeadings()
            }
        }
    }

    private suspend fun loadHeadings() {
        (repo.menuHeadings(storeId) as? Outcome.Success)?.value?.let { h -> _state.update { it.copy(headings = h) } }
    }

    fun edit(change: RestaurantForm.() -> RestaurantForm) = _state.update { it.copy(form = it.form.change(), saved = false, rangeInvalid = false) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val f = s.form
        val lo = number(f.minMinutes)?.toInt()
        val hi = number(f.maxMinutes)?.toInt()
        if (f.isRestaurant && ((lo == null) != (hi == null) || (lo != null && hi != null && hi < lo))) {
            _state.update { it.copy(rangeInvalid = true) }
            return
        }
        _state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            val r = repo.saveRestaurant(
                storeId,
                RestaurantSettings(
                    isRestaurant = f.isRestaurant,
                    deliveryFeeToman = number(f.fee) ?: 0.0,
                    minOrderToman = number(f.minOrder) ?: 0.0,
                    deliveryMinutes = if (lo != null && hi != null) lo..hi else null,
                    paused = f.paused,
                ),
            )
            _state.update { it.copy(saving = false, saved = r is Outcome.Success, error = (r as? Outcome.Failure)?.error) }
        }
    }

    fun typeHeading(v: String) = _state.update { it.copy(newHeading = v.take(60), headingError = null) }

    fun addHeading() {
        val name = _state.value.newHeading.trim()
        if (name.isEmpty()) return
        heading({ repo.addMenuHeading(storeId, name) }) { it.copy(newHeading = "") }
    }

    fun rename(h: MenuHeading, name: String) {
        if (name.isBlank() || name.trim() == h.name) return
        heading({ repo.renameMenuHeading(storeId, h.id, name) })
    }

    fun delete(h: MenuHeading) = heading({ repo.deleteMenuHeading(storeId, h.id) })

    /** Swaps two neighbours: each takes the other's position. */
    fun move(h: MenuHeading, by: Int) {
        val list = _state.value.headings
        val i = list.indexOf(h)
        val j = i + by
        if (i < 0 || j !in list.indices) return
        heading({
            val a = repo.moveMenuHeading(storeId, h.id, j)
            if (a is Outcome.Failure) a else repo.moveMenuHeading(storeId, list[j].id, i)
        })
    }

    private fun heading(call: suspend () -> Outcome<Unit>, onSuccess: (RestaurantState) -> RestaurantState = { it }) {
        if (_state.value.headingBusy) return
        _state.update { it.copy(headingBusy = true, headingError = null) }
        viewModelScope.launch {
            val r = call()
            _state.update { s -> (if (r is Outcome.Success) onSuccess(s) else s).copy(headingBusy = false, headingError = (r as? Outcome.Failure)?.error) }
            loadHeadings()
        }
    }

    private fun number(text: String): Double? = SchemaRules.toLatinDigits(text).replace(",", "").replace("٬", "").trim().toDoubleOrNull()?.takeIf { it >= 0 }
}

@Composable
fun RestaurantScreen(nav: StoreNavigation, viewModel: RestaurantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StorePage(stringResource(R.string.restaurant_settings_title), nav.back) {
        when {
            state.loading -> LoadingState()
            state.loadError != null -> ErrorState(state.loadError!!, onRetry = { viewModel.load() })
            else -> Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val f = state.form
                Text(stringResource(R.string.restaurant_settings_hint), color = AzzaTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().clickable { viewModel.edit { copy(isRestaurant = !isRestaurant) } }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = f.isRestaurant, onCheckedChange = { on -> viewModel.edit { copy(isRestaurant = on) } })
                    Text(stringResource(R.string.restaurant_is_restaurant))
                }
                if (f.isRestaurant) {
                    NumberField(f.fee, { v -> viewModel.edit { copy(fee = v) } }, R.string.restaurant_delivery_fee)
                    NumberField(f.minOrder, { v -> viewModel.edit { copy(minOrder = v) } }, R.string.restaurant_min_order)
                    Text(stringResource(R.string.restaurant_delivery_time), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { NumberField(f.minMinutes, { v -> viewModel.edit { copy(minMinutes = v.take(3)) } }, R.string.restaurant_from) }
                        Column(Modifier.weight(1f)) { NumberField(f.maxMinutes, { v -> viewModel.edit { copy(maxMinutes = v.take(3)) } }, R.string.restaurant_to) }
                        Text(stringResource(R.string.restaurant_minutes), color = AzzaTheme.colors.textMuted)
                    }
                    if (state.rangeInvalid) Text(stringResource(R.string.restaurant_range_invalid), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.restaurant_pause), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.restaurant_pause_hint), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = f.paused, onCheckedChange = { on -> viewModel.edit { copy(paused = on) } })
                    }
                }
                state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stores_save)) }
                if (state.saved) Text(stringResource(R.string.restaurant_saved), color = AzzaTheme.colors.success)

                HorizontalDivider(color = AzzaTheme.colors.navDivider, modifier = Modifier.padding(vertical = 8.dp))
                MenuHeadings(state, viewModel)
            }
        }
    }
}

@Composable
private fun MenuHeadings(state: RestaurantState, viewModel: RestaurantViewModel) {
    var renaming by remember { mutableStateOf<MenuHeading?>(null) }
    var removing by remember { mutableStateOf<MenuHeading?>(null) }
    Text(stringResource(R.string.restaurant_menu_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.restaurant_menu_hint), color = AzzaTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    if (state.headings.isEmpty()) Text(stringResource(R.string.restaurant_no_sections), color = AzzaTheme.colors.textMuted)
    state.headings.forEachIndexed { i, h ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(h.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
            IconButton(onClick = { viewModel.move(h, -1) }, enabled = i > 0 && !state.headingBusy) {
                Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.restaurant_move_up, h.name))
            }
            IconButton(onClick = { viewModel.move(h, 1) }, enabled = i < state.headings.lastIndex && !state.headingBusy) {
                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.restaurant_move_down, h.name))
            }
            IconButton(onClick = { renaming = h }, enabled = !state.headingBusy) { Icon(Icons.Outlined.Edit, stringResource(R.string.restaurant_rename, h.name)) }
            IconButton(onClick = { removing = h }, enabled = !state.headingBusy) { Icon(Icons.Outlined.Delete, stringResource(R.string.restaurant_remove, h.name)) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.newHeading,
            onValueChange = viewModel::typeHeading,
            placeholder = { Text(stringResource(R.string.restaurant_new_section)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = viewModel::addHeading, enabled = state.newHeading.isNotBlank() && !state.headingBusy) { Text(stringResource(R.string.restaurant_add_section)) }
    }
    state.headingError?.let {
        val text = if ((it as? AppError.Api)?.code == "MENU_SECTION_EXISTS") stringResource(R.string.restaurant_section_exists) else storeError(it)
        Text(text, color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall)
    }

    renaming?.let { h ->
        var name by remember(h.id) { mutableStateOf(h.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.restaurant_section_name)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.rename(h, name); renaming = null }, enabled = name.isNotBlank()) { Text(stringResource(R.string.stores_save)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.restaurant_cancel)) } },
        )
    }
    removing?.let { h ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(stringResource(R.string.restaurant_remove_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(h); removing = null }) { Text(stringResource(R.string.restaurant_remove, h.name)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.restaurant_cancel)) } },
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: Int) = OutlinedTextField(
    value = value,
    onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(12)) },
    label = { Text(stringResource(label)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = Modifier.fillMaxWidth(),
)
