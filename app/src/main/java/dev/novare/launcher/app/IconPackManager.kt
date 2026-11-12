package dev.novare.launcher.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import dev.novare.launcher.ui.ComposedIconDrawable
import org.xmlpull.v1.XmlPullParser

/**
 * Reads ADW/Go/Apex-style icon packs: discovers packs through their theme intents,
 * parses `appfilter.xml`, and composes legacy `iconback`/`iconmask`/`iconupon`
 * resources when a pack has no per-component entry.
 */
class IconPackManager(private val context: Context) {

    data class Pack(val pkg: String, val label: String)

    private val discovered = LinkedHashMap<String, Pack>()
    private val filters = HashMap<String, Map<String, String>>()
    private val resourcesByPack = HashMap<String, Resources?>()
    private val drawableCache = HashMap<String, Drawable?>()

    fun packs(): List<Pack> {
        if (discovered.isEmpty()) discover()
        return discovered.values.toList()
    }

    private fun discover() {
        val actions = listOf(
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.novalauncher.THEME",
        )
        val pm = context.packageManager
        for (action in actions) {
            val intent = Intent(action).addCategory(Intent.CATEGORY_DEFAULT)
            val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            for (info in resolved) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (discovered.containsKey(pkg)) continue
                val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                discovered[pkg] = Pack(pkg, label)
            }
        }
    }

    fun icon(packPackage: String, component: ComponentName, sizePx: Int): Drawable? {
        val res = resourcesFor(packPackage) ?: return null
        val key = "${packPackage}:${component.flattenToShortString()}:$sizePx"
        if (drawableCache.containsKey(key)) return drawableCache[key]
        val mapping = appFilter(packPackage)
        val name = mapping[componentKey(component)]
        var result: Drawable? = null
        if (name != null) {
            result = drawable(res, packPackage, name)
        }
        if (result == null) {
            val back = drawable(res, packPackage, "iconback")
            if (back != null) {
                val mask = drawable(res, packPackage, "iconmask")
                val upon = drawable(res, packPackage, "iconupon")
                result = ComposedIconDrawable(res, back, fallbackIcon(component), upon, mask)
            }
        }
        drawableCache[key] = result
        return result
    }

    private fun fallbackIcon(component: ComponentName): Drawable {
        return runCatching {
            context.packageManager.getActivityIcon(component)
        }.getOrElse {
            ResourcesCompat.getDrawable(context.resources, android.R.drawable.sym_def_app_icon, null)!!
        }
    }

    private fun resourcesFor(pkg: String): Resources? {
        if (resourcesByPack.containsKey(pkg)) return resourcesByPack[pkg]
        val res = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull()
        resourcesByPack[pkg] = res
        return res
    }

    private fun appFilter(pkg: String): Map<String, String> {
        filters[pkg]?.let { return it }
        val res = resourcesFor(pkg)
        val map = HashMap<String, String>()
        if (res != null) {
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) {
                runCatching {
                    val parser: XmlResourceParser = res.getXml(id)
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "item") {
                            val component = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (component != null && drawable != null) {
                                normalize(component)?.let { map[it] = drawable }
                            }
                        }
                        event = parser.next()
                    }
                    parser.close()
                }
            }
        }
        filters[pkg] = map
        return map
    }

    private fun drawable(res: Resources, pkg: String, name: String): Drawable? {
        var id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) id = res.getIdentifier(name, "mipmap", pkg)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(res, id, null) }.getOrNull()
    }

    private fun componentKey(component: ComponentName): String {
        val pkg = component.packageName
        val cls = component.className
        val normalized = if (cls.startsWith(".")) pkg + cls else if (!cls.contains('.')) "$pkg.$cls" else cls
        return "$pkg/$normalized"
    }

    /** Accepts `ComponentInfo{pkg/cls}` as written by icon packs and normalizes it. */
    private fun normalize(raw: String): String? {
        var value = raw.trim()
        if (value.startsWith("ComponentInfo{")) value = value.removePrefix("ComponentInfo{")
        if (value.endsWith("}")) value = value.dropLast(1)
        val slash = value.indexOf('/')
        if (slash <= 0) return null
        val pkg = value.substring(0, slash)
        var cls = value.substring(slash + 1)
        if (cls.startsWith(".")) cls = pkg + cls else if (!cls.contains('.')) cls = "$pkg.$cls"
        return "$pkg/$cls"
    }
}