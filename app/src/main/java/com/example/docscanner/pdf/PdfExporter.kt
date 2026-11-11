package com.example.docscanner.pdf

import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream

object PdfExporter {
    private const val PAGE_W = 595 // A4 width in points; page height follows each image's aspect ratio

    /** Loads one page image at a time (low memory), draws it full-bleed, streams the PDF to [out]. */
    fun export(imageFiles: List<File>, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            imageFiles.forEachIndexed { i, file ->
                val bmp = BitmapFactory.decodeFile(file.path) ?: return@forEachIndexed
                val pageH = (PAGE_W * bmp.height.toFloat() / bmp.width).toInt()
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, pageH, i + 1).create())
                page.canvas.drawBitmap(bmp, null, Rect(0, 0, PAGE_W, pageH), Paint(Paint.FILTER_BITMAP_FLAG))
                pdf.finishPage(page)
                bmp.recycle()
            }
            pdf.writeTo(out)
        } finally {
            pdf.close(); out.flush()
        }
    }
}
