package dev.pulsar.launcher.appwidget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import dev.pulsar.launcher.model.ItemInfo

/**
 * Owns the single [AppWidgetHost] of the launcher and the host views currently on screen.
 *
 * Widget ids are stable across process restarts (the host id constant is fixed), so
 * [stop] only drops the views: the bindings stay valid and [viewFor] rebuilds a view on
 * demand. Widget removal alone releases the id through [release].
 */
class WidgetHostManager(private val context: Context) {

    companion object {
        /** Fixed host id: changing it orphans every widget id persisted by the launcher. */
        const val HOST_ID: Int = 0x4E6F7661
    }

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host: AppWidgetHost = AppWidgetHost(context, HOST_ID)
    private val views = LinkedHashMap<Int, View>()

    private var listening = false
    private var cellWidthPx = 0
    private var cellHeightPx = 0

    /** Invoked from [onRestore] when a persisted id is dead and a fresh id was allocated. */
    var onWidgetIdChanged: ((ItemInfo, Int) -> Unit)? = null

    /** Supplies the pixel size of one grid cell; used to derive widget option sizes. */
    fun setCellSize(w: Int, h: Int) {
        cellWidthPx = w.coerceAtLeast(0)
        cellHeightPx = h.coerceAtLeast(0)
    }

    fun start() {
        if (listening) return
        host.startListening()
        listening = true
    }

    fun stop() {
        if (listening) {
            host.stopListening()
            listening = false
        }
        for (view in views.values) {
            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        }
        views.clear()
    }

    /**
     * Binds [provider] to [item] and creates its host view.
     *
     * A new id is allocated when [ItemInfo.appWidgetId] is negative and written back to the
     * item. Returns false when the system refuses the bind; the caller must then launch
     * `AppWidgetManager.ACTION_APPWIDGET_BIND` with the (already allocated) id from the item.
     */
    fun bindNew(provider: ComponentName?, item: ItemInfo): Boolean {
        val target = provider ?: item.appWidgetProvider ?: return false
        item.appWidgetProvider = target

        var widgetId = item.appWidgetId
        if (widgetId >= 0 && appWidgetManager.getAppWidgetInfo(widgetId) != null) {
            // Already bound: refreshing the view is enough, rebinding would be rejected.
            createHostView(item, widgetId)
            return true
        }
        if (widgetId < 0) {
            widgetId = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return false
            item.appWidgetId = widgetId
        }

        val allowed = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, target)
        }.getOrDefault(false)
        if (!allowed) return false

        createHostView(item, widgetId)
        return true
    }

    /** Cached view for [item], creating it when the item is bound but not yet inflated. */
    fun viewFor(item: ItemInfo): View? {
        val widgetId = item.appWidgetId
        if (widgetId < 0) return null
        views[widgetId]?.let { return it }
        if (!listening) start()
        return createHostView(item, widgetId)
    }

    /** Deletes the view and releases the widget id; the item becomes unbound. */
    fun release(item: ItemInfo) {
        val widgetId = item.appWidgetId
        if (widgetId < 0) return
        val view = views.remove(widgetId)
        runCatching { (view?.parent as? ViewGroup)?.removeView(view) }
        runCatching { host.deleteAppWidgetId(widgetId) }
        item.appWidgetId = -1
    }

    /** Persists the span on the item and pushes new size options to the widget. */
    fun updateSpans(item: ItemInfo, spanX: Int, spanY: Int) {
        item.spanX = spanX.coerceAtLeast(1)
        item.spanY = spanY.coerceAtLeast(1)
        val widgetId = item.appWidgetId
        if (widgetId < 0) return
        if (cellWidthPx <= 0 || cellHeightPx <= 0) return
        val options = Bundle().apply {
            val width = cellWidthPx * item.spanX
            val height = cellHeightPx * item.spanY
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        }
        runCatching { appWidgetManager.updateAppWidgetOptions(widgetId, options) }
    }

    /**
     * Validates the persisted id of a restored item and returns the id to keep.
     *
     * When the id no longer maps to an installed provider it is dropped, a fresh unbound id
     * is allocated for [provider] (so the caller can run the bind permission flow) and
     * [onWidgetIdChanged] reports the replacement. Returns -1 when nothing can be restored.
     */
    fun onRestore(item: ItemInfo, provider: ComponentName?): Int {
        val widgetId = item.appWidgetId
        val info = if (widgetId >= 0) appWidgetManager.getAppWidgetInfo(widgetId) else null
        if (info != null) {
            item.appWidgetProvider = info.provider ?: provider ?: item.appWidgetProvider
            return widgetId
        }
        if (widgetId >= 0) {
            runCatching { host.deleteAppWidgetId(widgetId) }
        }
        val target = provider ?: item.appWidgetProvider
        if (target == null) {
            item.appWidgetId = -1
            return -1
        }
        item.appWidgetProvider = target
        val fresh = runCatching { host.allocateAppWidgetId() }.getOrNull()
        if (fresh == null) {
            item.appWidgetId = -1
            return -1
        }
        item.appWidgetId = fresh
        onWidgetIdChanged?.invoke(item, fresh)
        return fresh
    }

    private fun createHostView(item: ItemInfo, widgetId: Int): View {
        views[widgetId]?.let { return it }
        val info = runCatching { appWidgetManager.getAppWidgetInfo(widgetId) }.getOrNull()
            ?: return placeholder(item).also { views[widgetId] = it }
        val view = runCatching { host.createView(context, widgetId, info) }.getOrNull()
            ?: return placeholder(item).also { views[widgetId] = it }
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        views[widgetId] = view
        return view
    }

    /** Shown instead of crashing when the provider's RemoteViews cannot be inflated. */
    private fun placeholder(item: ItemInfo): TextView {
        return TextView(context).apply {
            text = item.title.ifBlank { "Widget" }
            gravity = Gravity.CENTER
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val value = TypedValue()
            val resolved = runCatching {
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, value, true)
            }.getOrDefault(false)
            if (resolved) {
                setTextColor(if (value.resourceId != 0) context.getColor(value.resourceId) else value.data)
            } else {
                setTextColor(Color.GRAY)
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private val paddingPx: Int
        get() = (12f * context.resources.displayMetrics.density).toInt()
}
