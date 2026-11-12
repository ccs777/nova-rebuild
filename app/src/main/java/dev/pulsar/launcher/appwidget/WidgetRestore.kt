package dev.pulsar.launcher.appwidget

import dev.pulsar.launcher.model.ItemInfo
import dev.pulsar.launcher.model.ItemType

/**
 * Restore-side helper: finds widgets whose persisted binding did not survive, so the
 * caller can run the `ACTION_APPWIDGET_BIND` permission flow for each of them.
 */
object WidgetRestore {

    fun pending(items: List<ItemInfo>): List<ItemInfo> = items.filter { item ->
        item.itemType == ItemType.APPWIDGET &&
            item.appWidgetId < 0 &&
            item.appWidgetProvider != null
    }
}
