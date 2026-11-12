package dev.novare.launcher.shortcuts

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import dev.novare.launcher.R
import dev.novare.launcher.model.AppItem

/**
 * Lists an app's shortcuts in a dialog. Tapping a row launches it and marks it as picked, so the
 * neutral "Add to home" action can place the last picked shortcut without a long-press gesture.
 */
object ShortcutSheet {

    private const val DISMISS = "Dismiss"
    private const val NO_SHORTCUTS = "No shortcuts"
    private const val MAX_VISIBLE_ROWS = 5
    private const val ROW_HEIGHT_DP = 72

    fun show(
        context: Context,
        app: AppItem,
        repository: ShortcutRepository,
        onLaunch: (ShortcutRecord) -> Unit,
        onAddToHome: (ShortcutRecord) -> Unit,
    ) {
        val shortcuts = repository.forPackage(app.component.packageName)
        if (shortcuts.isEmpty()) {
            Toast.makeText(context, NO_SHORTCUTS, Toast.LENGTH_SHORT).show()
            return
        }

        val highlight = selectionBackground(context)
        val ripple = selectableItemBackground(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        }
        val rows = ArrayList<View>(shortcuts.size)
        var picked = -1
        var dialog: AlertDialog? = null

        fun select(index: Int) {
            picked = index
            rows.forEachIndexed { rowIndex, row ->
                row.background = if (rowIndex == index) highlight else null
            }
            dialog?.let {
                it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                it.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
            }
        }

        for ((index, record) in shortcuts.withIndex()) {
            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(context.dp(48), context.dp(48))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(record.icon ?: app.icon)
            }
            val label = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = context.dp(16) }
                text = record.shortLabel.ifBlank { record.longLabel ?: record.packageName }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                isClickable = true
                isFocusable = true
                foreground = ripple
                addView(icon)
                addView(label)
                setOnClickListener {
                    select(index)
                    onLaunch(record)
                }
            }
            rows.add(row)
            content.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val scroll = ScrollView(context).apply { addView(content) }
        val rowHeight = context.dp(ROW_HEIGHT_DP)
        val contentHeight = shortcuts.size.coerceAtMost(MAX_VISIBLE_ROWS) * rowHeight + context.dp(16)
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
        val scrollHeight = contentHeight.coerceAtMost(maxHeight).coerceAtLeast(rowHeight)

        val alert = AlertDialog.Builder(context)
            .setTitle(app.title.ifBlank { app.component.packageName })
            .setView(scroll)
            .setNeutralButton(context.getString(R.string.menu_add_to_home), null)
            .setPositiveButton(DISMISS, null)
            .create()
        dialog = alert
        alert.setOnShowListener {
            scroll.layoutParams = scroll.layoutParams?.apply { height = scrollHeight }
                ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, scrollHeight)
            alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            alert.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
            alert.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                shortcuts.getOrNull(picked)?.let { record ->
                    onAddToHome(record)
                    alert.dismiss()
                }
            }
        }
        alert.show()
    }

    private fun selectionBackground(context: Context): Drawable = GradientDrawable().apply {
        setColor(themeColor(context, android.R.attr.colorControlHighlight, 0x332196F3))
        cornerRadius = context.dp(12).toFloat()
    }

    private fun selectableItemBackground(context: Context): Drawable? {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            return null
        }
        return runCatching { ContextCompat.getDrawable(context, value.resourceId) }.getOrNull()
    }

    private fun themeColor(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return fallback
        if (value.resourceId == 0) return value.data
        return runCatching {
            ContextCompat.getColorStateList(context, value.resourceId)?.defaultColor
        }.getOrNull() ?: fallback
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
