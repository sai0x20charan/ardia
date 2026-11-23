package com.ardia.phone

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.webkit.WebView
import androidx.annotation.RequiresApi
import com.ardia.browser.PageCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

@RequiresApi(Build.VERSION_CODES.S)
internal class PageBottomBlur(density: Float, private val invalidate: () -> Unit) {
    private data class Source(val view: WebView, val region: Rect, val windowX: Int, val windowY: Int, val width: Int, val height: Int)

    private val scale = 2f
    private val radius = 18f * density
    private val padding = ceil(radius * 2).toInt()
    private val feather = ceil(12f * density).toInt()
    private val node = RenderNode("ardiaPageBottomBlur").apply {
        setRenderEffect(RenderEffect.createBlurEffect(radius / scale, radius / scale, Shader.TileMode.CLAMP))
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mappedStrip = RectF()
    private val location = IntArray(2)
    private var source: Source? = null
    private var committed: Source? = null
    private var pixels: Bitmap? = null
    private var pending: Job? = null
    private var capturedAt = 0L
    private var published = false
    private val mask = Paint().apply { blendMode = BlendMode.DST_IN }
    private var maskEdge = -1
    private var maskTop = -1
    private var navigationTop = -1
    private var fadeBottom = -1

    fun clear() {
        source = null
        committed = null
        pixels = null
        published = false
        node.discardDisplayList()
    }

    private fun canCapture(next: Source): Boolean {
        val view = next.view
        if (source !== next || !view.hasWindowFocus() || !PageCapture.windowCopyIsTrustworthy(view)) return false
        view.getLocationInWindow(location)
        return next.windowX == location[0] && next.windowY == location[1]
    }

    private fun awaitCleanFrame(next: Source, scope: CoroutineScope) {
        next.view.postVisualStateCallback(SystemClock.uptimeMillis(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (source !== next || !next.view.isAttachedToWindow) return
                next.view.viewTreeObserver.registerFrameCommitCallback {
                    if (source === next) {
                        committed = next
                        capture(next, scope)
                    }
                }
                next.view.invalidate()
            }
        })
    }

    private fun capture(next: Source, scope: CoroutineScope) {
        if (pending?.isActive == true || committed !== next || !canCapture(next)) return
        val interval = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 125L else 500L
        pending = scope.launch {
            delay((interval - (SystemClock.uptimeMillis() - capturedAt)).coerceAtLeast(1))
            if (!canCapture(next)) {
                pending = null
                if (source != null && source !== next) invalidate()
                return@launch
            }
            capturedAt = SystemClock.uptimeMillis()
            val strip = PageCapture.copyWindow(next.view, next.region, next.width, next.height)
            pending = null
            if (strip != null && canCapture(next)) {
                pixels = strip
                published = true
                invalidate()
            } else {
                strip?.recycle()
                if (source != null && source !== next) invalidate()
            }
        }
    }

    fun draw(canvas: Canvas, page: WebView, scope: CoroutineScope, pageTop: Int, pageEdge: Int, bandHeight: Int, navigationInset: Int) {
        val width = page.width
        val transitionTop = maxOf(pageTop, pageEdge - feather)
        if (transitionTop >= pageEdge) return
        val bottom = pageEdge + bandHeight
        val navTop = maxOf(pageEdge, bottom - navigationInset)
        val blurBottom = minOf(bottom, navTop + minOf(feather, navigationInset / 3))
        val fadesIntoNavigation = blurBottom > navTop
        val visibleBottom = if (fadesIntoNavigation) blurBottom else bottom
        val recordingTop = maxOf(pageTop, transitionTop - padding)
        val recordingBottom = minOf(visibleBottom + padding, pageEdge + (pageEdge - pageTop))
        val recordingHeight = ceil((recordingBottom - recordingTop) / scale).toInt()
        val recordingWidth = ceil(width / scale).toInt()
        val sampleBottom = transitionTop - pageTop
        val sampleHeight = minOf(sampleBottom, maxOf(pageEdge - recordingTop, recordingBottom - pageEdge))
        if (sampleHeight <= 0) return
        page.getLocationInWindow(location)
        val next = Source(page, Rect(0, sampleBottom - sampleHeight, width, sampleBottom), location[0], location[1], recordingWidth, ceil(sampleHeight / scale).toInt())
        val active = source
        if (active != next) {
            clear()
            source = next
            awaitCleanFrame(next, scope)
            return
        }
        if (!canCapture(active)) return
        if (published) published = false else capture(active, scope)
        val strip = pixels ?: return
        node.setPosition(0, 0, recordingWidth, recordingHeight)
        val recording = node.beginRecording()
        try {
            recording.scale(1 / scale, 1 / scale)
            recording.translate(0f, -recordingTop.toFloat())
            mappedStrip.set(0f, (pageEdge - sampleHeight).toFloat(), width.toFloat(), pageEdge.toFloat())
            recording.drawBitmap(strip, null, mappedStrip, bitmapPaint)
            recording.translate(0f, 2f * pageEdge)
            recording.scale(1f, -1f)
            recording.drawBitmap(strip, null, mappedStrip, bitmapPaint)
        } finally {
            node.endRecording()
        }
        LookActivity.blurLayerRenders += 1

        if (maskEdge != pageEdge || maskTop != transitionTop || navigationTop != navTop || fadeBottom != blurBottom) {
            maskEdge = pageEdge
            maskTop = transitionTop
            navigationTop = navTop
            fadeBottom = blurBottom
            val gradientBottom = if (fadesIntoNavigation) blurBottom else pageEdge
            val span = (gradientBottom - transitionTop).toFloat()
            val featherStop = (pageEdge - transitionTop) / span
            val colours = if (fadesIntoNavigation) {
                intArrayOf(Color.TRANSPARENT, 0x40000000, Color.BLACK, Color.BLACK, Color.TRANSPARENT)
            } else {
                intArrayOf(Color.TRANSPARENT, 0x40000000, Color.BLACK)
            }
            val stops = if (fadesIntoNavigation) {
                floatArrayOf(0f, featherStop * 0.65f, featherStop, (navTop - transitionTop) / span, 1f)
            } else {
                floatArrayOf(0f, 0.65f, 1f)
            }
            mask.shader = LinearGradient(0f, transitionTop.toFloat(), 0f, gradientBottom.toFloat(), colours, stops, Shader.TileMode.CLAMP)
        }
        val drawBottom = minOf(visibleBottom, recordingBottom)
        val layer = canvas.saveLayer(0f, transitionTop.toFloat(), width.toFloat(), drawBottom.toFloat(), null)
        val position = canvas.save()
        canvas.translate(0f, recordingTop.toFloat())
        canvas.scale(scale, scale)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(position)
        canvas.drawRect(0f, transitionTop.toFloat(), width.toFloat(), drawBottom.toFloat(), mask)
        canvas.restoreToCount(layer)
    }
}
