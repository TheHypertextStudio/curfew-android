package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import studio.hypertext.curfew.R

class LauncherIconInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun render(drawable: android.graphics.drawable.Drawable): Bitmap =
        Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, 108, 108)
            drawable.draw(Canvas(it))
        }

    @Test fun renderLauncherMaskReview() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val sheet = Bitmap.createBitmap(960, 340, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(250, 247, 240))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val labels = listOf("Circle", "Squircle", "Rounded square", "Themed")
        for (index in 0..3) {
            val left = 40f + index * 235f
            val top = 56f
            val rect = android.graphics.RectF(left, top, left + 180f, top + 180f)
            val path = android.graphics.Path()
            if (index == 0 || index == 3) path.addOval(rect, android.graphics.Path.Direction.CW)
            else if (index == 1) {
                path.moveTo(left + 90f, top)
                path.cubicTo(left + 170f, top, left + 180f, top + 10f, left + 180f, top + 90f)
                path.cubicTo(left + 180f, top + 170f, left + 170f, top + 180f, left + 90f, top + 180f)
                path.cubicTo(left + 10f, top + 180f, left, top + 170f, left, top + 90f)
                path.cubicTo(left, top + 10f, left + 10f, top, left + 90f, top)
                path.close()
            } else path.addRoundRect(rect, 36f, 36f, android.graphics.Path.Direction.CW)
            canvas.save()
            canvas.clipPath(path)
            canvas.translate(left - 45f, top - 45f)
            canvas.scale(2.5f, 2.5f)
            if (index == 3) {
                canvas.drawColor(Color.rgb(225, 221, 245))
                val mono = requireNotNull(icon.monochrome)
                mono.setTint(Color.rgb(68, 48, 96))
                mono.setBounds(0, 0, 108, 108)
                mono.draw(canvas)
            } else {
                icon.background.setBounds(0, 0, 108, 108)
                icon.background.draw(canvas)
                icon.foreground.setBounds(0, 0, 108, 108)
                icon.foreground.draw(canvas)
            }
            canvas.restore()
            paint.color = Color.rgb(43, 36, 31)
            paint.textSize = 20f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText(labels[index], left + 90f, 282f, paint)
        }
        context.openFileOutput("launcher-icon-review.png", Context.MODE_PRIVATE).use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun launcherUsesCurfewSunWithFullBleedDuskAndDedicatedThemedArt() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val foreground = render(icon.foreground)
        assertEquals("The sun keeps Curfew's orange", Color.rgb(242, 138, 57), foreground.getPixel(54, 56))
        assertEquals("No baked-in Apple tile surrounds the sun", 0, Color.alpha(foreground.getPixel(0, 0)))
        assertEquals("The horizon makes the setting sun recognizable", 255, Color.alpha(foreground.getPixel(54, 65)))
        assertEquals("The sun and horizon retain a clear gap", 0, Color.alpha(foreground.getPixel(54, 61)))
        val background = render(icon.background)
        assertEquals(255, Color.alpha(background.getPixel(0, 0)))
        assertEquals(255, Color.alpha(background.getPixel(107, 107)))
        assertNotEquals("Dusk retains its vertical color transition", background.getPixel(54, 0), background.getPixel(54, 107))
        val mono = render(requireNotNull(icon.monochrome))
        assertEquals(255, Color.alpha(mono.getPixel(54, 56)))
        assertEquals(0, Color.alpha(mono.getPixel(54, 25)))
        for (y in 0 until 108) for (x in 0 until 108) {
            if (Color.alpha(foreground.getPixel(x, y)) > 0) {
                assertTrue("The sun must survive every launcher mask", (x - 54) * (x - 54) + (y - 54) * (y - 54) <= 33 * 33)
            }
            assertEquals("Themed art preserves the sun silhouette", Color.alpha(foreground.getPixel(x, y)), Color.alpha(mono.getPixel(x, y)))
        }
    }
}
