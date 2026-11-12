package dev.novare.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.novare.launcher.R
import dev.novare.launcher.model.AppItem
import dev.novare.launcher.model.DrawerTab
import dev.novare.launcher.model.ItemInfo

class AllAppsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val searchView: EditText
    private val grid: RecyclerView
    private val tabsContainer: LinearLayout
    private val tabsScroll: HorizontalScrollView
    private val emptyView: TextView
    private val adapter: IconGridAdapter

    private var allApps: List<AppItem> = emptyList()
    private var tabs: List<DrawerTab> = emptyList()
    private var hidden: Set<String> = emptySet()
    private var showTabs = true
    private var selectedTab = 0

    var isOpen = false
        private set

    var onAppClick: ((AppItem) -> Unit)? = null
    var onAppLongClick: ((AppItem, View) -> Boolean)? = null
    var onTabLongClick: ((DrawerTab, View) -> Unit)? = null
    var onCreateTab: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.all_apps, this, true)
        searchView = findViewById(R.id.drawer_search)
        grid = findViewById(R.id.drawer_grid)
        tabsContainer = findViewById(R.id.drawer_tabs)
        tabsScroll = findViewById(R.id.tabs_scroll)
        emptyView = findViewById(R.id.drawer_empty)

        adapter = IconGridAdapter()
        adapter.onClick = { item -> if (item is AppItem) onAppClick?.invoke(item) }
        adapter.onLongClick = { item, view ->
            if (item is AppItem) onAppLongClick?.invoke(item, view) ?: false else false
        }
        grid.layoutManager = GridLayoutManager(context, 5)
        grid.adapter = adapter

        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
    }

    fun configure(
        iconSizePx: Int,
        iconFor: (ItemInfo) -> Drawable?,
        labelFor: (ItemInfo) -> String,
    ) {
        val columns = if (resources.configuration.screenWidthDp >= 600) 7 else 5
        (grid.layoutManager as GridLayoutManager).spanCount = columns
        adapter.iconSize = iconSizePx
        adapter.iconProvider = iconFor
        adapter.labelProvider = labelFor
    }

    fun bind(
        apps: List<AppItem>,
        drawerTabs: List<DrawerTab>,
        showTabs: Boolean,
        hiddenApps: Set<String>,
    ) {
        allApps = apps
        tabs = drawerTabs
        this.showTabs = showTabs
        hidden = hiddenApps
        if (selectedTab > tabs.size) selectedTab = 0
        rebuildTabs()
        applyFilter()
    }

    fun selectTab(index: Int) {
        selectedTab = index
        rebuildTabs()
        applyFilter()
    }

    fun selectedTabName(): String? = tabs.getOrNull(selectedTab - 1)?.name

    private fun rebuildTabs() {
        tabsContainer.removeAllViews()
        tabsScroll.visibility = if (showTabs) VISIBLE else GONE
        if (!showTabs) return

        fun addTab(label: String, index: Int, tab: DrawerTab?) {
            val text = TextView(context).apply {
                this.text = label
                textSize = 15f
                setPadding(dp(14), dp(8), dp(14), dp(10))
                setTextColor(
                    resources.getColor(
                        if (selectedTab == index) R.color.tab_selected else R.color.tab_unselected,
                        null,
                    )
                )
                setOnClickListener {
                    if (tab == null && index == 0) selectTab(0) else selectTab(index)
                }
                if (tab != null) {
                    setOnLongClickListener { onTabLongClick?.invoke(tab, this); true }
                }
            }
            tabsContainer.addView(text)
        }

        addTab(context.getString(R.string.drawer_tab_all), 0, null)
        tabs.forEachIndexed { index, tab -> addTab(tab.name, index + 1, tab) }
        tabsContainer.addView(TextView(context).apply {
            text = "+"
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), dp(10))
            setTextColor(resources.getColor(R.color.tab_unselected, null))
            setOnClickListener { onCreateTab?.invoke() }
        })
    }

    private fun applyFilter() {
        val query = searchView.text?.toString()?.trim()?.lowercase().orEmpty()
        val base = allApps.filter { !hidden.contains(it.key()) }
        val tabFiltered = if (selectedTab == 0) {
            base
        } else {
            val tab = tabs.getOrNull(selectedTab - 1)
            if (tab == null) base else base.filter { tab.components.contains(it.key()) }
        }
        val filtered = if (query.isEmpty()) {
            tabFiltered
        } else {
            tabFiltered.filter { it.title.lowercase().contains(query) }
        }
        adapter.submit(filtered)
        emptyView.visibility = if (filtered.isEmpty()) VISIBLE else GONE
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        visibility = VISIBLE
        alpha = 0f
        translationY = dp(36).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        searchView.text.clear()
        animate().alpha(0f).translationY(dp(36).toFloat()).setDuration(160)
            .withEndAction { visibility = GONE }.start()
    }

    fun backPressed(): Boolean {
        if (!isOpen) return false
        close()
        return true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}