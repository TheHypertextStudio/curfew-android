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
        try {
            return QRCodeReader().decode(image, mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (_: com.google.zxing.ReaderException) {
            // Pure extraction needs upright finder patterns; camera frames may be rotated.
            // Keep normal perspective detection first, then try each pure orientation.
        }
        var pixels = luminance
        var frameWidth = width
        var frameHeight = height
        var candidate = image
        repeat(4) { turn ->
            try { return QRCodeReader().decode(candidate, mapOf(DecodeHintType.PURE_BARCODE to true)).text }
            catch (_: com.google.zxing.ReaderException) { /* Try the next orientation. */ }
            if (turn < 3) {
                val previous = pixels
                val previousWidth = frameWidth
                val previousHeight = frameHeight
                pixels = ByteArray(previous.size) { index ->
                    val x = index % previousHeight
                    val y = index / previousHeight
                    previous[(previousHeight - 1 - x) * previousWidth + y]
                }
                frameWidth = previousHeight
                frameHeight = previousWidth
                candidate = BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(pixels, frameWidth, frameHeight, 0, 0, frameWidth, frameHeight, false)))
            }
        }
        return null
    }
}
