package dev.novare.launcher.model

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable

object ItemType {
    const val APPLICATION = 0
    const val FOLDER = 2
    const val APPWIDGET = 4
    const val DEEP_SHORTCUT = 6
}

/**
 * Container values follow the AOSP launcher schema Nova inherits:
 * negative ids are synthetic containers, a positive id is a folder row id.
 */
object Container {
    const val DESKTOP = -100
    const val HOTSEAT = -101
}

open class ItemInfo(
    var id: Long = 0L,
    var title: String = "",
    var screen: Int = 0,
    var cellX: Int = 0,
    var cellY: Int = 0,
    var spanX: Int = 1,
    var spanY: Int = 1,
    var container: Int = Container.DESKTOP,
    var rank: Int = 0,
    var itemType: Int = ItemType.APPLICATION,
    var intent: Intent? = null,
    var profileId: Long = -1L,
    var customIconUri: String? = null,
    var appWidgetId: Int = -1,
    var appWidgetProvider: ComponentName? = null,
    var appWidgetSource: Int = -1,
)

class AppItem(
    var component: ComponentName,
    var icon: Drawable? = null,
) : ItemInfo(itemType = ItemType.APPLICATION) {
    fun key(): String = component.flattenToString()
}

class FolderItem : ItemInfo(itemType = ItemType.FOLDER) {
    val contents = ArrayList<ItemInfo>()
}