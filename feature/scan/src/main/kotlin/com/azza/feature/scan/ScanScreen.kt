package com.azza.feature.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.ProductRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object ScanRoute

fun NavGraphBuilder.scanScreen(onBack: () -> Unit, onOpenProduct: (String) -> Unit) {
    composable<ScanRoute> { ScanScreen(onBack, onOpenProduct) }
}

sealed interface ScanStatus {
    data object Scanning : ScanStatus
    data class Looking(val code: String) : ScanStatus
    data class NotFound(val code: String) : ScanStatus
    data class Failed(val code: String, val error: AppError) : ScanStatus
}

data class ScanState(val status: ScanStatus = ScanStatus.Scanning, val typed: String = "", val typedInvalid: Boolean = false)

@HiltViewModel
class ScanViewModel @Inject constructor(private val products: ProductRepository) : ViewModel() {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _found = Channel<String>(Channel.BUFFERED)
    val found = _found.receiveAsFlow()

    /** The camera reports a code: only one look-up at a time, and not the same miss twice in a row. */
    fun onScanned(code: String) {
        val now = _state.value.status
        if (now is ScanStatus.Looking || (now is ScanStatus.NotFound && now.code == code)) return
        lookUp(code)
    }

    fun onTyped(text: String) = _state.update { it.copy(typed = text.filter(Char::isDigit).take(13), typedInvalid = false) }

    fun submitTyped() {
        val code = _state.value.typed
        if (!Ean13.isValid(code)) return _state.update { it.copy(typedInvalid = true) }
        lookUp(code)
    }

    fun scanAgain() = _state.update { it.copy(status = ScanStatus.Scanning) }

    private fun lookUp(code: String) {
        _state.update { it.copy(status = ScanStatus.Looking(code)) }
        viewModelScope.launch {
            when (val r = products.idByBarcode(code)) {
                is Outcome.Success -> {
                    _state.update { it.copy(status = ScanStatus.Scanning, typed = "") }
                    _found.send(r.value)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        status = if ((r.error as? AppError.Api)?.code == "PRODUCT_NOT_FOUND") ScanStatus.NotFound(code) else ScanStatus.Failed(code, r.error),
                    )
                }
            }
        }
    }
}

@Composable
fun ScanScreen(onBack: () -> Unit, onOpenProduct: (String) -> Unit, viewModel: ScanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.found.collect(onOpenProduct) }

    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted && !asked) permission.launch(Manifest.permission.CAMERA) }
    var torch by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.scan_back)) } },
                actions = {
                    if (granted) {
                        IconButton(onClick = { torch = !torch }) {
                            Icon(
                                if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                                stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    CameraPreview(torch, active = state.status == ScanStatus.Scanning || state.status is ScanStatus.NotFound, onCode = viewModel::onScanned)
                    // Where to hold the label: a wide, short window, as for any 1-D barcode.
                    Box(Modifier.fillMaxWidth(0.8f).aspectRatio(2.6f).border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp)))
                } else {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.scan_camera_needed), color = Color.White)
                        OutlinedButton(onClick = {
                            if (asked) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            } else {
                                permission.launch(Manifest.permission.CAMERA)
                            }
                        }) { Text(stringResource(R.string.scan_allow_camera), color = Color.White) }
                    }
                }
            }

            when (val s = state.status) {
                ScanStatus.Scanning -> Text(stringResource(R.string.scan_hint), color = AzzaTheme.colors.textSecondary)
                is ScanStatus.Looking -> Text(stringResource(R.string.scan_looking, s.code), color = AzzaTheme.colors.textSecondary)
                is ScanStatus.NotFound -> Text(stringResource(R.string.scan_not_found, s.code), color = AzzaTheme.colors.error)
                is ScanStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(errorMessage(s.error), color = AzzaTheme.colors.error, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = viewModel::scanAgain) { Text(stringResource(R.string.scan_again)) }
                }
            }

            Text(stringResource(R.string.scan_type_instead), style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textSecondary)
            OutlinedTextField(
                value = state.typed,
                onValueChange = viewModel::onTyped,
                label = { Text(stringResource(R.string.scan_digits)) },
                isError = state.typedInvalid,
                supportingText = { if (state.typedInvalid) Text(stringResource(R.string.scan_invalid)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.submitTyped() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::submitTyped,
                enabled = state.typed.length == 13 && state.status !is ScanStatus.Looking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.scan_find)) }
        }
    }
}

/**
 * CameraX preview + analysis. Frames are decoded on one background thread,
 * the newest only (a slow decode drops frames rather than queueing them).
 * [active] false stops reporting while a code is being looked up.
 */
@Composable
private fun CameraPreview(torch: Boolean, active: Boolean, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember { LifecycleCameraController(context) }
    val decoder = remember { BarcodeDecoder() }
    // Read on the analysis thread; written from composition.
    val reporting = remember { java.util.concurrent.atomic.AtomicBoolean(active) }
    reporting.set(active)
    val latestOnCode = remember { java.util.concurrent.atomic.AtomicReference(onCode) }
    latestOnCode.set(onCode)
    val main = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(lifecycleOwner) {
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.setImageAnalysisAnalyzer(executor) { image ->
            image.use {
                if (!reporting.get()) return@use
                val plane = it.planes[0]
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                // The last row may be shorter than rowStride; pad so every row can be read at full stride.
                val full = if (bytes.size >= plane.rowStride * it.height) bytes else bytes.copyOf(plane.rowStride * it.height)
                decoder.decode(full, it.width, it.height, plane.rowStride)?.let { code -> main.execute { latestOnCode.get()(code) } }
            }
        }
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            controller.clearImageAnalysisAnalyzer()
            executor.shutdown()
        }
    }
    LaunchedEffect(torch) { controller.enableTorch(torch) }

    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
        modifier = Modifier.fillMaxSize(),
    )
}
