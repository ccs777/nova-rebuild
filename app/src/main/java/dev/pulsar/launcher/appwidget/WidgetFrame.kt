package dev.pulsar.launcher.appwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import dev.pulsar.launcher.model.ItemInfo
import kotlin.math.roundToInt

/**
 * Chrome around a single widget: selection border, bottom-right resize handle and the
 * long-press bridge. The frame only mutates the in-memory spans and reports them through
 * [onResize]; persistence belongs to the caller.
 */
class WidgetFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var item: ItemInfo? = null
        set(value) {
            field = value
            invalidate()
        }

    var chromeSelected: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var onLongPress: ((ItemInfo) -> Boolean)? = null
    var onResize: ((ItemInfo, Int, Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val handleRect = RectF()

    private var cellWidthPx = 0
    private var cellHeightPx = 0
    private var resizing = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startSpanX = 1
    private var startSpanY = 1
    private var reportedSpanX = 1
    private var reportedSpanY = 1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        isClickable = true
        isLongClickable = true
        setWillNotDraw(false)
        setOnLongClickListener { view ->
            val target = item
            if (target != null) onLongPress?.invoke(target) ?: false else false
        }
    }

    /** Pixel size of one grid cell, used to translate drag distance into spans. */
    fun setCellSize(w: Int, h: Int) {
        cellWidthPx = w.coerceAtLeast(0)
        cellHeightPx = h.coerceAtLeast(0)
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        if (child == null) return
        child.isLongClickable = true
        child.setOnLongClickListener { view ->
            val target = item
            if (target != null) onLongPress?.invoke(target) ?: false else false
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) return resizing
        if (!isInsideHandle(ev.x, ev.y) || item == null) return false
        val target = item ?: return false
        resizing = true
        startSpanX = target.spanX.coerceAtLeast(1)
        startSpanY = target.spanY.coerceAtLeast(1)
        reportedSpanX = startSpanX
        reportedSpanY = startSpanY
        downRawX = ev.rawX
        downRawY = ev.rawY
        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!resizing) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val target = item ?: return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (cellWidthPx > 0 && cellHeightPx > 0) {
                    target.spanX = (startSpanX + (dx / cellWidthPx).roundToInt()).coerceAtLeast(1)
                    target.spanY = (startSpanY + (dy / cellHeightPx).roundToInt()).coerceAtLeast(1)
                } else {
                    // No cell metrics yet: fall back to a coarse 64dp step per cell.
                    val step = (64f * density).coerceAtLeast(1f)
                    target.spanX = (startSpanX + (dx / step).roundToInt()).coerceAtLeast(1)
                    target.spanY = (startSpanY + (dy / step).roundToInt()).coerceAtLeast(1)
                }
                reportedSpanX = target.spanX
                reportedSpanY = target.spanY
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                finishResize()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                finishResize()
                return true
            }
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val inset = borderPaint.strokeWidth / 2f
        borderPaint.color = if (chromeSelected) SELECTED_BORDER else IDLE_BORDER
        handlePaint.color = if (chromeSelected) SELECTED_BORDER else IDLE_BORDER
        canvas.drawRoundRect(
            inset,
            inset,
            width - inset,
            height - inset,
            cornerRadius,
            cornerRadius,
            borderPaint,
        )
        handleRect.set(
            width - paddingRight - handleSize,
            height - paddingBottom - handleSize,
            width - paddingRight - inset,
            height - paddingBottom - inset,
        )
        if (handleRect.width() <= 0f || handleRect.height() <= 0f) return
        drawHandle(canvas)
    }

    private fun drawHandle(canvas: Canvas) {
        val step = handleRect.width() / 4f
        for (index in 1..3) {
            val offset = index * step
            canvas.drawLine(
                handleRect.right - offset,
                handleRect.bottom,
                handleRect.right,
                handleRect.bottom - offset,
                handlePaint,
            )
        }
    }

    private fun finishResize() {
        val target = item
        val changed = target != null && (reportedSpanX != startSpanX || reportedSpanY != startSpanY)
        resizing = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
        if (target != null && changed) {
            onResize?.invoke(target, reportedSpanX, reportedSpanY)
        }
    }

    private fun isInsideHandle(x: Float, y: Float): Boolean {
        val size = handleSize + touchSlop
        return x >= width - paddingRight - size &&
            y >= height - paddingBottom - size &&
            x <= width - paddingLeft &&
            y <= height - paddingTop
    }

    private val handleSize: Float
        get() = 18f * density

    private val cornerRadius: Float
        get() = 4f * density

    private companion object {
        val SELECTED_BORDER = 0xFF8AB4F8.toInt()
        const val IDLE_BORDER = 0x33FFFFFF
    }
}
