package dev.novare.launcher.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Process

data class ShortcutRecord(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String?,
    val intent: Intent,
    val icon: Drawable?,
    val isDynamic: Boolean,
)

/**
 * Reads the shortcuts an app publishes. LauncherApps is the platform source once this app holds
 * shortcut-host permission; before that, activities handling ACTION_CREATE_SHORTCUT stand in.
 */
class ShortcutRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val density: Int = context.resources.displayMetrics.densityDpi

    fun forPackage(packageName: String): List<ShortcutRecord> {
        if (packageName.isEmpty()) return emptyList()
        val platform = runCatching { platformShortcuts(packageName) }.getOrDefault(emptyList())
        if (platform.isNotEmpty()) return platform
        return runCatching { legacyShortcuts(packageName) }.getOrDefault(emptyList())
    }

    /** Returns an empty list when LauncherApps is unavailable or this app is not the shortcut host. */
    private fun platformShortcuts(packageName: String): List<ShortcutRecord> {
        val launcherApps = launcherApps ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    ShortcutQuery.FLAG_MATCH_PINNED or
                    ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
        val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle()) ?: return emptyList()
        val records = ArrayList<ShortcutRecord>(shortcuts.size)
        for (shortcut in shortcuts) {
            // A shortcut without an intent can neither be launched nor placed on the workspace.
            val intent = shortcut.intent ?: continue
            records.add(
                ShortcutRecord(
                    id = shortcut.id,
                    packageName = packageName,
                    shortLabel = shortcut.shortLabel?.toString().orEmpty(),
                    longLabel = shortcut.longLabel?.toString(),
                    intent = intent,
                    icon = runCatching { launcherApps.getShortcutBadgedIconDrawable(shortcut, density) }.getOrNull(),
                    isDynamic = shortcut.isDynamic,
                ),
            )
        }
        return records
    }

    private fun legacyShortcuts(packageName: String): List<ShortcutRecord> {
        val query = Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(packageName)
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(query, 0)
        val records = ArrayList<ShortcutRecord>(resolved.size)
        val seen = HashSet<String>()
        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val component = ComponentName(activityInfo.packageName, activityInfo.name)
            val id = component.flattenToString()
            if (!seen.add(id)) continue
            records.add(
                ShortcutRecord(
                    id = id,
                    packageName = packageName,
                    shortLabel = runCatching { info.loadLabel(packageManager).toString() }
                        .getOrDefault(activityInfo.name),
                    longLabel = null,
                    intent = Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component),
                    icon = runCatching { info.loadIcon(packageManager) }.getOrNull(),
                    isDynamic = false,
                ),
            )
        }
        return records
    }
}
