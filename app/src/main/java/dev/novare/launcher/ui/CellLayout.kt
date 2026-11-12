package dev.novare.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import dev.novare.launcher.model.ItemInfo

/**
 * Fixed-grid container that positions children by cell coordinates, mirroring the
 * `cellX/cellY/spanX/spanY` model stored in Nova's favorites table.
 */
class CellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    var columns: Int = 5
        private set
    var rows: Int = 5
        private set

    var gapPx: Int = 0
    var iconSizePx: Int = 0
    var onItemClick: ((ItemInfo) -> Unit)? = null
    var onItemLongClick: ((ItemInfo, View) -> Boolean)? = null
    var onEmptyLongClick: ((Float, Float) -> Unit)? = null
    var iconProvider: ((ItemInfo) -> Drawable?)? = null
    var labelProvider: ((ItemInfo) -> String)? = null

    /** Supplies a live view for widget cells; icons are built internally. */
    var widgetViewProvider: ((ItemInfo) -> View?)? = null

    private var occupancy: Array<Array<ItemInfo?>> = Array(rows) { arrayOfNulls(columns) }
    private val itemViews = LinkedHashMap<ItemInfo, View>()
    private var cellWidth = 0f
    private var cellHeight = 0f

    init {
        isClickable = true
        isLongClickable = true
        clipChildren = false
    }

    fun setGrid(columns: Int, rows: Int) {
        if (this.columns == columns && this.rows == rows) return
        this.columns = columns
        this.rows = rows
        occupancy = Array(rows) { arrayOfNulls(columns) }
        requestLayout()
    }

    fun bind(items: List<ItemInfo>) {
        removeAllViews()
        itemViews.clear()
        for (row in occupancy) row.fill(null)
        for (item in items) {
            val view = createView(item)
            itemViews[item] = view
            addView(view)
            val x = item.cellX.coerceIn(0, columns - 1)
            val y = item.cellY.coerceIn(0, rows - 1)
            item.cellX = x
            item.cellY = y
            mark(item, x, y, true)
        }
        requestLayout()
    }

    fun viewFor(item: ItemInfo): View? = itemViews[item]

    private fun createView(item: ItemInfo): View {
        val widget = widgetViewProvider?.invoke(item)
        if (widget != null) {
            widget.isClickable = true
            widget.isLongClickable = true
            widget.setOnClickListener { onItemClick?.invoke(item) }
            widget.setOnLongClickListener { target ->
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onItemLongClick?.invoke(item, target) ?: false
            }
            return widget
        }
        val view = AppIconView(context)
        view.isClickable = true
        view.bind(item, iconProvider?.invoke(item), iconSizePx, labelProvider?.invoke(item) ?: item.title)
        view.setOnClickListener { onItemClick?.invoke(item) }
        view.setOnLongClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onItemLongClick?.invoke(item, it) ?: false
        }
        return view
    }

    /** Re-binds one cell in place instead of rebuilding the page. */
    fun refreshItem(item: ItemInfo) {
        val view = itemViews[item] ?: return
        if (view is AppIconView) {
            view.bind(item, iconProvider?.invoke(item), iconSizePx, labelProvider?.invoke(item) ?: item.title)
        } else {
            view.requestLayout()
        }
    }

    fun addItem(item: ItemInfo, preferredX: Int? = null, preferredY: Int? = null): Boolean {
        val cell = if (preferredX != null && preferredY != null && isFree(preferredX, preferredY, item.spanX, item.spanY)) {
            intArrayOf(preferredX, preferredY)
        } else {
            findVacantCell(item.spanX, item.spanY) ?: return false
        }
        item.cellX = cell[0]
        item.cellY = cell[1]
        mark(item, cell[0], cell[1], true)
        val view = createView(item)
        itemViews[item] = view
        addView(view)
        requestLayout()
        return true
    }

    fun removeItem(item: ItemInfo) {
        val view = itemViews.remove(item) ?: return
        unmark(item, item.cellX, item.cellY)
        removeView(view)
        requestLayout()
    }

    fun itemAt(cellX: Int, cellY: Int): ItemInfo? {
        if (cellX !in 0 until columns || cellY !in 0 until rows) return null
        return occupancy[cellY][cellX]
    }

    fun findVacantCell(spanX: Int, spanY: Int): IntArray? {
        for (y in 0..rows - spanY) {
            for (x in 0..columns - spanX) {
                if (isFree(x, y, spanX, spanY)) return intArrayOf(x, y)
            }
        }
        return null
    }

    fun hitCell(localX: Float, localY: Float): IntArray? {
        if (cellWidth <= 0f || cellHeight <= 0f) return null
        val x = ((localX - paddingLeft) / cellWidth).toInt()
        val y = ((localY - paddingTop) / cellHeight).toInt()
        if (x !in 0 until columns || y !in 0 until rows) return null
        return intArrayOf(x, y)
    }

    private fun isFree(x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        if (x < 0 || y < 0 || x + spanX > columns || y + spanY > rows) return false
        for (cy in y until y + spanY) {
            for (cx in x until x + spanX) {
                if (occupancy[cy][cx] != null) return false
            }
        }
        return true
    }

    private fun mark(item: ItemInfo, x: Int, y: Int, occupied: Boolean) {
        val value = if (occupied) item else null
        for (cy in y until y + item.spanY) {
            if (cy !in 0 until rows) continue
            for (cx in x until x + item.spanX) {
                if (cx !in 0 until columns) continue
                occupancy[cy][cx] = value
            }
        }
    }

    private fun unmark(item: ItemInfo, x: Int, y: Int) = mark(item, x, y, false)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val availableW = width - paddingLeft - paddingRight - gapPx * (columns - 1)
        val availableH = height - paddingTop - paddingBottom - gapPx * (rows - 1)
        cellWidth = if (columns > 0) availableW.toFloat() / columns else 0f
        cellHeight = if (rows > 0) availableH.toFloat() / rows else 0f
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val item = itemViews.entries.firstOrNull { it.value === child }?.key
            val spanX = item?.spanX ?: 1
            val spanY = item?.spanY ?: 1
            val childW = (cellWidth * spanX + gapPx * (spanX - 1)).toInt().coerceAtLeast(0)
            val childH = (cellHeight * spanY + gapPx * (spanY - 1)).toInt().coerceAtLeast(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val item = itemViews.entries.firstOrNull { it.value === child }?.key ?: continue
            val x = paddingLeft + (item.cellX * (cellWidth + gapPx)).toInt()
            val y = paddingTop + (item.cellY * (cellHeight + gapPx)).toInt()
            val w = (cellWidth * item.spanX + gapPx * (item.spanX - 1)).toInt()
            val h = (cellHeight * item.spanY + gapPx * (item.spanY - 1)).toInt()
            child.layout(x, y, x + w, y + h)
        }
    }

    private var lastRawX = 0f
    private var lastRawY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            lastRawX = event.rawX
            lastRawY = event.rawY
        }
        return super.onTouchEvent(event) || event.actionMasked == MotionEvent.ACTION_DOWN
    }

    override fun performLongClick(): Boolean {
        onEmptyLongClick?.let { it(lastRawX, lastRawY); return true }
        return super.performLongClick()
    }
}