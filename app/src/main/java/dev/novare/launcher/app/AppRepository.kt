package dev.novare.launcher.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import dev.novare.launcher.model.AppItem
import java.text.Collator
import java.util.Locale

/**
 * Loads launchable activities. LauncherApps is the primary source; PackageManager queries cover
 * the window before this launcher is the default home, when LauncherApps has no access.
 */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val collator = Collator.getInstance(Locale.getDefault())

    private var apps: List<AppItem> = emptyList()
    private var byComponent: Map<String, AppItem> = emptyMap()

    var onChange: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reload()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        reload()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun reload() {
        val loaded = loadWithLauncherApps() ?: loadWithPackageManager()
        val sorted = loaded.sortedWith { a, b -> collator.compare(a.title, b.title) }
        apps = sorted
        byComponent = sorted.associateBy { it.key() }
        onChange?.invoke()
    }

    fun all(): List<AppItem> = apps

    fun findByComponent(component: ComponentName): AppItem? = byComponent[component.flattenToString()]

    fun resolve(component: ComponentName): AppItem? {
        val existing = findByComponent(component)
        if (existing != null) return existing
        val fromPackageManager = runCatching {
            val info = packageManager.getActivityInfo(component, 0)
            val item = AppItem(component)
            item.title = info.loadLabel(packageManager).toString()
            item.icon = info.loadIcon(packageManager)
            item.intent = launchIntent(component)
            item
        }.getOrNull()
        return fromPackageManager ?: resolveWithLauncherApps(component)
    }

    fun defaultDock(): List<AppItem> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("https://"), "text/html"),
            Intent("android.media.action.STILL_IMAGE_CAMERA"),
            Intent(Intent.ACTION_SENDTO).setData(android.net.Uri.parse("smsto:")),
        )
        val result = ArrayList<AppItem>()
        val used = HashSet<String>()
        for (intent in intents) {
            val resolved = packageManager.resolveActivity(intent, 0)?.activityInfo ?: continue
            val component = ComponentName(resolved.packageName, resolved.name)
            val app = resolve(component) ?: continue
            if (used.add(app.key())) result.add(app)
        }
        if (result.isEmpty()) result.addAll(apps.take(4))
        return result
    }

    /** Returns null when LauncherApps is unavailable, throws SecurityException, or sees no apps. */
    private fun loadWithLauncherApps(): List<AppItem>? {
        val launcherApps = launcherApps ?: return null
        val density = context.resources.displayMetrics.densityDpi
        val loaded = ArrayList<AppItem>()
        val seen = HashSet<String>()
        try {
            for (user in launcherApps.profiles) {
                for (info in launcherApps.getActivityList(null, user)) {
                    val item = itemFor(info, density) ?: continue
                    if (seen.add(item.key())) loaded.add(item)
                }
            }
        } catch (e: RuntimeException) {
            // SecurityException while this launcher is not yet the default home; OEM implementations
            // can also throw IllegalStateException when the profile is locked.
            return null
        }
        return loaded.ifEmpty { null }
    }

    private fun loadWithPackageManager(): List<AppItem> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        val loaded = ArrayList<AppItem>(resolved.size)
        val seen = HashSet<String>()
        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val component = ComponentName(activityInfo.packageName, activityInfo.name)
            if (!seen.add(component.flattenToString())) continue
            val item = AppItem(component)
            item.title = info.loadLabel(packageManager).toString()
            item.icon = info.loadIcon(packageManager)
            item.intent = launchIntent(component)
            loaded.add(item)
        }
        return loaded
    }

    private fun itemFor(info: LauncherActivityInfo, density: Int): AppItem? {
        val component = info.componentName ?: return null
        val item = AppItem(component)
        item.title = info.label.toString()
        item.icon = info.getBadgedIcon(density)
        item.intent = launchIntent(component)
        return item
    }

    private fun resolveWithLauncherApps(component: ComponentName): AppItem? {
        val launcherApps = launcherApps ?: return null
        val density = context.resources.displayMetrics.densityDpi
        return try {
            var match: LauncherActivityInfo? = null
            for (user in launcherApps.profiles) {
                match = launcherApps.getActivityList(component.packageName, user)
                    .firstOrNull { it.componentName == component }
                if (match != null) break
            }
            match?.let { itemFor(it, density) }
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun launchIntent(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            this.component = component
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
}
