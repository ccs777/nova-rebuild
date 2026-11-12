package dev.novare.launcher.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import dev.novare.launcher.model.AppItem
import java.text.Collator
import java.util.Locale

/** Loads launchable activities with PackageManager; no LauncherApps default-home requirement. */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
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
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        val loaded = ArrayList<AppItem>(resolved.size)
        val seen = HashSet<String>()
        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val component = ComponentName(activityInfo.packageName, activityInfo.name)
            val key = component.flattenToString()
            if (!seen.add(key)) continue
            val item = AppItem(component)
            item.title = info.loadLabel(packageManager).toString()
            item.icon = info.loadIcon(packageManager)
            item.intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                this.component = component
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            loaded.add(item)
        }
        loaded.sortWith { a, b -> collator.compare(a.title, b.title) }
        apps = loaded
        byComponent = loaded.associateBy { it.key() }
        onChange?.invoke()
    }

    fun all(): List<AppItem> = apps

    fun findByComponent(component: ComponentName): AppItem? = byComponent[component.flattenToString()]

    fun resolve(component: ComponentName): AppItem? {
        val existing = findByComponent(component)
        if (existing != null) return existing
        return runCatching {
            val info = packageManager.getActivityInfo(component, 0)
            val item = AppItem(component)
            item.title = info.loadLabel(packageManager).toString()
            item.icon = info.loadIcon(packageManager)
            item.intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                this.component = component
            }
            item
        }.getOrNull()
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
}