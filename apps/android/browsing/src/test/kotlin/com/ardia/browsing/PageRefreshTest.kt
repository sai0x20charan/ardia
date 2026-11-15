package com.ardia.browsing

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ardia.browser.FindResult
import com.ardia.browser.FullscreenVideo
import com.ardia.browser.Page
import com.ardia.browser.PageEvent
import com.ardia.browser.PageRequest
import com.ardia.browser.PageState
import com.ardia.browser.PageTone
import com.ardia.browser.ReadableContent
import com.ardia.browser.ReadableContentException
import com.ardia.design.ArdiaTheme
import com.ardia.store.SwipeNavigationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class RefreshTestPage(
    private val onReload: (RefreshTestPage) -> Unit = {},
) : Page {
    override val id: String = "refresh-tab"
    override val isPrivate: Boolean = false
    val mutableState = MutableStateFlow(PageState(url = "https://example.com/", title = "Example", isLive = true))
    var reloadCount: Int = 0
    override val state: StateFlow<PageState> = mutableState
    override val view: StateFlow<View?> = MutableStateFlow(null)
    override val request: StateFlow<PageRequest?> = MutableStateFlow(null)
    override val find: StateFlow<FindResult?> = MutableStateFlow(null)
    override val fullscreen: StateFlow<FullscreenVideo?> = MutableStateFlow(null)
    override val events: SharedFlow<PageEvent> = MutableSharedFlow()

    override fun load(url: String) = Unit

    override fun reload() {
        reloadCount += 1
        onReload(this)
    }

    override fun stop() = Unit
    override fun goBack(): Boolean = false
    override fun goForward(): Boolean = false
    override fun find(text: String) = Unit
    override fun findNext() = Unit
    override fun findPrevious() = Unit
    override fun endFind() = Unit
    override fun setPageZoom(zoom: Double) = Unit
    override fun zoomIn() = Unit
    override fun zoomOut() = Unit
    override fun setDesktopSite(enabled: Boolean) = Unit
    override fun download(url: String) = Unit
    override suspend fun readableContent(): ReadableContent = throw ReadableContentException(ReadableContentException.Reason.NotReadable)
    override suspend fun snapshot(maxWidth: Int): Bitmap? = null
    override suspend fun sampleTone(topInset: Int, bottomInset: Int): PageTone? = null
    override fun saveState(): ByteArray? = null
    override fun close() = Unit
}

