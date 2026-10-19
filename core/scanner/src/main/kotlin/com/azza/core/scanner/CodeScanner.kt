package com.azza.core.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow

/**
 * Where codes come from when not from the camera: a test's fake scanner,
 * or anything else that produces codes. See [CodeScanner].
 */
fun interface ScanSource {
    fun codes(): Flow<ScannedCode>
}

/**
 * Drops a value seen within [windowMillis] of the last time it was seen —
 * a camera reads the same label many times a second, a hardware scanner
 * sometimes twice. A different value always passes.
 */
class ScanDeduper(private val windowMillis: Long = 1_500, private val now: () -> Long = System::currentTimeMillis) {
    private var lastValue: String? = null
    private var lastAt = 0L

    fun accept(value: String): Boolean {
        val at = now()
        val repeat = value == lastValue && at - lastAt < windowMillis
        lastAt = at
        lastValue = value
        return !repeat
    }
}

/**
 * Collects a keyboard-wedge scanner's keys (Bluetooth / USB scanners type
 * the code and press Enter) while nothing else takes the keys. A run of at
 * least 3 characters ended by Enter is one code.
 */
class WedgeBuffer {
    private val buffer = StringBuilder()

    /** Feeds one key; returns the finished code on Enter, else null. */
    fun key(char: Char?, enter: Boolean): String? {
        if (enter) {
            val code = buffer.toString().trim()
            buffer.clear()
            return code.takeIf { it.length >= 3 }
        }
        if (char != null && !char.isISOControl()) buffer.append(char)
        return null
    }
}

/**
 * The register's scanner (POS-04, reused by POS-05): a live camera with a
 * torch, a typed-code field, and keyboard-wedge scanners. The same value is
 * ignored for 1.5 s; a new one beeps and vibrates and goes to [onCode].
 * Without the camera permission it explains why and offers typing instead.
 *
 * [active] false pauses reporting (e.g. while a sheet is open). [source]
 * replaces the camera — tests pass a fake one.
 */
@Composable
fun CodeScanner(
    onCode: (ScannedCode) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    source: ScanSource? = null,
    manualEntry: Boolean = true,
) {
    val context = LocalContext.current
    val deduper = remember { ScanDeduper() }
    val feedback = remember { ScanFeedback(context) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    val currentOnCode by rememberUpdatedState(onCode)
    val isActive by rememberUpdatedState(active)
    val deliver: (ScannedCode, Boolean) -> Unit = remember {
        { code, dedupe ->
            if (isActive && (!dedupe || deduper.accept(code.text))) {
                feedback.play()
                currentOnCode(code)
            }
        }
    }

    if (source != null) {
        LaunchedEffect(source) { source.codes().collect { deliver(it, true) } }
    }

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (source == null && !granted && !asked) permission.launch(Manifest.permission.CAMERA) }
    var torch by rememberSaveable { mutableStateOf(false) }

    val wedge = remember { WedgeBuffer() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier
            .focusRequester(focus)
            .focusable()
            // After the children: a focused text field keeps its own keys.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                val char = event.utf16CodePoint.takeIf { it > 0 }?.toChar()
                wedge.key(char, enter)?.let { deliver(ScannedCode(it, null), true) }
                enter
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(16.dp)).background(Color.Black).testTag("scanner_view"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                source != null -> Text(stringResource(R.string.scan_hint), color = Color.White)
                granted -> {
                    CameraPreview(torch, active, onCode = { deliver(it, true) })
                    Box(Modifier.fillMaxWidth(0.8f).aspectRatio(2.6f).border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp)))
                    IconButton(
                        onClick = { torch = !torch },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    ) {
                        Icon(
                            if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                            tint = Color.White,
                        )
                    }
                }
                else -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        if (manualEntry) ManualEntry { deliver(ScannedCode(it, null), false) }
    }
}

@Composable
private fun ManualEntry(onSubmit: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val submit = {
        val code = typed.trim()
        if (code.isNotEmpty()) {
            onSubmit(code)
            typed = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.take(128) },
            label = { Text(stringResource(R.string.scan_type_instead)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.weight(1f).testTag("scanner_manual"),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        FilledTonalButton(onClick = submit, enabled = typed.isNotBlank()) { Text(stringResource(R.string.scan_use_code)) }
    }
}

/** A short beep and a short buzz: the code was read. Silent where the phone cannot do either. */
private class ScanFeedback(context: Context) {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()

    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun play() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        }
    }

    fun release() {
        tone?.release()
    }
}

/**
 * CameraX preview + analysis. Frames are decoded on one background thread,
 * the newest only (a slow decode drops frames rather than queueing them).
 */
@Composable
private fun CameraPreview(torch: Boolean, active: Boolean, onCode: (ScannedCode) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember { LifecycleCameraController(context) }
    val decoder = remember { BarcodeDecoder() }
    // Read on the analysis thread; written from composition.
    val reporting = remember { AtomicBoolean(active) }
    reporting.set(active)
    val latestOnCode = remember { AtomicReference(onCode) }
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
