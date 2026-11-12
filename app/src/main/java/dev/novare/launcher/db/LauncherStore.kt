package dev.novare.launcher.db

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import dev.novare.launcher.model.AppItem
import dev.novare.launcher.model.Container
import dev.novare.launcher.model.DrawerTab
import dev.novare.launcher.model.FolderItem
import dev.novare.launcher.model.ItemInfo
import dev.novare.launcher.model.ItemType
import dev.novare.launcher.model.LauncherLayout

/**
 * Reads and writes the launcher item graph. A folder is a `favorites` row with
 * itemType FOLDER; its children are rows whose `container` equals the folder row id.
 */
class LauncherStore(context: Context) {

    private val helper = LauncherDb(context)

    fun load(): LauncherLayout {
        val layout = LauncherLayout()
        val folders = HashMap<Long, FolderItem>()
        val children = ArrayList<Pair<Long, ItemInfo>>()
        helper.readableDatabase.query(
            "favorites", null, null, null, null, null, "rank ASC, _id ASC"
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow("_id"))
                val container = c.getInt(c.getColumnIndexOrThrow("container"))
                val itemType = c.getInt(c.getColumnIndexOrThrow("itemType"))
                if (container == Container.DESKTOP || container == Container.HOTSEAT) {
                    val item = if (itemType == ItemType.FOLDER) FolderItem() else appItemFrom(c) ?: continue
                    readCommon(item, c)
                    if (item is FolderItem) folders[id] = item
                    if (container == Container.DESKTOP) layout.desktop.add(item) else layout.hotseat.add(item)
                } else if (container > 0) {
                    val child = appItemFrom(c) ?: continue
                    readCommon(child, c)
                    children.add(container.toLong() to child)
                }
            }
        }
        for ((folderId, child) in children) folders[folderId]?.contents?.add(child)
        layout.customIcons.putAll(loadCustomIcons())
        layout.tabs.addAll(loadTabs())
        return layout
    }

    fun save(layout: LauncherLayout) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("favorites", null, null)
            db.delete("drawer_groups", null, null)
            db.delete("appgroups", null, null)
            db.delete("customIcons", null, null)

            var nextId = 1L
            for (item in layout.desktop) item.id = nextId++
            for (item in layout.desktop) {
                if (item is FolderItem) {
                    for (child in item.contents) child.id = nextId++
                }
            }
            for (item in layout.hotseat) item.id = nextId++

            layout.desktop.forEachIndexed { index, item ->
                db.insertOrThrow("favorites", null, valuesFor(item, Container.DESKTOP, item.screen, index))
                if (item is FolderItem) {
                    item.contents.forEachIndexed { childIndex, child ->
                        db.insertOrThrow("favorites", null, valuesFor(child, item.id.toInt(), child.screen, childIndex))
                    }
                }
            }
            layout.hotseat.forEachIndexed { index, item ->
                db.insertOrThrow("favorites", null, valuesFor(item, Container.HOTSEAT, 0, index))
            }

            for ((component, uri) in layout.customIcons) {
                db.insertOrThrow(
                    "customIcons", null,
                    ContentValues().apply {
                        put("component", component)
                        put("iconPackage", uri)
                        putNull("iconResource")
                    }
                )
            }

            layout.tabs.forEachIndexed { index, tab ->
                val values = ContentValues().apply {
                    put("name", tab.name)
                    put("orderIndex", index)
                }
                val tabId = db.insertOrThrow("drawer_groups", null, values)
                for (component in tab.components) {
                    db.insertOrThrow(
                        "appgroups", null,
                        ContentValues().apply {
                            put("groupId", tabId)
                            put("component", component)
                        }
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear() {
        val db = helper.writableDatabase
        db.delete("favorites", null, null)
        db.delete("drawer_groups", null, null)
        db.delete("appgroups", null, null)
        db.delete("customIcons", null, null)
    }

    private fun valuesFor(item: ItemInfo, container: Int, screen: Int, rank: Int): ContentValues =
        ContentValues().apply {
            put("_id", item.id)
            put("title", item.title)
            put("intent", intentFor(item)?.toUri(0))
            put("container", container)
            put("screen", screen)
            put("cellX", item.cellX)
            put("cellY", item.cellY)
            put("spanX", item.spanX)
            put("spanY", item.spanY)
            put("itemType", item.itemType)
            put("profileId", item.profileId)
            put("rank", rank)
            put("modified", System.currentTimeMillis())
        }

    private fun intentFor(item: ItemInfo): Intent? {
        item.intent?.let { return it }
        val app = item as? AppItem ?: return null
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.component
        }
    }

    private fun appItemFrom(c: Cursor): AppItem? {
        val raw = c.getString(c.getColumnIndexOrThrow("intent")) ?: return null
        val intent = runCatching { Intent.parseUri(raw, 0) }.getOrNull() ?: return null
        val component = intent.component ?: return null
        return AppItem(component)
    }

    private fun readCommon(item: ItemInfo, c: Cursor) {
        item.title = c.getString(c.getColumnIndexOrThrow("title")) ?: ""
        item.screen = c.getInt(c.getColumnIndexOrThrow("screen"))
        item.cellX = c.getInt(c.getColumnIndexOrThrow("cellX"))
        item.cellY = c.getInt(c.getColumnIndexOrThrow("cellY"))
        item.spanX = c.getFloat(c.getColumnIndexOrThrow("spanX")).toInt().coerceAtLeast(1)
        item.spanY = c.getFloat(c.getColumnIndexOrThrow("spanY")).toInt().coerceAtLeast(1)
        item.profileId = c.getLong(c.getColumnIndexOrThrow("profileId"))
        item.rank = c.getInt(c.getColumnIndexOrThrow("rank"))
        if (item is AppItem) {
            item.intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = item.component
            }
        }
    }

    private fun loadCustomIcons(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        helper.readableDatabase.query("customIcons", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val component = c.getString(c.getColumnIndexOrThrow("component")) ?: continue
                val uri = c.getString(c.getColumnIndexOrThrow("iconPackage")) ?: continue
                map[component] = uri
            }
        }
        return map
    }

    private fun loadTabs(): List<DrawerTab> {
        val tabs = LinkedHashMap<Long, DrawerTab>()
        helper.readableDatabase.query(
            "drawer_groups", null, null, null, null, null, "orderIndex ASC, _id ASC"
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow("_id"))
                tabs[id] = DrawerTab(id, c.getString(c.getColumnIndexOrThrow("name")) ?: "")
            }
        }
        helper.readableDatabase.query("appgroups", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val groupId = c.getLong(c.getColumnIndexOrThrow("groupId"))
                val component = c.getString(c.getColumnIndexOrThrow("component")) ?: continue
                tabs[groupId]?.components?.add(component)
            }
        }
        return tabs.values.toList()
    }
}