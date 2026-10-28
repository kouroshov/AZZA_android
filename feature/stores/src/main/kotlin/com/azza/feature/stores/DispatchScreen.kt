package com.azza.feature.stores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.StoresRepository
import com.azza.core.designsystem.theme.AzzaTheme
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

// ✅ (2026-10-25, Phase 5 — shops, after Digikala's «ارسال از ۲ روز کاری
// آینده»; the web's DispatchSettingsCard). How many working days the store
// takes to send an order: a range, both or neither. Each listing page turns
// it into dates (Fridays skipped).

@Serializable data class DispatchRoute(val storeId: String)

data class DispatchState(
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val min: String = "",
    val max: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val invalid: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class DispatchViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: StoresRepository) : ViewModel() {
    private val storeId = savedStateHandle.toRoute<DispatchRoute>().storeId
    private val _state = MutableStateFlow(DispatchState())
    val state: StateFlow<DispatchState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, loadError = null) }
        when (val r = repo.store(storeId)) {
            is Outcome.Failure -> _state.update { it.copy(loading = false, loadError = r.error) }
            is Outcome.Success -> _state.update {
                it.copy(
                    loading = false,
                    min = r.value.dispatchDays?.first?.toString().orEmpty(),
                    max = r.value.dispatchDays?.last?.toString().orEmpty(),
                )
            }
        }
    }

    fun edit(min: String = _state.value.min, max: String = _state.value.max) =
        _state.update { it.copy(min = min.filter(Char::isDigit).take(2), max = max.filter(Char::isDigit).take(2), saved = false, invalid = false) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val lo = s.min.toIntOrNull()
        val hi = s.max.toIntOrNull()
        if ((lo == null) != (hi == null) || (lo != null && hi != null && (hi < lo || hi > 30))) {
            _state.update { it.copy(invalid = true) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r = repo.saveDispatchDays(storeId, if (lo != null && hi != null) lo..hi else null)
            _state.update { it.copy(saving = false, saved = r is Outcome.Success, error = (r as? Outcome.Failure)?.error) }
        }
    }
}

@Composable
fun DispatchScreen(nav: StoreNavigation, viewModel: DispatchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StorePage(stringResource(R.string.shop_dispatch_title), nav.back) {
        when {
            state.loading -> LoadingState()
            state.loadError != null -> ErrorState(state.loadError!!, onRetry = { viewModel.load() })
            else -> Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shop_dispatch_hint), color = AzzaTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.min,
                        onValueChange = { viewModel.edit(min = it) },
                        label = { Text(stringResource(R.string.restaurant_from)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.max,
                        onValueChange = { viewModel.edit(max = it) },
                        label = { Text(stringResource(R.string.restaurant_to)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.shop_working_days), color = AzzaTheme.colors.textMuted)
                }
                if (state.invalid) Text(stringResource(R.string.shop_dispatch_invalid), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(storeError(it), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stores_save)) }
                if (state.saved) Text(stringResource(R.string.restaurant_saved), color = AzzaTheme.colors.success)
            }
        }
    }
}
