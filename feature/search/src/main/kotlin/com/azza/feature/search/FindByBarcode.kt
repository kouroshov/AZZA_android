package com.azza.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.common.WebDestination
import com.azza.core.common.WebLinks
import com.azza.core.data.ProductRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.scanner.CodeScanner
import com.azza.core.scanner.Ean13
import com.azza.core.ui.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// The search box's barcode button (restored by the product owner,
// 2026-10-20): a post's label — its EAN-13, or its AZZA link as a QR —
// opens the post. The camera, typing and keyboard-wedge scanners come from
// core:scanner, the same scanner as the inventory and the register.

@Serializable data object FindByBarcodeRoute

fun NavGraphBuilder.findByBarcodeScreen(onBack: () -> Unit, onOpenProduct: (String) -> Unit) {
    composable<FindByBarcodeRoute> { FindByBarcodeScreen(onBack, onOpenProduct) }
}

sealed interface FindStatus {
    data object Scanning : FindStatus
    data class Looking(val code: String) : FindStatus
    data class NotFound(val code: String) : FindStatus
    data class Failed(val error: AppError) : FindStatus
}

@HiltViewModel
class FindByBarcodeViewModel @Inject constructor(
    private val products: ProductRepository,
    private val webLinks: WebLinks,
) : ViewModel() {
    private val _status = MutableStateFlow<FindStatus>(FindStatus.Scanning)
    val status: StateFlow<FindStatus> = _status.asStateFlow()

    private val _found = Channel<String>(Channel.BUFFERED)
    val found = _found.receiveAsFlow()

    fun onCode(raw: String) {
        if (_status.value is FindStatus.Looking) return
        val code = raw.trim()
        // A post's QR is its address on the website.
        (webLinks.parse(code) as? WebDestination.Product)?.let { viewModelScope.launch { _found.send(it.id) }; return }
        if (!Ean13.isValid(code)) {
            _status.value = FindStatus.NotFound(code)
            return
        }
        _status.value = FindStatus.Looking(code)
        viewModelScope.launch {
            when (val r = products.idByBarcode(code)) {
                is Outcome.Success -> {
                    _status.value = FindStatus.Scanning
                    _found.send(r.value)
                }
                is Outcome.Failure -> _status.value =
                    if ((r.error as? AppError.Api)?.code == "PRODUCT_NOT_FOUND") FindStatus.NotFound(code) else FindStatus.Failed(r.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindByBarcodeScreen(onBack: () -> Unit, onOpenProduct: (String) -> Unit, viewModel: FindByBarcodeViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.found.collect(onOpenProduct) }
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.scan_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CodeScanner(onCode = { viewModel.onCode(it.text) }, active = status !is FindStatus.Looking)
            when (val s = status) {
                FindStatus.Scanning -> Text(stringResource(R.string.scan_find_hint), color = AzzaTheme.colors.textSecondary)
                is FindStatus.Looking -> Text(stringResource(R.string.scan_looking, s.code), color = AzzaTheme.colors.textSecondary)
                is FindStatus.NotFound -> Text(stringResource(R.string.scan_not_found, s.code), color = AzzaTheme.colors.error)
                is FindStatus.Failed -> Text(errorMessage(s.error), color = AzzaTheme.colors.error)
            }
        }
    }
}
