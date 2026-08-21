package com.familylibrary.app.ui.scan

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import com.familylibrary.app.util.ScanFeedback
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** 同一 ISBN 在此时间内只回调一次（避免条码一直在画面里时反复震动） */
private const val DEFAULT_SCAN_COOLDOWN_MS = 3_000L

@androidx.camera.core.ExperimentalGetImage
@Composable
fun IsbnBarcodeScanner(
    enabled: Boolean,
    onIsbnDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
    scanCooldownMs: Long = DEFAULT_SCAN_COOLDOWN_MS,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val onDetectedState = rememberUpdatedState(onIsbnDetected)
    val debounce = remember { ScanDebounce() }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewView = this
            }
        },
        modifier = modifier,
    )

    DisposableEffect(enabled, previewView, scanCooldownMs) {
        val pv = previewView
        if (!enabled || pv == null) return@DisposableEffect onDispose {}

        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_CODE_128,
                )
                .build()
        )
        var cameraProvider: ProcessCameraProvider? = null
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(pv.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            for (code in barcodes) {
                                val isbn = normalizeBarcodeToIsbn(code.rawValue) ?: continue
                                if (!debounce.shouldAccept(isbn, scanCooldownMs)) continue
                                mainExecutor.execute {
                                    ScanFeedback.onIsbnScanned(view)
                                    onDetectedState.value(isbn)
                                }
                                break
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (_: Exception) {
                // camera unavailable
            }
        }, mainExecutor)

        onDispose {
            cameraProvider?.unbindAll()
            scanner.close()
            analyzerExecutor.shutdown()
            debounce.reset()
        }
    }
}

/** 线程安全的扫码去重（分析线程写入，主线程读取时间戳） */
internal class ScanDebounce {
    @Volatile private var lastIsbn: String = ""
    @Volatile private var lastAtMs: Long = 0L

    @Synchronized
    fun shouldAccept(isbn: String, cooldownMs: Long): Boolean {
        val now = System.currentTimeMillis()
        if (isbn == lastIsbn && now - lastAtMs < cooldownMs) return false
        lastIsbn = isbn
        lastAtMs = now
        return true
    }

    fun reset() {
        lastIsbn = ""
        lastAtMs = 0L
    }
}

fun normalizeBarcodeToIsbn(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.length == 13 || digits.length == 10 -> digits
        digits.length > 13 -> digits.takeLast(13)
        else -> null
    }
}
