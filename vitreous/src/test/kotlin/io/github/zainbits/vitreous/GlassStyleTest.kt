package io.github.zainbits.vitreous

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassStyleTest {
    @Test
    fun clarityRaisesTint() {
        var last = -1f
        for (i in 0..10) {
            val s = GlassStyle.forClarity(i / 10f, dark = false)
            assertTrue("tint must not fall at ${i / 10f}", s.tintAlpha >= last)
            last = s.tintAlpha
        }
    }

    @Test
    fun fullyTintedSkipsBlur() {
        val s = GlassStyle.forClarity(1f, dark = true)
        assertEquals(0.dp, s.blurRadius)
        assertEquals(1f, s.tintAlpha)
    }

    @Test
    fun clarityIsClamped() {
        assertEquals(GlassStyle.forClarity(0f, false), GlassStyle.forClarity(-3f, false))
        assertEquals(GlassStyle.forClarity(1f, false), GlassStyle.forClarity(9f, false))
    }
}
