package studio.hypertext.curfew.checkin

import android.graphics.Bitmap
import android.graphics.Color

object PlaceCodeImage {
    fun create(payload: String, size: Int = 900): Bitmap {
        val matrix = PlaceQr.encode(payload, size)
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}
