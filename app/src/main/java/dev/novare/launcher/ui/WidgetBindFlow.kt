package dev.novare.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent

/**
 * Permission flow for binding a widget id. `bindAppWidgetIdIfAllowed` only succeeds for the
 * default home app or an allowlisted one; otherwise the user must confirm through
 * `ACTION_APPWIDGET_BIND`, which returns the id it actually bound.
 */
object WidgetBindFlow {

    const val REQUEST_CODE = 4201

    fun intent(widgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }

    /** Returns the bound widget id, or -1 when the user or the framework refused the bind. */
    fun parseResult(resultCode: Int, data: Intent?): Int {
        if (resultCode != Activity.RESULT_OK) return -1
        return data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
    }
}
