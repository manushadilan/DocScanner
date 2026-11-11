package com.example.docscanner.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Camera preview with a 3x3 alignment grid and a shutter button. */
@Composable
fun CameraCapture(onCaptured: (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val busy = remember { AtomicBoolean(false) }

    fun shoot() {
        if (!busy.compareAndSet(false, true)) return   // ignore double taps
        capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // toBitmap() ignores rotation, so apply it ourselves
                val m = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                val raw = image.toBitmap(); image.close()
                onCaptured(Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true))
                busy.set(false)
            }
            override fun onError(e: ImageCaptureException) { busy.set(false) }
        })
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { c ->
            PreviewView(c).also { pv ->
                val fut = ProcessCameraProvider.getInstance(c)
                fut.addListener({
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    fut.get().apply {
                        unbindAll()
                        bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }
                }, ContextCompat.getMainExecutor(c))
            }
        }, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            for (i in 1..2) {
                drawLine(Color.White.copy(.5f), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 2f)
                drawLine(Color.White.copy(.5f), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 2f)
            }
        }
        LargeFloatingActionButton(
            onClick = { shoot() },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)
        ) { Icon(Icons.Default.PhotoCamera, "Capture") }
    }
}
