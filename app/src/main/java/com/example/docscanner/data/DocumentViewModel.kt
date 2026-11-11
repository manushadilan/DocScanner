package com.example.docscanner.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

data class ScanPage(val id: String = UUID.randomUUID().toString(), val file: File)

/** Holds the finished pages plus the in-progress scan (raw photo -> cropped photo). */
class DocumentViewModel(app: Application) : AndroidViewModel(app) {
    private val dir = File(app.filesDir, "pages").apply { mkdirs() }

    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())
    val pages: StateFlow<List<ScanPage>> = _pages

    var raw by mutableStateOf<Bitmap?>(null); private set
    var cropped by mutableStateOf<Bitmap?>(null); private set

    fun startScan(b: Bitmap) { raw = downscale(b, 3000); cropped = null }
    fun storeCropped(b: Bitmap) { cropped = b }
    fun rotateRaw() { raw?.let { raw = Bitmap.createBitmap(it, 0, 0, it.width, it.height, Matrix().apply { postRotate(90f) }, true) } }

    /** Saves the final bitmap as a JPEG page. Call off the main thread. */
    fun commit(b: Bitmap) {
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        _pages.update { it + ScanPage(file = f) }
        raw = null; cropped = null
    }

    fun delete(id: String) = _pages.update { l ->
        l.firstOrNull { it.id == id }?.file?.delete(); l.filterNot { it.id == id }
    }
    fun move(from: Int, to: Int) = _pages.update { l ->
        if (to !in l.indices) l else l.toMutableList().apply { add(to, removeAt(from)) }
    }

    companion object {
        fun downscale(b: Bitmap, max: Int): Bitmap {
            val s = max.toFloat() / maxOf(b.width, b.height)
            return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        }
    }
}
