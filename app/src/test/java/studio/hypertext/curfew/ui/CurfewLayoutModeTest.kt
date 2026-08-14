package studio.hypertext.curfew.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CurfewLayoutModeTest {
    @Test
    fun `layout adapts across phone tablet foldable and desktop windows`() {
        assertEquals(CurfewLayoutMode.COMPACT, CurfewLayoutMode.forWidthDp(360))
        assertEquals(CurfewLayoutMode.MEDIUM, CurfewLayoutMode.forWidthDp(700))
        assertEquals(CurfewLayoutMode.EXPANDED, CurfewLayoutMode.forWidthDp(1_200))
    }
}
