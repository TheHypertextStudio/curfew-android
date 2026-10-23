package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
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
        val sheet = Bitmap.createBitmap(960, 620, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(250, 247, 240))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val labels = listOf("Circle", "Squircle", "Rounded square", "Teardrop")
        for (index in 0..7) {
            val column = index % 4
            val themed = index >= 4
            val left = 40f + column * 235f
            val top = if (themed) 346f else 56f
            val rect = android.graphics.RectF(left, top, left + 180f, top + 180f)
            val path = android.graphics.Path()
            if (column == 0) path.addOval(rect, android.graphics.Path.Direction.CW)
            else if (column == 1) {
                path.moveTo(left + 90f, top)
                path.cubicTo(left + 170f, top, left + 180f, top + 10f, left + 180f, top + 90f)
                path.cubicTo(left + 180f, top + 170f, left + 170f, top + 180f, left + 90f, top + 180f)
                path.cubicTo(left + 10f, top + 180f, left, top + 170f, left, top + 90f)
                path.cubicTo(left, top + 10f, left + 10f, top, left + 90f, top)
                path.close()
            } else if (column == 2) path.addRoundRect(rect, 36f, 36f, android.graphics.Path.Direction.CW)
            else {
                path.moveTo(left + 90f, top)
                path.cubicTo(left + 140f, top, left + 180f, top + 40f, left + 180f, top + 90f)
                path.lineTo(left + 180f, top + 180f)
                path.lineTo(left + 90f, top + 180f)
                path.cubicTo(left + 40f, top + 180f, left, top + 140f, left, top + 90f)
                path.cubicTo(left, top + 40f, left + 40f, top, left + 90f, top)
                path.close()
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.translate(left - 45f, top - 45f)
            canvas.scale(2.5f, 2.5f)
            if (themed) {
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
            canvas.drawText(if (themed) "${labels[column]} · themed" else labels[column], left + 90f, if (themed) 572f else 282f, paint)
        }
        context.openFileOutput("launcher-icon-review.png", Context.MODE_PRIVATE).use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun launcherPreservesTheMacFullSunAndSourceProportions() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val foreground = render(icon.foreground)
        assertEquals("The Mac sun stays a complete disc, including its lower half", 255, Color.alpha(foreground.getPixel(54, 68)))
        assertEquals("The sun keeps the source asset's orange", Color.rgb(238, 135, 59), foreground.getPixel(54, 56))
        assertEquals("No invented horizon surrounds the Mac sun", 0, Color.alpha(foreground.getPixel(30, 65)))
        assertEquals("No baked-in Apple tile surrounds the sun", 0, Color.alpha(foreground.getPixel(0, 0)))
        val mono = render(requireNotNull(icon.monochrome))
        var weight = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        var left = 108
        var right = -1
        var top = 108
        var bottom = -1
        for (y in 0 until 108) for (x in 0 until 108) {
            val alpha = Color.alpha(foreground.getPixel(x, y))
            if (alpha > 0) {
                assertTrue("The complete sun must survive launcher masks", (x - 54) * (x - 54) + (y - 54) * (y - 54) <= 33 * 33)
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
                weight += alpha
                weightedX += (x + .5) * alpha
                weightedY += (y + .5) * alpha
            }
            assertEquals("Themed art preserves the full disc silhouette", alpha, Color.alpha(mono.getPixel(x, y)))
        }
        assertEquals("The sun stays horizontally centered", 54.0, weightedX / weight, .5)
        assertEquals("The source sun retains its slight vertical offset", 56.32, weightedY / weight, .5)
        assertTrue("The source radius maps to about 14.77dp in Android's full layer", right - left in 28..31 && bottom - top in 28..31)
    }

    @Test fun backgroundPreservesMacDuskWithoutAnInventedLowerBand() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val background = render(icon.background)
        assertEquals(255, Color.alpha(background.getPixel(0, 0)))
        assertEquals(255, Color.alpha(background.getPixel(107, 107)))
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
        val sourceTop = Color.valueOf(.30f, .24f, .42f, 1f, p3).convert(srgb).toArgb()
        val sourceBottom = Color.valueOf(.93f, .46f, .22f, 1f, p3).convert(srgb).toArgb()
        for ((reference, actual) in listOf(sourceTop to background.getPixel(54, 0), sourceBottom to background.getPixel(54, 107))) {
            assertTrue("Android preserves the Mac Display P3 gradient as sRGB", kotlin.math.abs(Color.red(reference) - Color.red(actual)) <= 2 && kotlin.math.abs(Color.green(reference) - Color.green(actual)) <= 2 && kotlin.math.abs(Color.blue(reference) - Color.blue(actual)) <= 2)
        }
        assertNotEquals(background.getPixel(54, 18), background.getPixel(54, 90))
        val before = background.getPixel(54, 68)
        val after = background.getPixel(54, 69)
        assertTrue("The Mac background remains one continuous gradient", kotlin.math.abs(Color.red(after) - Color.red(before)) < 12 && kotlin.math.abs(Color.green(after) - Color.green(before)) < 12 && kotlin.math.abs(Color.blue(after) - Color.blue(before)) < 12)
    }
}
