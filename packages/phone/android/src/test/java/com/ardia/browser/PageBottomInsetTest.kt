package com.ardia.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBottomInsetTest {
    @Test
    fun formatsInsetAndViewportForDocumentStartAndUpdates() {
        val start = PageBottomInset.script(96.0, 840.5)
        assertTrue(start.contains("window.__ardiaSetBottomInset(96.00,840.50)"))
        assertTrue(start.contains("scroll-padding-bottom"))
        assertTrue(start.contains("__ardia_bottom_apron"))
        assertTrue(start.contains("elementsFromPoint"))
        assertFalse(start.contains("__ardia_bottom_blur"))

        val update = PageBottomInset.updateCall(-12.0, 800.0)
        assertTrue(update.contains("window.__ardiaSetBottomInset(0.00,800.00)"))
    }
}
