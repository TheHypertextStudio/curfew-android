package studio.hypertext.curfew.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CurfewLayoutModeTest {
    @Test
    fun `expanded navigation requires room for scaled labels and short windows`() {
        assertEquals(CurfewLayoutMode.MEDIUM, CurfewLayoutMode.forWindow(840, 360, 1f))
        assertEquals(CurfewLayoutMode.MEDIUM, CurfewLayoutMode.forWindow(840, 700, 2f))
        assertEquals(CurfewLayoutMode.EXPANDED, CurfewLayoutMode.forWindow(1200, 700, 1.5f))
        assertEquals(CurfewLayoutMode.COMPACT, CurfewLayoutMode.forWindow(320, 700, 2f))
    }

    @Test
    fun `layout adapts across phone tablet foldable and desktop windows`() {
        assertEquals(CurfewLayoutMode.COMPACT, CurfewLayoutMode.forWidthDp(360))
        assertEquals(CurfewLayoutMode.MEDIUM, CurfewLayoutMode.forWidthDp(700))
        assertEquals(CurfewLayoutMode.EXPANDED, CurfewLayoutMode.forWidthDp(1_200))
    }
}