@RunWith(RobolectricTestRunner::class)
class PageRefreshTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun step(totalMillis: Long) {
        val frame = 16L
        var elapsed = 0L
        while (elapsed < totalMillis) {
            compose.mainClock.advanceTimeBy(frame)
            elapsed += frame
        }
        compose.waitForIdle()
    }

    @Test
    fun fastReloadClearsRefreshIndicatorBeforeTimeout() {
        compose.mainClock.autoAdvance = false
        lateinit var testScope: CoroutineScope
        val page = RefreshTestPage { p ->
            testScope.launch {
                delay(16L)
                p.mutableState.value = p.mutableState.value.copy(isLoading = true)
                delay(48L)
                p.mutableState.value = p.mutableState.value.copy(isLoading = false)
            }
        }
        lateinit var gestures: PageGestures
        compose.setContent {
            ArdiaTheme {
                val scope = rememberCoroutineScope()
                testScope = scope
                gestures = remember { PageGestures(scope) }
                Box(Modifier.size(400.dp)) {
                    RefreshIndicator(gestures, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        compose.runOnUiThread {
            gestures.followPull(300f)
            gestures.finishPull(page)
        }
        step(1_000L)
        assertEquals(1, page.reloadCount)
        assertFalse(gestures.isRefreshing)
        assertEquals(0f, gestures.pull, 0.01f)
        assertEquals(0, compose.onAllNodesWithContentDescription("Reloading").fetchSemanticsNodes().size)
    }

    @Test
    fun reloadThatNeverStartsLoadingClearsAfterStartGraceWindow() {
        compose.mainClock.autoAdvance = false
        val page = RefreshTestPage()
        lateinit var gestures: PageGestures
        compose.setContent {
            ArdiaTheme {
                val scope = rememberCoroutineScope()
                gestures = remember { PageGestures(scope) }
                Box(Modifier.size(400.dp)) {
                    RefreshIndicator(gestures, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        compose.runOnUiThread {
            gestures.followPull(300f)
            gestures.finishPull(page)
        }
        step(PageGestures.LOAD_START_TIMEOUT_MILLIS + 600L)
        assertEquals(1, page.reloadCount)
        assertFalse(gestures.isRefreshing)
        assertEquals(0f, gestures.pull, 0.01f)
        assertEquals(0, compose.onAllNodesWithContentDescription("Reloading").fetchSemanticsNodes().size)
    }

    @Test
    fun slowReloadHoldsIndicatorUntilLoadingCompletesThenClears() {
        compose.mainClock.autoAdvance = false
        lateinit var testScope: CoroutineScope
        val page = RefreshTestPage { p ->
            testScope.launch {
                delay(32L)
                p.mutableState.value = p.mutableState.value.copy(isLoading = true)
                delay(1_800L)
                p.mutableState.value = p.mutableState.value.copy(isLoading = false)
            }
        }
        lateinit var gestures: PageGestures
        compose.setContent {
            ArdiaTheme {
                val scope = rememberCoroutineScope()
                testScope = scope
                gestures = remember { PageGestures(scope) }
                Box(Modifier.size(400.dp)) {
                    RefreshIndicator(gestures, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        compose.runOnUiThread {
            gestures.followPull(300f)
            gestures.finishPull(page)
        }
        step(1_200L)
        assertTrue(gestures.isRefreshing)
        assertEquals(PageGestures.REFRESH_HOLD_DP, gestures.pull, 0.5f)
        assertEquals(1, compose.onAllNodesWithContentDescription("Reloading").fetchSemanticsNodes().size)

        step(1_400L)
        assertFalse(gestures.isRefreshing)
        assertEquals(0f, gestures.pull, 0.01f)
        assertEquals(0, compose.onAllNodesWithContentDescription("Reloading").fetchSemanticsNodes().size)
    }

    @Test
    fun cancelledPullDoesNotReloadAndResetsPull() {
        compose.mainClock.autoAdvance = false
        val page = RefreshTestPage()
        lateinit var gestures: PageGestures
        compose.setContent {
            ArdiaTheme {
                val scope = rememberCoroutineScope()
                gestures = remember { PageGestures(scope) }
            }
        }
        compose.runOnUiThread {
            gestures.followPull(300f)
            gestures.finishPull(page, allowTrigger = false)
        }
        step(600L)
        assertEquals(0, page.reloadCount)
        assertFalse(gestures.isRefreshing)
        assertEquals(0f, gestures.pull, 0.01f)
    }

    @Test
    fun followPullCancelsPendingRetractionAnimation() {
        compose.mainClock.autoAdvance = false
        val page = RefreshTestPage()
        lateinit var gestures: PageGestures
        compose.setContent {
            ArdiaTheme {
                val scope = rememberCoroutineScope()
                gestures = remember { PageGestures(scope) }
            }
        }
        compose.runOnUiThread {
            gestures.followPull(40f)
            gestures.finishPull(page)
        }
        step(64L)
        val expected = PullToRefreshRules.distance(220f)
        compose.runOnUiThread { gestures.followPull(220f) }
        step(500L)
        assertEquals(expected, gestures.pull, 0.01f)
    }

    @Test
    fun touchPullAndReleaseHidesIndicatorAfterFastReload() {
        compose.mainClock.autoAdvance = false
        lateinit var testScope: CoroutineScope
        val page = RefreshTestPage { p ->
            testScope.launch {
                delay(16L)
                p.mutableState.value = p.mutableState.value.copy(isLoading = true)
                delay(64L)
                p.mutableState.value = p.mutableState.value.copy(isLoading = false)
            }
        }
        lateinit var gestures: PageGestures
        compose.setContent {
            ArdiaTheme {
                val scope = rememberCoroutineScope()
                testScope = scope
                gestures = remember { PageGestures(scope) }
                Box(Modifier.size(400.dp)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("surface")
                            .pageGestures(gestures, page, SwipeNavigationMode.ANYWHERE, enabled = true, onPullTriggerCrossed = {}),
                    )
                    RefreshIndicator(gestures, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("surface").performTouchInput {
            down(Offset(200f, 50f))
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, 300f))
            up()
        }
        step(1_000L)
        assertEquals(1, page.reloadCount)
        assertFalse(gestures.isRefreshing)
        assertEquals(0f, gestures.pull, 0.01f)
        assertEquals(0, compose.onAllNodesWithContentDescription("Reloading").fetchSemanticsNodes().size)
    }
}
