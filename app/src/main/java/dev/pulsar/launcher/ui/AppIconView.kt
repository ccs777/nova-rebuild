package dev.pulsar.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.pulsar.launcher.R
import dev.pulsar.launcher.model.ItemInfo

class AppIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    val iconView: ImageView
    val labelView: TextView

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.app_icon, this, true)
        iconView = findViewById(R.id.icon)
        labelView = findViewById(R.id.label)
        isClickable = true
        isLongClickable = true
        isHapticFeedbackEnabled = true
    }

    private var requestedIconSize = 0

    fun bind(item: ItemInfo, drawable: Drawable?, iconSizePx: Int, label: String) {
        // icon bounds are recomputed in onMeasure so small screens never overflow a cell
        requestedIconSize = iconSizePx
        iconView.layoutParams = iconView.layoutParams.apply {
            width = iconSizePx
            height = iconSizePx
        }
        iconView.setImageDrawable(drawable)
        labelView.text = label
        labelView.visibility = if (label.isEmpty()) GONE else VISIBLE
        // drawer (iconSizePx 0) uses larger labels to match the stock grid look
        labelView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (iconSizePx <= 0) 14f else 12f)
        contentDescription = label
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Grids (drawer, folder) measure cells with WRAP_CONTENT height, so the height spec
        // carries no usable size. Clamp only by the cell width, which is always exact.
        val cellWidth = MeasureSpec.getSize(widthMeasureSpec)
        val side = when {
            cellWidth <= 0 -> requestedIconSize
            requestedIconSize <= 0 -> (cellWidth * 0.85f).toInt()
            else -> minOf(requestedIconSize, cellWidth)
        }
        iconView.layoutParams = iconView.layoutParams.apply {
            this.width = side
            this.height = side
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}