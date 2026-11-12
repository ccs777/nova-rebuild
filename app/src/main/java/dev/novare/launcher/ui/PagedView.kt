package dev.novare.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Scroller
import kotlin.math.roundToInt

/**
 * Horizontal pager with launcher semantics: pages are laid out edge to edge,
 * the pager snaps and clamps at both ends, and vertical flicks are reported so
 * the activity can run drawer/notification gestures.
 */
class PagedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val scroller = Scroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maximumVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private val pages = ArrayList<View>()
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var scrollOffset = 0

    var currentPage = 0
        private set

    var onPageChanged: ((Int) -> Unit)? = null

    val pageCount: Int get() = pages.size

    fun setPages(newPages: List<View>) {
        val previous = currentPage
        removeAllViews()
        pages.clear()
        pages.addAll(newPages)
        for (page in pages) addView(page)
        currentPage = previous.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        scrollOffset = currentPage * pageWidth()
        requestLayout()
        invalidate()
    }

    fun pageAt(index: Int): View? = pages.getOrNull(index)

    private fun pageWidth(): Int =
        (width - paddingLeft - paddingRight).coerceAtLeast(1)

    fun pageUnder(rawX: Float, rawY: Float): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val localX = rawX - location[0] + scrollOffset
        val page = (localX / pageWidth()).toInt()
        return page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }

    fun setCurrentPage(page: Int, animate: Boolean) {
        if (pages.isEmpty()) return
        val target = page.coerceIn(0, pages.size - 1)
        val targetX = target * pageWidth()
        if (animate) {
            scroller.startScroll(scrollOffset, 0, targetX - scrollOffset, 0, 260)
            invalidate()
        } else {
            scroller.abortAnimation()
            scrollOffset = targetX
            scrollTo(targetX, 0)
        }
        if (currentPage != target) {
            currentPage = target
            onPageChanged?.invoke(target)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currX
            scrollTo(scrollOffset, 0)
            postInvalidateOnAnimation()
        } else if (currentPage * pageWidth() != scrollOffset && !dragging) {
            scrollOffset = currentPage * pageWidth()
            scrollTo(scrollOffset, 0)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                dragging = false
                scroller.abortAnimation()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f) {
                    dragging = true
                    lastX = ev.x
                    return true
                }
                if (kotlin.math.abs(dy) > touchSlop * 3 && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                    return false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                dragging = false
                scroller.abortAnimation()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        dragging = true
                    } else if (kotlin.math.abs(dy) > touchSlop * 2 && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                        return true
                    }
                }
                if (dragging) {
                    val delta = lastX - event.x
                    scrollOffset = (scrollOffset + delta.toInt()).coerceIn(0, (pages.size - 1) * pageWidth())
                    scrollTo(scrollOffset, 0)
                    lastX = event.x
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (!dragging) {
                    performClick()
                } else {
                    val tracker = velocityTracker
                    var velocity = 0f
                    if (tracker != null) {
                        tracker.computeCurrentVelocity(1000, maximumVelocity.toFloat())
                        velocity = tracker.xVelocity
                    }
                    val width = pageWidth()
                    val projected = scrollOffset.toFloat() - velocity * 0.12f
                    val target = (projected / width).roundToInt().coerceIn(0, pages.size - 1)
                    setCurrentPage(target, false)
                }
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val childWidth = width - paddingLeft - paddingRight
        val childHeight = height - paddingTop - paddingBottom
        for (page in pages) {
            page.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = pageWidth()
        val top = paddingTop
        val bottom = b - t - paddingBottom
        for ((index, page) in pages.withIndex()) {
            val left = paddingLeft + index * width - scrollOffset
            page.layout(left, top, left + width, bottom)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}