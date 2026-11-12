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
        contentDescription = label
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cellWidth = MeasureSpec.getSize(widthMeasureSpec)
        val cellHeight = MeasureSpec.getSize(heightMeasureSpec)
        val density = resources.displayMetrics.density
        val labelSpace = if (labelView.text.isNullOrEmpty()) 0 else (density * 18).toInt()
        val available = (minOf(cellWidth, cellHeight) - labelSpace).coerceAtLeast((density * 16).toInt())
        val side = if (requestedIconSize <= 0) available else minOf(requestedIconSize, available)
        iconView.layoutParams = iconView.layoutParams.apply {
            this.width = side
            this.height = side
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}