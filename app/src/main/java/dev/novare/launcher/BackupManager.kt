package dev.novare.launcher

import android.content.Context
import android.net.Uri
import dev.novare.launcher.db.LauncherPrefs
import dev.novare.launcher.db.LauncherStore
import dev.novare.launcher.model.AppItem
import dev.novare.launcher.model.Container
import dev.novare.launcher.model.DrawerTab
import dev.novare.launcher.model.FolderItem
import dev.novare.launcher.model.ItemInfo
import dev.novare.launcher.model.LauncherLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable backup archive. Nova 8.0.18 backs up a ZIP containing `nova.xml` plus
 * its database files; this build keeps the same ZIP-with-prefs shape but stores
 * the item graph as JSON so a backup stays readable across schema changes.
 */
object BackupManager {

    const val FORMAT = "novarebuild"
    const val VERSION = 1
    const val MIME = "application/zip"

    data class Restored(val layout: LauncherLayout, val preferences: Map<String, Any?>)

    fun export(context: Context, uri: Uri, layout: LauncherLayout, prefs: LauncherPrefs): Result<Unit> =
        runCatching {
            val resolver = context.contentResolver
            val stream: OutputStream = resolver.openOutputStream(uri)
                ?: error("Cannot open backup destination")
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("meta.json"))
                zip.write(meta().toString().toByteArray())

                zip.putNextEntry(ZipEntry("favorites.json"))
                zip.write(itemsJson(layout).toString().toByteArray())

