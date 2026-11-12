package dev.novare.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import dev.novare.launcher.model.ItemInfo
import kotlin.math.hypot

/**
 * Owns the floating drag bitmap and the touch stream while an item is dragged.
 * Drop resolution is delegated to the host activity, which owns the model.
 */
class DragController(private val layer: DragLayer) {

    private var dragView: ImageView? = null
    private var item: ItemInfo? = null
    private var source: View? = null
    private var sourceAlpha = 1f
    private var fromDrawer = false
    private var startRawX = 0f
    private var startRawY = 0f
    private var moved = false
    private val touchSlop = ViewConfiguration.get(layer.context).scaledTouchSlop

    var onDragStarted: ((ItemInfo, Boolean) -> Unit)? = null
    var onDragMove: ((Float, Float) -> Unit)? = null
    var onDrop: ((ItemInfo, Boolean, Float, Float, Boolean) -> Unit)? = null
    var onDragEnded: (() -> Unit)? = null

    val isDragging: Boolean get() = item != null

    fun startDrag(item: ItemInfo, sourceView: View, fromDrawer: Boolean) {
        if (isDragging) return
        val width = sourceView.width.coerceAtLeast(1)
        val height = sourceView.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        sourceView.draw(Canvas(bitmap))
        val view = ImageView(layer.context)
        view.setImageBitmap(bitmap)
        view.layoutParams = FrameLayout.LayoutParams(width, height)
        view.alpha = 0.9f
        layer.addView(view)
        dragView = view

        this.item = item
        this.source = sourceView
        this.fromDrawer = fromDrawer
        startRawX = 0f
        startRawY = 0f
        moved = false
        sourceAlpha = sourceView.alpha
        sourceView.alpha = 0.25f

        layer.isDragging = true
        layer.dragTouchHandler = { event -> handleTouch(event) }
        onDragStarted?.invoke(item, fromDrawer)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = rawX
                startRawY = rawY
            }

            MotionEvent.ACTION_MOVE -> {
                if (startRawX == 0f && startRawY == 0f) {
                    startRawX = rawX
                    startRawY = rawY
                }
                if (!moved && hypot(rawX - startRawX, rawY - startRawY) > touchSlop) moved = true
                moveDragView(rawX, rawY)
                onDragMove?.invoke(rawX, rawY)
            }

            MotionEvent.ACTION_UP -> {
                val dropped = item
                val drawer = fromDrawer
                val wasMoved = moved
                cleanup()
                if (dropped != null) onDrop?.invoke(dropped, drawer, rawX, rawY, wasMoved)
                onDragEnded?.invoke()
            }

            MotionEvent.ACTION_CANCEL -> {
                cleanup()
                onDragEnded?.invoke()
            }
        }
        return true
    }

    private fun moveDragView(rawX: Float, rawY: Float) {
        val view = dragView ?: return
        val location = IntArray(2)
        layer.getLocationOnScreen(location)
        view.x = rawX - location[0] - view.width / 2f
        view.y = rawY - location[1] - view.height / 2f
    }

    private fun cleanup() {
        dragView?.let { layer.removeView(it) }
        dragView = null
        source?.alpha = sourceAlpha
        source = null
        item = null
        layer.isDragging = false
        layer.dragTouchHandler = null
    }
}