package com.ardia.browser

import org.junit.Assert.assertTrue
import org.junit.Test

class PageBottomInsetTest {
    @Test
    fun formatsInsetViewportAndBlurForDocumentStartAndUpdates() {
        val start = PageBottomInset.script(96.0, 840.5, blurEnabled = true)
        assertTrue(start.contains("window.__ardiaSetBottomInset(96.00,840.50,true)"))
        assertTrue(start.contains("scroll-padding-bottom"))
        assertTrue(start.contains("backdrop-filter"))
        assertTrue(start.contains("elementsFromPoint"))

        val update = PageBottomInset.updateCall(-12.0, 800.0, blurEnabled = false)
        assertTrue(update.contains("window.__ardiaSetBottomInset(0.00,800.00,false)"))
    }
}