                zip.putNextEntry(ZipEntry("nova.xml"))
                zip.write(prefsJson(prefs).toString().toByteArray())
            }
        }

    fun import(context: Context, uri: Uri): Result<Restored> = runCatching {
        val resolver = context.contentResolver
        val stream: InputStream = resolver.openInputStream(uri) ?: error("Cannot open backup")
        var meta: JSONObject? = null
        var items: JSONObject? = null
        var prefsJson: JSONObject? = null
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                val text = String(bytes)
                when (entry.name) {
                    "meta.json" -> meta = JSONObject(text)
                    "favorites.json" -> items = JSONObject(text)
                    "nova.xml" -> prefsJson = JSONObject(text)
                }
                zip.closeEntry()
            }
        }
        val header = meta ?: error("Backup has no meta.json")
        require(header.optString("format") == FORMAT) { "Not a NovaRebuild backup" }
        val body = items ?: error("Backup has no favorites.json")
        Restored(
            layout = readLayout(body),
            preferences = readPreferences(prefsJson ?: JSONObject()),
        )
    }

    private fun meta(): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("created", System.currentTimeMillis())
    }

    private fun itemsJson(layout: LauncherLayout): JSONObject {
        val desktop = JSONArray()
        for (item in layout.desktop) desktop.put(itemJson(item))
        val hotseat = JSONArray()
        for (item in layout.hotseat) hotseat.put(appJson(item))
        val tabs = JSONArray()
        for (tab in layout.tabs) {
            tabs.put(JSONObject().apply {
                put("name", tab.name)
                put("components", JSONArray(tab.components.toList()))
            })
        }
        val customIcons = JSONObject()
        for ((component, iconUri) in layout.customIcons) customIcons.put(component, iconUri)
        return JSONObject().apply {
            put("desktop", desktop)
            put("hotseat", hotseat)
            put("tabs", tabs)
            put("customIcons", customIcons)
        }
    }

    private fun itemJson(item: ItemInfo): JSONObject = when (item) {
        is FolderItem -> JSONObject().apply {
            put("kind", "folder")
            put("title", item.title)
            put("screen", item.screen)
            put("x", item.cellX)
            put("y", item.cellY)
            put("children", JSONArray().apply {
                for (child in item.contents) put(appJson(child))
            })
        }

        is AppItem -> appJson(item)
        else -> appJson(item as AppItem)
    }

    private fun appJson(item: ItemInfo): JSONObject {
        val json = JSONObject()
        json.put("kind", "app")
        json.put("title", item.title)
        json.put("screen", item.screen)
        json.put("x", item.cellX)
        json.put("y", item.cellY)
        if (item is AppItem) json.put("component", item.component.flattenToString())
        return json
    }

    private fun prefsJson(prefs: LauncherPrefs): JSONObject {
        val json = JSONObject()
        for ((key, value) in prefs.snapshot()) {
            when (value) {
                is Set<*> -> json.put(key, JSONArray(value.filterIsInstance<String>()))
                else -> json.put(key, value)
            }
        }
        return json
    }

    private fun readLayout(body: JSONObject): LauncherLayout {
        val layout = LauncherLayout()
        val desktop = body.optJSONArray("desktop") ?: JSONArray()
        for (index in 0 until desktop.length()) {
            val item = desktop.optJSONObject(index) ?: continue
            if (item.optString("kind") == "folder") {
                val folder = FolderItem()
                folder.title = item.optString("title")
                folder.screen = item.optInt("screen")
                folder.cellX = item.optInt("x")
                folder.cellY = item.optInt("y")
                folder.container = Container.DESKTOP
                val children = item.optJSONArray("children") ?: JSONArray()
                for (childIndex in 0 until children.length()) {
                    readApp(children.optJSONObject(childIndex))?.let { folder.contents.add(it) }
                }
                layout.desktop.add(folder)
            } else {
                readApp(item)?.let { layout.desktop.add(it) }
            }
        }
        val hotseat = body.optJSONArray("hotseat") ?: JSONArray()
        for (index in 0 until hotseat.length()) {
            readApp(hotseat.optJSONObject(index))?.let { app ->
                app.container = Container.HOTSEAT
                app.cellX = layout.hotseat.size
                app.cellY = 0
                layout.hotseat.add(app)
            }
        }
        val tabs = body.optJSONArray("tabs") ?: JSONArray()
        for (index in 0 until tabs.length()) {
            val tabJson = tabs.optJSONObject(index) ?: continue
            val tab = DrawerTab(0, tabJson.optString("name"))
            val components = tabJson.optJSONArray("components") ?: JSONArray()
            for (componentIndex in 0 until components.length()) {
                tab.components.add(components.optString(componentIndex))
            }
            layout.tabs.add(tab)
        }
        val customIcons = body.optJSONObject("customIcons")
        if (customIcons != null) {
            for (key in customIcons.keys()) {
                layout.customIcons[key] = customIcons.optString(key)
            }
        }
        return layout
    }

    private fun readApp(json: JSONObject?): AppItem? {
        if (json == null) return null
        val raw = json.optString("component")
        if (raw.isEmpty()) return null
        val component = android.content.ComponentName.unflattenFromString(raw) ?: return null
        val app = AppItem(component)
        app.title = json.optString("title")
        app.screen = json.optInt("screen")
        app.cellX = json.optInt("x")
        app.cellY = json.optInt("y")
        app.container = Container.DESKTOP
        app.intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            this.component = component
        }
        return app
    }

    private fun readPreferences(json: JSONObject): Map<String, Any?> {
        val map = HashMap<String, Any?>()
        for (key in json.keys()) {
            when (val value = json.get(key)) {
                is JSONArray -> {
                    val set = HashSet<String>()
                    for (index in 0 until value.length()) set.add(value.optString(index))
                    map[key] = set
                }

                is Boolean, is Int, is Long, is Double, is String -> map[key] = value
            }
        }
        return map
    }

    /** Clears the database and replaces it with a restored layout, then bumps the reload counter. */
    fun applyRestore(context: Context, restored: Restored) {
        val store = LauncherStore(context)
        store.clear()
        store.save(restored.layout)
        val prefs = LauncherPrefs(context)
        prefs.replaceAll(restored.preferences)
        prefs.setFirstRunDone()
        prefs.bumpLayoutRevision()
    }
}