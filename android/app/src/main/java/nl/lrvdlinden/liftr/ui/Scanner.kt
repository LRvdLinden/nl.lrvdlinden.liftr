package nl.lrvdlinden.liftr.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.lrvdlinden.liftr.core.BarcodeFormat
import nl.lrvdlinden.liftr.core.tr
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Reads QR and barcodes with ML Kit, from the camera or from a picture. */
object CodeReader {
    fun format(mlkit: Int, value: String): BarcodeFormat = when (mlkit) {
        Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR
        Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN13
        Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN8
        Barcode.FORMAT_UPC_A -> BarcodeFormat.UPCA
        Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE128
        Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE39
        Barcode.FORMAT_ITF -> BarcodeFormat.ITF
        Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF417
        Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
        else -> BarcodeFormat.guess(value)
    }

    suspend fun readImage(context: Context, uri: Uri): Pair<String, BarcodeFormat>? = try {
        val image = InputImage.fromFilePath(context, uri)
        suspendCancellableCoroutine { cont ->
            BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { codes ->
                    val code = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                    cont.resume(code?.let { it.rawValue!! to format(it.format, it.rawValue!!) })
                }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (e: Exception) {
        null
    }
}

/** Camera viewfinder that closes as soon as it reads a code. */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerScreen(onResult: (String, BarcodeFormat) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; denied = !it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var found by remember { mutableStateOf(false) }
    fun close() { Nav.stack.lastOrNull { it is Route.Scanner }?.let { Nav.remove(it) } }

    DetailScaffold(tr("Scan with camera", "Scan met de camera"), onBack = { close() }, scroll = false) {
        if (granted) {
            val executor = remember { Executors.newSingleThreadExecutor() }
            val scanner = remember { BarcodeScanning.getClient() }
            DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }
            Box(Modifier.fillMaxWidth().aspectRatio(0.8f).border(2.dp, LiftrColors.accent, RoundedCornerShape(20.dp)).padding(2.dp)) {
                AndroidView(factory = { ctx ->
                    val view = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null || found) { proxy.close(); return@setAnalyzer }
                            val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            scanner.process(input)
                                .addOnSuccessListener { codes ->
                                    val code = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                                    if (code != null && !found) {
                                        found = true
                                        val value = code.rawValue!!
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            close()
                                            onResult(value, CodeReader.format(code.format, value))
                                        }
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                }, modifier = Modifier.fillMaxSize())
            }
            Text(tr("Hold the code in the frame.", "Houd de code in het kader."), color = LiftrColors.muted, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Column(Modifier.fillMaxWidth().liftrCard(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (denied) tr("Allow the camera for Liftr in Settings to scan your pass.",
                    "Sta de camera voor Liftr toe in Instellingen om je pas te scannen.") else "",
                    color = LiftrColors.muted, textAlign = TextAlign.Center)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
        }
    }
}

