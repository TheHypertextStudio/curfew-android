package studio.hypertext.curfew.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import studio.hypertext.curfew.checkin.PlaceQr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceScanScreen(label: String, onScan: suspend (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler { if (!busy) onCancel() }
    Scaffold(modifier = Modifier.semantics { paneTitle = "Scan code" }, topBar = { TopAppBar(title = { Text("Scan code") }, navigationIcon = { TextButton(onClick = onCancel, enabled = !busy) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Point the camera at your $label code.", style = MaterialTheme.typography.headlineSmall)
            Text("Camera images stay on this phone. Cancelling leaves the alarm unchanged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!granted) {
                Text("Allow camera access to scan your saved code.")
                Button(onClick = { request.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Allow camera") }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Camera settings") }
            } else {
                CameraCodePreview(attempt, label, onError = { error = "Couldn’t open the camera. Check camera access and try again." }, onDecoded = { payload ->
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try { onScan(payload) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: IllegalArgumentException) { error = "That isn’t the code for this check-in. Try your saved code." }
                            catch (_: Exception) { error = "Couldn’t save the check-in. The alarm is unchanged; try again." }
                            finally { busy = false }
                        }
                    }
                })
            }
            error?.let { TaskMessage(it, error = true) }
            if (error != null && granted) Button(onClick = { error = null; attempt++ }, enabled = !busy) { Text("Try again") }
            if (busy) Text("Checking code…")
            TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel scan") }
        }
    }
}

@Composable
private fun CameraCodePreview(attempt: Int, label: String, onError: () -> Unit, onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val decoded by rememberUpdatedState(onDecoded)
    val failed by rememberUpdatedState(onError)
    DisposableEffect(owner, attempt) {
        var alive = true
        var provider: ProcessCameraProvider? = null
        val executor = Executors.newSingleThreadExecutor()
        val delivered = AtomicBoolean(false)
        val main = ContextCompat.getMainExecutor(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(executor) { image ->
            try {
                if (!delivered.get()) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer.duplicate()
                    val offset = buffer.position()
                    val pixels = ByteArray(image.width * image.height)
                    for (y in 0 until image.height) for (x in 0 until image.width) {
                        pixels[y * image.width + x] = buffer.get(offset + y * plane.rowStride + x * plane.pixelStride)
                    }
                    PlaceQr.decode(pixels, image.width, image.height)?.takeIf { delivered.compareAndSet(false, true) }?.let {
                        main.execute { if (alive) decoded(it) }
                    }
                }
            } catch (_: Exception) {
                if (delivered.compareAndSet(false, true)) main.execute { if (alive) failed() }
            } finally { image.close() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (alive) try {
                check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                provider = future.get()
                provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { failed() }
        }, main)
        onDispose {
            alive = false
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            executor.shutdown()
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp).aspectRatio(1f)
        .semantics { contentDescription = "Camera preview for $label code" })
}
