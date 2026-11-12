package dev.pulsar.launcher.shortcuts

import android.content.Context
import dev.pulsar.launcher.model.Container
import dev.pulsar.launcher.model.ItemInfo
import dev.pulsar.launcher.model.ItemType

/** Converts a resolved shortcut into a desktop item; persisting it stays with the caller. */
object ShortcutItemFactory {

    fun toItem(context: Context, record: ShortcutRecord): ItemInfo = ItemInfo(
        title = titleFor(context, record),
        itemType = ItemType.DEEP_SHORTCUT,
        intent = record.intent,
        container = Container.DESKTOP,
        spanX = 1,
        spanY = 1,
    )

    private fun titleFor(context: Context, record: ShortcutRecord): String {
        if (record.shortLabel.isNotBlank()) return record.shortLabel
        val longLabel = record.longLabel
        if (!longLabel.isNullOrBlank()) return longLabel
        return runCatching {
            val info = context.packageManager.getApplicationInfo(record.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(record.packageName)
    }
}
