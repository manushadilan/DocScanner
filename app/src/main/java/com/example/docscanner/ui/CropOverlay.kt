package com.example.docscanner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

private const val HIT = 90f
private val Blue = Color(0xFF2196F3)

/**
 * Flexible crop editor. [corners] are NORMALIZED (0..1), order TL,TR,BR,BL.
 * - drag a corner (free) or an edge handle (moves that whole edge)
 * - drag inside the shape to move it
 * - a 4x4 grid shows how the page will be flattened; a loupe magnifies the handle under your finger
 */
@Composable
fun CropOverlay(bitmap: Bitmap, corners: List<Offset>, onChange: (List<Offset>) -> Unit, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val cur by rememberUpdatedState(corners)      // keeps the gesture alive while state changes
    val notify by rememberUpdatedState(onChange)
    var active by remember { mutableIntStateOf(-1) } // 0-3 corner, 4-7 edge, 8 whole shape

    Box(modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { p ->
                    val px = cur.map { Offset(it.x * size.width, it.y * size.height) }
                    val handles = px + px.indices.map { lerp(px[it], px[(it + 1) % 4], .5f) }
                    val i = handles.indices.minBy { (handles[it] - p).getDistance() }
                    active = when {
                        (handles[i] - p).getDistance() < HIT -> i
                        inside(px, p) -> 8
                        else -> -1
                    }
                },
                onDragEnd = { active = -1 },
                onDragCancel = { active = -1 },
                onDrag = { change, d ->
                    if (active < 0) return@detectDragGestures
                    change.consume()
                    val dx = d.x / size.width; val dy = d.y / size.height
                    val c = cur
                    notify(when (active) {
                        in 0..3 -> c.mapIndexed { i, o -> if (i == active) o.shift(dx, dy) else o }
                        in 4..7 -> { val a = active - 4; val b = (a + 1) % 4
                            c.mapIndexed { i, o -> if (i == a || i == b) o.shift(dx, dy) else o } }
                        else -> { // move all four, keeping the shape inside the image
                            val mx = dx.coerceIn(-c.minOf { it.x }, 1f - c.maxOf { it.x })
                            val my = dy.coerceIn(-c.minOf { it.y }, 1f - c.maxOf { it.y })
                            c.map { it.shift(mx, my) }
                        }
                    })
                })
        }) {
            val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
            val quad = Path().apply { moveTo(pts[0].x, pts[0].y); for (k in 1..3) lineTo(pts[k].x, pts[k].y); close() }
            // dim everything outside the crop
            drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(Offset.Zero, size)); addPath(quad) }, Color(0x99000000))
            // 4x4 grid inside the quad
            val g = Color.White.copy(alpha = .6f)
            for (k in 1..3) {
                val t = k / 4f
                drawLine(g, lerp(pts[0], pts[1], t), lerp(pts[3], pts[2], t), 2f)
                drawLine(g, lerp(pts[0], pts[3], t), lerp(pts[1], pts[2], t), 2f)
            }
            drawPath(quad, Blue, style = Stroke(4f))
            for (i in 0..3) { // edge handles
                val m = lerp(pts[i], pts[(i + 1) % 4], .5f)
                drawCircle(Color.White, 16f, m); drawCircle(Blue, if (active == 4 + i) 12f else 9f, m)
            }
            pts.forEachIndexed { i, p -> drawCircle(Color.White, 24f, p); drawCircle(Blue, if (i == active) 19f else 15f, p) }

            if (active in 0..7) { // loupe, parked on the side away from the finger
                val pt = if (active < 4) pts[active] else lerp(pts[active - 4], pts[(active - 3) % 4], .5f)
                val box = 240f
                val left = if (pt.x < size.width / 2) size.width - box - 16f else 16f
                val centre = Offset(left + box / 2, 16f + box / 2)
                val s = minOf(80, bitmap.width, bitmap.height)
                val bx = (pt.x / size.width * bitmap.width).toInt(); val by = (pt.y / size.height * bitmap.height).toInt()
                val src = IntOffset((bx - s / 2).coerceIn(0, bitmap.width - s), (by - s / 2).coerceIn(0, bitmap.height - s))
                clipPath(Path().apply { addOval(Rect(centre, box / 2)) }) {
                    drawImage(image, src, IntSize(s, s), IntOffset(left.toInt(), 16), IntSize(box.toInt(), box.toInt()))
                }
                drawCircle(Color.White, box / 2, centre, style = Stroke(4f))
                drawLine(Blue, Offset(centre.x - 14f, centre.y), Offset(centre.x + 14f, centre.y), 3f)
                drawLine(Blue, Offset(centre.x, centre.y - 14f), Offset(centre.x, centre.y + 14f), 3f)
            }
        }
    }
}

private fun Offset.shift(dx: Float, dy: Float) = Offset((x + dx).coerceIn(0f, 1f), (y + dy).coerceIn(0f, 1f))

private fun inside(poly: List<Offset>, p: Offset): Boolean { // ray casting
    var c = false; var j = poly.lastIndex
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) c = !c
        j = i
    }
    return c
}
