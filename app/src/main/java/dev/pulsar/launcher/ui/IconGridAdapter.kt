package dev.pulsar.launcher.ui

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.pulsar.launcher.model.ItemInfo

/** RecyclerView adapter shared by the app drawer and the open-folder grid. */
class IconGridAdapter : RecyclerView.Adapter<IconGridAdapter.Holder>() {

    var items: List<ItemInfo> = emptyList()
        private set

    var iconSize: Int = 0
    var iconProvider: (ItemInfo) -> Drawable? = { null }
    var labelProvider: (ItemInfo) -> String = { it.title }
    var onClick: ((ItemInfo) -> Unit)? = null
    var onLongClick: ((ItemInfo, View) -> Boolean)? = null

    fun submit(list: List<ItemInfo>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val iconView: AppIconView) : RecyclerView.ViewHolder(iconView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = AppIconView(parent.context)
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        view.setPadding(4, 12, 4, 12)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.iconView.bind(item, iconProvider(item), iconSize, labelProvider(item))
        holder.iconView.setOnClickListener { onClick?.invoke(item) }
        holder.iconView.setOnLongClickListener { onLongClick?.invoke(item, it) ?: false }
    }
}