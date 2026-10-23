package studio.hypertext.curfew.checkin

import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test

class PlaceCodePrinterInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun attributes(media: PrintAttributes.MediaSize) = PrintAttributes.Builder()
        .setMediaSize(media).setResolution(PrintAttributes.Resolution("fixture", "Fixture", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build()

    @Test fun smallPaperFailsWithoutLeavingAnUnfinishedPdfPage() {
        val image = PlaceCodeImage.create(PlaceCheckIn.create("Fixture bathroom").payload)
        val printer = PlaceCodePrinter(context, image)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                printer.renderPage(attributes(PrintAttributes.MediaSize("small", "Small", 1000, 1000)), ByteArrayOutputStream(), CancellationSignal())
            }
        } finally { printer.onFinish() }
    }

    @Test fun a4ExportProducesOneReadablePdfPage() {
        val file = java.io.File.createTempFile("place-print-", ".pdf", context.cacheDir)
        val printer = PlaceCodePrinter(context, PlaceCodeImage.create(PlaceCheckIn.create("Fixture bathroom").payload))
        try {
            FileOutputStream(file).use { assertTrue(printer.renderPage(attributes(PrintAttributes.MediaSize.ISO_A4), it, CancellationSignal())) }
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertEquals(1, renderer.pageCount)
                if (InstrumentationRegistry.getArguments().getString("curfew.print.capture") == "true") {
                    renderer.openPage(0).use { page ->
                        val preview = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                        try {
                            preview.eraseColor(android.graphics.Color.WHITE)
                            page.render(preview, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            context.cacheDir.resolve("place-print-review.png").outputStream().use { preview.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        } finally { preview.recycle() }
                    }
                }
            }
        } finally { printer.onFinish(); file.delete() }
    }
}
