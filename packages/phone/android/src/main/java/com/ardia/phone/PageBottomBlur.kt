package com.ardia.phone

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.ceil

@RequiresApi(Build.VERSION_CODES.S)
internal class PageBottomBlur(density: Float) {
    private val scale = 2f
    private val radius = 18f * density
    private val padding = ceil(radius * 2).toInt()
    private val feather = ceil(12f * density).toInt()
    private val node = RenderNode("ardiaPageBottomBlur").apply {
        setRenderEffect(RenderEffect.createBlurEffect(radius / scale, radius / scale, Shader.TileMode.CLAMP))
    }
    private val mask = Paint().apply { blendMode = BlendMode.DST_IN }
    private var maskEdge = -1
    private var maskTop = -1

    fun clear() = node.discardDisplayList()

    fun draw(canvas: Canvas, width: Int, pageTop: Int, pageEdge: Int, bandHeight: Int, drawPage: (Canvas) -> Unit) {
        val transitionTop = maxOf(pageTop, pageEdge - feather)
        if (transitionTop >= pageEdge) return
        val bottom = pageEdge + bandHeight
        val recordingTop = maxOf(pageTop, transitionTop - padding)
        val recordingBottom = minOf(bottom + padding, pageEdge + (pageEdge - pageTop))
        val recordingHeight = ceil((recordingBottom - recordingTop) / scale).toInt()
        val recordingWidth = ceil(width / scale).toInt()
        node.setPosition(0, 0, recordingWidth, recordingHeight)
        val recording = node.beginRecording()
        try {
            recording.scale(1 / scale, 1 / scale)
            recording.translate(0f, -recordingTop.toFloat())
            val original = recording.save()
            recording.clipRect(0, recordingTop, width, pageEdge)
            drawPage(recording)
            recording.restoreToCount(original)
            recording.clipRect(0, pageEdge, width, recordingBottom)
            recording.translate(0f, 2f * pageEdge)
            recording.scale(1f, -1f)
            drawPage(recording)
        } finally {
            node.endRecording()
        }
        LookActivity.blurLayerRenders += 1

        if (maskEdge != pageEdge || maskTop != transitionTop) {
            maskEdge = pageEdge
            maskTop = transitionTop
            mask.shader = LinearGradient(0f, transitionTop.toFloat(), 0f, pageEdge.toFloat(),
                intArrayOf(Color.TRANSPARENT, 0x40000000, Color.BLACK), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        }
        val drawBottom = minOf(bottom, recordingBottom)
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
