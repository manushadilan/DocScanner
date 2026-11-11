package com.example.docscanner.scanner

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

enum class FilterType { ORIGINAL, MAGIC_COLOR, GRAYSCALE, BLACK_WHITE }

object ImageFilters {

    /** contrast: multiplier (1.0 = unchanged). brightness: offset, -255..255. */
    fun apply(bitmap: Bitmap, type: FilterType, brightness: Double = 0.0, contrast: Double = 1.0): Bitmap {
        val rgba = Mat().also { Utils.bitmapToMat(bitmap, it) }
        val out = when (type) {
            FilterType.ORIGINAL -> rgba
            FilterType.MAGIC_COLOR -> magicColor(rgba)
            FilterType.GRAYSCALE -> gray(rgba).also { Imgproc.cvtColor(it, it, Imgproc.COLOR_GRAY2RGBA) }
            FilterType.BLACK_WHITE -> blackWhite(rgba)
        }
        out.convertTo(out, -1, contrast, brightness)
        return Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
            .also { Utils.matToBitmap(out, it) }
    }

    private fun gray(rgba: Mat) = Mat().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2GRAY) }

    /** Removes shadows by dividing by a heavily blurred copy (background estimate), then boosts contrast. */
    private fun magicColor(rgba: Mat): Mat {
        val rgb = Mat().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2RGB) }
        val bg = Mat()
        val k = ((maxOf(rgb.cols(), rgb.rows()) / 20).coerceIn(3, 255)) or 1   // odd kernel size
        Imgproc.medianBlur(rgb, bg, k.coerceAtMost(255))
        val norm = Mat()
        Core.divide(rgb, bg, norm, 255.0)                  // flat-field correction
        norm.convertTo(norm, -1, 1.25, -20.0)              // punch up whites
        return Mat().also { Imgproc.cvtColor(norm, it, Imgproc.COLOR_RGB2RGBA) }
    }

    /** Adaptive (local) threshold copes with uneven lighting far better than a global one. */
    private fun blackWhite(rgba: Mat): Mat {
        val g = gray(rgba)
        Imgproc.GaussianBlur(g, g, Size(3.0, 3.0), 0.0)
        val bw = Mat()
        Imgproc.adaptiveThreshold(g, bw, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 31, 15.0)
        return Mat().also { Imgproc.cvtColor(bw, it, Imgproc.COLOR_GRAY2RGBA) }
    }
}
