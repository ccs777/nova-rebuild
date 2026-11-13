package dev.pulsar.launcher

import android.content.Context

/**
 * Opens the notification shade. Tries the status bar service first (works for apps that hold
 * EXPAND_STATUS_BAR, which the manifest declares), then falls back to the accessibility
 * service if the user has enabled it.
 */
object NotificationShade {

    fun expand(context: Context): Boolean {
        val viaStatusBar = runCatching {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(service)
            true
        }.getOrDefault(false)
        return viaStatusBar || LauncherAccessibilityService.expandNotifications()
    }
}
