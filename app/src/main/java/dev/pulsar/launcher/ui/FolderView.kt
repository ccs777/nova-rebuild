package dev.pulsar.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.pulsar.launcher.R
import dev.pulsar.launcher.model.FolderItem
import dev.pulsar.launcher.model.ItemInfo

class FolderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val card: View
    private val titleView: EditText
    private val grid: RecyclerView
    private val emptyView: TextView
    private val adapter: IconGridAdapter

    var folder: FolderItem? = null
        private set

    var onItemClick: ((ItemInfo) -> Unit)? = null
    var onItemLongClick: ((ItemInfo, View) -> Boolean)? = null
    var onRename: ((FolderItem, String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.folder_view, this, true)
        card = findViewById(R.id.folder_card)
        titleView = findViewById(R.id.folder_title)
        grid = findViewById(R.id.folder_grid)
        emptyView = findViewById(R.id.folder_empty)

        adapter = IconGridAdapter()
        adapter.onClick = { item -> onItemClick?.invoke(item) }
        adapter.onLongClick = { item, view -> onItemLongClick?.invoke(item, view) ?: false }
        grid.layoutManager = GridLayoutManager(context, 4)
        grid.adapter = adapter

        setOnClickListener { dismiss() }
        card.setOnClickListener { }
        titleView.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                commitTitle()
                true
            } else {
                false
            }
        }
        titleView.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitTitle() }

        visibility = GONE
    }

    fun configure(
        iconSizePx: Int,
        iconFor: (ItemInfo) -> Drawable?,
        labelFor: (ItemInfo) -> String,
    ) {
        adapter.iconSize = iconSizePx
        adapter.iconProvider = iconFor
        adapter.labelProvider = labelFor
        (grid.layoutManager as GridLayoutManager).spanCount = 4
    }

    fun show(target: FolderItem) {
        folder = target
        titleView.setText(target.title)
        adapter.submit(target.contents.toList())
        emptyView.visibility = if (target.contents.isEmpty()) VISIBLE else GONE
        visibility = VISIBLE
    }

    fun refresh() {
        val target = folder ?: return
        adapter.submit(target.contents.toList())
        emptyView.visibility = if (target.contents.isEmpty()) VISIBLE else GONE
    }

    fun dismiss() {
        if (visibility != VISIBLE) return
        commitTitle()
        visibility = GONE
        folder = null
        onDismiss?.invoke()
    }

    fun containsPoint(rawX: Float, rawY: Float): Boolean {
        if (visibility != VISIBLE) return false
        val location = IntArray(2)
        card.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + card.width &&
            rawY >= location[1] && rawY <= location[1] + card.height
    }

    private fun commitTitle() {
        val target = folder ?: return
        val value = titleView.text?.toString()?.trim().orEmpty()
        if (value.isNotEmpty() && value != target.title) {
            target.title = value
            onRename?.invoke(target, value)
        }
    }
}