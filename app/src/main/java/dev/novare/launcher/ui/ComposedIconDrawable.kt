package dev.novare.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.content.res.Resources
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Legacy icon-pack composition: iconback + icon mask + iconupon around an app icon. */
class ComposedIconDrawable(
    private val res: Resources,
    private val back: Drawable?,
    icon: Drawable,
    private val upon: Drawable?,
    private val mask: Drawable?,
) : Drawable() {

    private val iconCopy: Drawable = copy(icon)
    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var maskBitmap: Bitmap? = null

    private fun copy(source: Drawable): Drawable =
        source.constantState?.newDrawable(res) ?: source

    override fun draw(canvas: Canvas) {
        val b = bounds
        back?.let {
            it.bounds = b
            it.draw(canvas)
        }
        val insetX = (b.width() * 0.1f).toInt()
        val insetY = (b.height() * 0.1f).toInt()
        val iconBounds = Rect(b.left + insetX, b.top + insetY, b.right - insetX, b.bottom - insetY)
        val maskDrawable = mask
        if (maskDrawable != null && iconBounds.width() > 0 && iconBounds.height() > 0) {
            val w = iconBounds.width()
            val h = iconBounds.height()
            var bitmap = maskBitmap
            if (bitmap == null || bitmap.width != w || bitmap.height != h) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                maskBitmap = bitmap
            }
            bitmap.eraseColor(Color.TRANSPARENT)
            val maskCanvas = Canvas(bitmap)
            val maskCopy = copy(maskDrawable)
            maskCopy.bounds = Rect(0, 0, w, h)
            maskCopy.draw(maskCanvas)

            val save = canvas.saveLayer(
                iconBounds.left.toFloat(), iconBounds.top.toFloat(),
                iconBounds.right.toFloat(), iconBounds.bottom.toFloat(), null
            )
            iconCopy.bounds = iconBounds
            iconCopy.draw(canvas)
            canvas.drawBitmap(bitmap, null, RectF(iconBounds), maskPaint)
            canvas.restoreToCount(save)
        } else {
            iconCopy.bounds = iconBounds
            iconCopy.draw(canvas)
        }
        upon?.let {
            it.bounds = b
            it.draw(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        iconCopy.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        iconCopy.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}