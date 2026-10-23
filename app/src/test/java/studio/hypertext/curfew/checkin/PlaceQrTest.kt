package studio.hypertext.curfew.checkin

import org.junit.Assert.*
import org.junit.Test

class PlaceQrTest {
    @Test fun `printed code roundtrips through actual offline QR codec`() {
        val draft = PlaceCheckIn.create("Bathroom")
        val matrix = PlaceQr.encode(draft.payload)
        val pixels = ByteArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) 0 else 255.toByte() }
        assertEquals(draft.payload, PlaceQr.decode(pixels, matrix.width, matrix.height))
    }
    @Test fun `camera rotation does not change the scanned identity`() {
        val draft = PlaceCheckIn.create("Kitchen")
        val matrix = PlaceQr.encode(draft.payload, 320)
        val pixels = ByteArray(matrix.width * matrix.height) { index -> if (matrix[index / matrix.width, matrix.height - 1 - index % matrix.width]) 0 else 255.toByte() }
        assertEquals(draft.payload, PlaceQr.decode(pixels, matrix.width, matrix.height))
    }
    @Test fun `unreadable frame supplies no code`() {
        assertNull(PlaceQr.decode(ByteArray(320 * 240) { 255.toByte() }, 320, 240))
    }
    @Test fun `dense printed fixture is detected at export resolution`() {
        val payload = "curfew:place:1:c2146807-f9be-475e-a686-d91127f483a4:dwO3NexnVtF1vR2U8vSORVd7U6KNrU8QT0P1F1CEcCA"
        val matrix = PlaceQr.encode(payload)
        val pixels = ByteArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) 0 else 255.toByte() }
        val image = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(com.google.zxing.PlanarYUVLuminanceSource(pixels, matrix.width, matrix.height, 0, 0, matrix.width, matrix.height, false)))
        assertEquals(payload, com.google.zxing.qrcode.QRCodeReader().decode(image, mapOf(com.google.zxing.DecodeHintType.PURE_BARCODE to true)).text)
        assertEquals(payload, PlaceQr.decode(pixels, matrix.width, matrix.height))
    }
}
