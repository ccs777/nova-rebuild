package dev.novare.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

/** 2x2 preview of a folder's first four icons, drawn as one drawable for the folder icon. */
class FolderPreviewDrawable(
    private val children: List<Drawable>,
    private val resources: android.content.res.Resources,
) : Drawable() {

    private val copies: List<Drawable> = children.take(4).map { child ->
        child.constantState?.newDrawable(resources) ?: child
    }

    override fun draw(canvas: Canvas) {
        if (copies.isEmpty()) return
        val b = bounds
        val halfW = b.width() / 2f
        val halfH = b.height() / 2f
        val inset = min(halfW, halfH) * 0.08f
        for (index in copies.indices) {
            val col = index % 2
            val row = index / 2
            val cell = Rect(
                (b.left + col * halfW + inset).toInt(),
                (b.top + row * halfH + inset).toInt(),
                (b.left + (col + 1) * halfW - inset).toInt(),
                (b.top + (row + 1) * halfH - inset).toInt(),
            )
            val child = copies[index]
            child.bounds = cell
            child.draw(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        for (child in copies) child.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        for (child in copies) child.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}