package dev.novare.launcher.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import dev.novare.launcher.db.LauncherPrefs
import dev.novare.launcher.model.AppItem
import dev.novare.launcher.model.FolderItem
import dev.novare.launcher.model.ItemInfo
import dev.novare.launcher.ui.FolderPreviewDrawable

/** Resolves the drawable for an item: user icon override, icon pack, then the app icon. */
class IconCache(
    private val context: Context,
    private val prefs: LauncherPrefs,
    private val iconPacks: IconPackManager,
) {

    private val memory = LruCache<String, Drawable>(256)
    val customIcons = HashMap<String, String>()

    /** Resolves an installed app for components that came from a restored database row. */
    var appResolver: ((android.content.ComponentName) -> AppItem?)? = null

    fun clear() {
        memory.evictAll()
    }

    fun iconFor(item: ItemInfo): Drawable? {
        if (item is FolderItem) return folderIcon(item)
        val app = item as? AppItem ?: return null
        val key = app.key()
        memory.get(key)?.let { return it }
        val resolved = app.icon ?: appResolver?.invoke(app.component)?.icon
        val icon = customIcon(key) ?: packed(app) ?: resolved
        if (icon != null) memory.put(key, icon)
        return icon
    }

    fun folderIcon(folder: FolderItem): Drawable {
        val children = folder.contents.take(4).mapNotNull { child ->
            if (child is AppItem) iconFor(child) else null
        }
        return FolderPreviewDrawable(children, context.resources)
    }

    fun labelFor(item: ItemInfo): String = item.title

    private fun packed(app: AppItem): Drawable? {
        val pack = prefs.iconPackPackage ?: return null
        return iconPacks.icon(pack, app.component, context.resources.displayMetrics.densityDpi)
    }

    private fun customIcon(key: String): Drawable? {
        val raw = customIcons[key] ?: return null
        val cacheKey = "custom:$raw"
        memory.get(cacheKey)?.let { return it }
        val drawable = runCatching {
            context.contentResolver.openInputStream(Uri.parse(raw))?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
            }
        }.getOrNull()
        if (drawable != null) memory.put(cacheKey, drawable)
        return drawable
    }
}