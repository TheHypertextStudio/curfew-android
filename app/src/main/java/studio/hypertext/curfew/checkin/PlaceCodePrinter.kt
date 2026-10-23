package studio.hypertext.curfew.checkin

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import android.content.Context
import java.io.FileOutputStream

class PlaceCodePrinter(private val context: Context, private val bitmap: Bitmap) : PrintDocumentAdapter() {
    private var attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal,
                          callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        attributes = newAttributes
        callback.onLayoutFinished(PrintDocumentInfo.Builder("curfew-check-in.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(), newAttributes != oldAttributes)
    }
    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
        val document = PrintedPdfDocument(context, attributes)
        try {
            val page = document.startPage(0)
            val area = page.info.contentRect
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f }
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("Curfew check-in", area.left.toFloat(), area.top + 28f, paint)
            val size = minOf(area.width(), area.height() - 140)
            require(size >= 100)
            val left = area.left + (area.width() - size) / 2
            page.canvas.drawBitmap(bitmap, null, Rect(left, area.top + 50, left + size, area.top + 50 + size), null)
            paint.textSize = 12f
            page.canvas.drawText("Keep this code away from bed. Scan it when Sunrise rings.", area.left.toFloat(), area.top + size + 78f, paint)
            page.canvas.drawText("Anyone with a copy can check in. Keep it private.", area.left.toFloat(), area.top + size + 98f, paint)
            document.finishPage(page)
            if (cancellationSignal.isCanceled) callback.onWriteCancelled()
            else {
                FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        } catch (_: Exception) { callback.onWriteFailed("Couldn’t prepare this page. Try saving the code as an image.") }
        finally { document.close() }
    }
    override fun onFinish() { bitmap.recycle() }
}
