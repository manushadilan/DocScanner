package com.example.docscanner.scanner

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

/** Quad order everywhere: top-left, top-right, bottom-right, bottom-left. */
object DocumentScanner {

    /** Finds the largest 4-corner contour. Returns points in the bitmap's pixel space, or null. */
    fun detectQuad(bitmap: Bitmap): List<Point>? {
        val src = Mat().also { Utils.bitmapToMat(bitmap, it) }
        val scale = 500.0 / src.rows()                        // work on a small image = fast
        val small = Mat().also { Imgproc.resize(src, it, Size(), scale, scale) }
        val gray = Mat(); val edges = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val minArea = 0.15 * small.rows() * small.cols()      // ignore tiny shapes

        val best = contours.sortedByDescending { Imgproc.contourArea(it) }.take(8).firstNotNullOfOrNull { c ->
            val c2f = MatOfPoint2f(*c.toArray())
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * Imgproc.arcLength(c2f, true), true)
            val pts = approx.toArray()
            if (pts.size == 4 && Imgproc.contourArea(approx) > minArea &&
                Imgproc.isContourConvex(MatOfPoint(*pts))) pts else null
        } ?: return null
        return orderPoints(best.map { Point(it.x / scale, it.y / scale) })
    }

    /** Sort corners using x+y (min=TL, max=BR) and y-x (min=TR, max=BL). */
    fun orderPoints(p: List<Point>): List<Point> = listOf(
        p.minBy { it.x + it.y }, p.minBy { it.y - it.x },
        p.maxBy { it.x + it.y }, p.maxBy { it.y - it.x })

    /** Warps the quad (bitmap pixel coords, ordered TL,TR,BR,BL) into a flat rectangle. */
    fun warp(bitmap: Bitmap, quad: List<Point>): Bitmap {
        val (tl, tr, br, bl) = quad
        val w = max(dist(br, bl), dist(tr, tl)).toInt().coerceAtLeast(1)
        val h = max(dist(tr, br), dist(tl, bl)).toInt().coerceAtLeast(1)
        val src = Mat().also { Utils.bitmapToMat(bitmap, it) }
        val m = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(tl, tr, br, bl),
            MatOfPoint2f(Point(0.0, 0.0), Point(w - 1.0, 0.0), Point(w - 1.0, h - 1.0), Point(0.0, h - 1.0)))
        val dst = Mat()
        Imgproc.warpPerspective(src, dst, m, Size(w.toDouble(), h.toDouble()), Imgproc.INTER_CUBIC)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(dst, it) }
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
