package studio.hypertext.curfew.checkin

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter

object PlaceQr {
    fun encode(payload: String, size: Int = 512): BitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        val image = BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)))
        return try {
            QRCodeReader().decode(image, mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (_: com.google.zxing.ReaderException) {
            // A perfectly rendered print may defeat finder detection despite valid bits.
            // The pure reader still validates the QR; enrollment matching remains mandatory.
            try { QRCodeReader().decode(image, mapOf(DecodeHintType.PURE_BARCODE to true)).text }
            catch (_: com.google.zxing.ReaderException) { null }
        }
    }
}
