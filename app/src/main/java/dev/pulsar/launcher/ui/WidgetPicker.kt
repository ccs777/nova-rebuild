package dev.pulsar.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import dev.pulsar.launcher.R
import java.text.Collator
import java.util.Locale

/** Lists installed widget providers and returns the chosen [AppWidgetProviderInfo]. */
object WidgetPicker {

    fun show(context: Context, onPick: (AppWidgetProviderInfo) -> Unit) {
        val manager = AppWidgetManager.getInstance(context)
        val collator = Collator.getInstance(Locale.getDefault())
        val providers = manager.installedProviders
            .filter { it.provider != null }
            .sortedWith { a, b -> collator.compare(label(context, a), label(context, b)) }
        if (providers.isEmpty()) return
        val labels = providers.map { "${label(context, it)} — ${it.provider.packageName}" }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.widgets_title)
            .setItems(labels, DialogInterface.OnClickListener { _, which -> onPick(providers[which]) })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun label(context: Context, info: AppWidgetProviderInfo): String {
        return runCatching { info.loadLabel(context.packageManager) }.getOrNull()
            ?: info.provider?.packageName
            ?: "Widget"
    }
}
