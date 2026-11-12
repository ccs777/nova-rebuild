package dev.pulsar.launcher.db

import android.content.Context
import android.content.SharedPreferences

/** Preference keys follow the names recovered from Nova's resource strings where they overlap. */
class LauncherPrefs(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nova", Context.MODE_PRIVATE)

    private val narrowScreen: Boolean
        get() = context.resources.configuration.screenWidthDp < 380

    var gridColumns: Int
        get() = prefs.getString("workspace_columns", null)?.toIntOrNull()
            ?: if (narrowScreen) 3 else 5
        set(value) = prefs.edit().putString("workspace_columns", value.toString()).apply()

    var gridRows: Int
        get() = prefs.getString("workspace_rows", null)?.toIntOrNull() ?: if (narrowScreen) 4 else 5
        set(value) = prefs.edit().putString("workspace_rows", value.toString()).apply()

    var dockCount: Int
        get() = prefs.getString("dock_count", "4")?.toIntOrNull() ?: 4
        set(value) = prefs.edit().putString("dock_count", value.toString()).apply()

    var iconSizeDp: Int
        get() = prefs.getString("icon_size", null)?.toIntOrNull() ?: if (narrowScreen) 60 else 48
        set(value) = prefs.edit().putString("icon_size", value.toString()).apply()

    fun getFirstRunDone(): Boolean = prefs.getBoolean("first_run_done", false)

    fun setFirstRunDone() {
        prefs.edit().putBoolean("first_run_done", true).apply()
    }

    var swipeUpOpensDrawer: Boolean
        get() = prefs.getBoolean("swipe_up_drawer", true)
        set(value) = prefs.edit().putBoolean("swipe_up_drawer", value).apply()

    var swipeDownExpandsNotifications: Boolean
        get() = prefs.getBoolean("swipe_down_notifications", false)
        set(value) = prefs.edit().putBoolean("swipe_down_notifications", value).apply()

    var showDrawerTabs: Boolean
        get() = prefs.getBoolean("drawer_tabs", true)
        set(value) = prefs.edit().putBoolean("drawer_tabs", value).apply()

    var iconPackPackage: String?
        get() = prefs.getString("icon_pack", null)?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString("icon_pack", value).apply()

    var hiddenApps: Set<String>
        get() = prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("hidden_apps", value).apply()

    fun snapshot(): Map<String, *> = prefs.all

    fun replaceAll(values: Map<String, *>) {
        val editor = prefs.edit()
        editor.clear()
        for ((key, value) in values) {
            when (value) {
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    val layoutRevision: Int
        get() = prefs.getInt("layout_revision", 0)

    fun bumpLayoutRevision() {
        prefs.edit().putInt("layout_revision", layoutRevision + 1).apply()
    }

    fun clear() = prefs.edit().clear().commit()
}