package dev.pulsar.launcher.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Root container. While a drag is in flight it intercepts the touch stream so the
 * originally long-pressed child stops receiving events; otherwise it only observes
 * the stream to detect vertical workspace gestures.
 */
class DragLayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var isDragging = false
        set(value) {
            field = value
            requestDisallowInterceptTouchEvent(false)
        }

    var dragTouchHandler: ((MotionEvent) -> Boolean)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDragging) return true
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handler = dragTouchHandler
        if (isDragging && handler != null) {
            handler(event)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                downTime = SystemClock.uptimeMillis()
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    val duration = SystemClock.uptimeMillis() - downTime
                    val threshold = 24f * resources.displayMetrics.density
                    if (duration < 700 && abs(dy) > threshold * 3 && abs(dy) > abs(dx) * 1.5f) {
                        if (dy < 0) onSwipeUp?.invoke() else onSwipeDown?.invoke()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}