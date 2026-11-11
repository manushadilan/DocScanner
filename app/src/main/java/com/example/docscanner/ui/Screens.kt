@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)

package com.example.docscanner.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.docscanner.camera.CameraCapture
import com.example.docscanner.data.DocumentViewModel
import com.example.docscanner.pdf.PdfExporter
import com.example.docscanner.scanner.DocumentScanner
import com.example.docscanner.scanner.FilterType
import com.example.docscanner.scanner.ImageFilters
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.*
import org.opencv.core.Point

/* ---------- 1. Capture ---------- */
@Composable
fun CaptureScreen(vm: DocumentViewModel, onCaptured: () -> Unit) {
    val perm = rememberPermissionState(Manifest.permission.CAMERA)
    val main = ContextCompat.getMainExecutor(LocalContext.current)
    LaunchedEffect(Unit) { if (!perm.status.isGranted) perm.launchPermissionRequest() }

    if (perm.status.isGranted) {
        // The callback fires on the camera thread; hop to main before touching UI state/navigation.
        CameraCapture(onCaptured = { bmp -> main.execute { vm.startScan(bmp); onCaptured() } })
    } else {
        Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Camera access is needed to scan documents.")
            Spacer(Modifier.height(16.dp))
            Button({ perm.launchPermissionRequest() }) { Text("Grant permission") }
        }
    }
}

/* ---------- 2. Crop: auto-detect, then fine-tune with grid, handles, loupe ---------- */
@Composable
fun CropScreen(vm: DocumentViewModel, onRetake: () -> Unit, onDone: () -> Unit) {
    val raw = vm.raw ?: run { LaunchedEffect(Unit) { onRetake() }; return }
    var corners by remember(raw) { mutableStateOf<List<Offset>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fullPage = listOf(Offset(.02f, .02f), Offset(.98f, .02f), Offset(.98f, .98f), Offset(.02f, .98f))

    suspend fun detect() = withContext(Dispatchers.Default) {
        DocumentScanner.detectQuad(raw)?.map { Offset((it.x / raw.width).toFloat(), (it.y / raw.height).toFloat()) } ?: fullPage
    }
    LaunchedEffect(raw) { corners = detect() }   // re-runs after Rotate, too

    Scaffold(topBar = { TopAppBar(title = { Text("Adjust edges") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                corners?.let { CropOverlay(raw, it, { c -> corners = c }) } ?: CircularProgressIndicator()
            }
            Text("Drag corners or edge handles · drag inside to move all", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                TextButton({ vm.rotateRaw() }) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(4.dp)); Text("Rotate") }
                TextButton({ scope.launch { corners = detect() } }) { Text("Auto-detect") }
                TextButton({ corners = fullPage }) { Text("Full page") }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                OutlinedButton(onRetake) { Text("Retake") }
                Button(enabled = corners != null && !busy, onClick = {
                    scope.launch {
                        busy = true
                        val out = withContext(Dispatchers.Default) {
                            val quad = DocumentScanner.orderPoints(
                                corners!!.map { Point(it.x * raw.width.toDouble(), it.y * raw.height.toDouble()) })
                            DocumentScanner.warp(raw, quad)
                        }
                        vm.storeCropped(out); busy = false; onDone()
                    }
                }) { Text("Crop") }
            }
        }
    }
}

/* ---------- 3. Enhance: filter + brightness/contrast, preview on a small copy ---------- */
@Composable
fun EnhanceScreen(vm: DocumentViewModel, onSaved: () -> Unit) {
    val src = vm.cropped ?: run { LaunchedEffect(Unit) { onSaved() }; return }
    val small = remember(src) { DocumentViewModel.downscale(src, 1000) }
    var filter by remember { mutableStateOf(FilterType.MAGIC_COLOR) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var preview by remember { mutableStateOf(small) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(filter, brightness, contrast) {
        delay(120) // debounce slider drags; the previous run is cancelled automatically
        preview = withContext(Dispatchers.Default) {
            ImageFilters.apply(small, filter, brightness.toDouble(), contrast.toDouble())
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Enhance") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Image(preview.asImageBitmap(), null, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.padding(vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
                FilterType.entries.forEach { t ->
                    FilterChip(filter == t, { filter = t }, { Text(t.name.lowercase().replace('_', ' ')) })
                }
            }
            Text("Brightness"); Slider(brightness, { brightness = it }, valueRange = -100f..100f)
            Text("Contrast"); Slider(contrast, { contrast = it }, valueRange = 0.5f..2f)
            Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    saving = true
                    withContext(Dispatchers.Default) { // full-resolution pass, then write the JPEG
                        vm.commit(ImageFilters.apply(src, filter, brightness.toDouble(), contrast.toDouble()))
                    }
                    onSaved()
                }
            }) { Text(if (saving) "Saving…" else "Save page") }
        }
    }
}

/* ---------- 4. Pages: grid view, reorder, delete, add, Save as PDF ---------- */
@Composable
fun PagesScreen(vm: DocumentViewModel, onAdd: () -> Unit) {
    val pages by vm.pages.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var exporting by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)!!.use { PdfExporter.export(pages.map { it.file }, it) }
                }.isSuccess
            }
            exporting = false
            snack.showSnackbar(if (ok) "PDF saved" else "Export failed")
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("My scan (${pages.size})") }) },
        snackbarHost = { SnackbarHost(snack) },
        // Big, labelled bottom bar: the two main actions are impossible to miss.
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onAdd, Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(8.dp)); Text("Add page")
                    }
                    Button(
                        onClick = { saveLauncher.launch("scan_${System.currentTimeMillis()}.pdf") },
                        enabled = pages.isNotEmpty() && !exporting,
                        modifier = Modifier.weight(1.4f).height(52.dp)
                    ) {
                        if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (exporting) "Saving…" else "Save as PDF", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { pad ->
        if (pages.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { Text("No pages yet. Tap “Add page” to scan.") }
        } else LazyVerticalGrid(
            GridCells.Adaptive(150.dp), Modifier.padding(pad), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { i, page ->
                val thumb by produceState<Bitmap?>(null, page.file) {
                    value = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(page.file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                }
                Card {
                    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).padding(6.dp)) {
                        thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                        Badge(Modifier.align(Alignment.TopStart)) { Text("${i + 1}") }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        IconButton({ vm.move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Default.ChevronLeft, "Move earlier") }
                        IconButton({ vm.delete(page.id) }) { Icon(Icons.Default.Delete, "Delete") }
                        IconButton({ vm.move(i, i + 1) }, enabled = i < pages.lastIndex) { Icon(Icons.Default.ChevronRight, "Move later") }
                    }
                }
            }
        }
    }
}
