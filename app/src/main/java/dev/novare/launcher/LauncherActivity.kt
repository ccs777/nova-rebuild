package dev.novare.launcher

import android.app.AlertDialog
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.novare.launcher.app.AppRepository
import dev.novare.launcher.app.IconCache
import dev.novare.launcher.app.IconPackManager
import dev.novare.launcher.appwidget.WidgetFrame
import dev.novare.launcher.appwidget.WidgetHostManager
import dev.novare.launcher.db.LauncherPrefs
import dev.novare.launcher.model.AppItem
import dev.novare.launcher.model.Container
import dev.novare.launcher.model.DrawerTab
import dev.novare.launcher.model.FolderItem
import dev.novare.launcher.model.ItemInfo
import dev.novare.launcher.model.ItemType
import dev.novare.launcher.model.LauncherChange
import dev.novare.launcher.model.LauncherLayout
import dev.novare.launcher.model.LauncherModel
import dev.novare.launcher.ui.AllAppsView
import dev.novare.launcher.ui.CellLayout
import dev.novare.launcher.ui.DragController
import dev.novare.launcher.ui.DragLayer
import dev.novare.launcher.ui.FolderView
import dev.novare.launcher.ui.PagedView
import dev.novare.launcher.ui.WidgetBindFlow
import dev.novare.launcher.ui.WidgetPicker

class LauncherActivity : AppCompatActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var model: LauncherModel
    private lateinit var appRepo: AppRepository
    private lateinit var iconPacks: IconPackManager
    private lateinit var iconCache: IconCache
    private val layout: LauncherLayout get() = model.layout
    private lateinit var dragController: DragController
    private lateinit var widgetHost: WidgetHostManager

    private lateinit var dragLayer: DragLayer
    private lateinit var pager: PagedView
    private lateinit var hotseat: CellLayout
    private lateinit var indicator: LinearLayout
    private lateinit var bottomBar: View
    private lateinit var drawerBar: View
    private lateinit var drawer: AllAppsView
    private lateinit var folderView: FolderView
    private lateinit var deleteTarget: View

    private var gridColumns = 5
    private var gridRows = 5
    private var dockCount = 4
    private var iconSizePx = 0
    private var prefsFingerprint = ""
    private var layoutRevision = -1
    private var lastPageFlip = 0L
    private var pendingIconApp: AppItem? = null
    private var dragAnchor: View? = null
    private var pendingWidgetItem: ItemInfo? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val item = pendingWidgetItem
        pendingWidgetItem = null
        val boundId = WidgetBindFlow.parseResult(result.resultCode, result.data)
        if (item != null && boundId > 0) {
            item.appWidgetId = boundId
            model.addToDesktop(item, item.screen, item.cellX, item.cellY)
        }
    }

    private val pickIcon = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val app = pendingIconApp
        pendingIconApp = null
        if (uri != null && app != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            model.setCustomIcon(app.key(), uri.toString())
            iconCache.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        applySettings()
        setContentView(R.layout.activity_launcher)

        dragLayer = findViewById(R.id.drag_layer)
        pager = findViewById(R.id.workspace)
        hotseat = findViewById(R.id.hotseat)
        indicator = findViewById(R.id.page_indicator)
        bottomBar = findViewById(R.id.bottom_bar)
        drawerBar = findViewById(R.id.drawer_bar)
        drawer = findViewById(R.id.all_apps)
        folderView = findViewById(R.id.folder_view)
        deleteTarget = findViewById(R.id.delete_target)

        model = LauncherModel(this)
        model.onChange = { change -> applyChange(change) }
        model.load()
        iconPacks = IconPackManager(this)
        iconCache = IconCache(this, prefs, iconPacks)
        iconCache.customIcons.putAll(layout.customIcons)
        iconCache.appResolver = { component -> appRepo.resolve(component) }

        appRepo = AppRepository(this)
        appRepo.onChange = {
            runOnUiThread {
                if (!::dragController.isInitialized || !dragController.isDragging) rebuildAll()
            }
        }
        appRepo.start()

        if (!prefs.getFirstRunDone()) {
            seedFirstRun()
            prefs.setFirstRunDone()
        }

        widgetHost = WidgetHostManager(this)
        widgetHost.onWidgetIdChanged = { item, id -> item.appWidgetId = id }
        widgetHost.start()
        dragController = DragController(dragLayer)
        wireDragController()
        wireViews()
        registerBackHandling()
        applyInsets()
        rebuildAll()
    }

    override fun onResume() {
        super.onResume()
        val fingerprint = settingsFingerprint()
        val revision = prefs.layoutRevision
        if (fingerprint != prefsFingerprint || revision != layoutRevision) {
            prefsFingerprint = fingerprint
            layoutRevision = revision
            applySettings()
            iconCache.clear()
            model.load()
            iconCache.customIcons.clear()
            iconCache.customIcons.putAll(layout.customIcons)
            model.normalize(gridColumns, gridRows)
            rebuildAll()
        }
    }

    override fun onDestroy() {
        appRepo.stop()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- settings

    private fun applySettings() {
        gridColumns = prefs.gridColumns
        gridRows = prefs.gridRows
        dockCount = prefs.dockCount
        iconSizePx = dp(prefs.iconSizeDp)
        prefsFingerprint = settingsFingerprint()
    }

    private fun settingsFingerprint(): String = listOf(
        prefs.gridColumns, prefs.gridRows, prefs.dockCount, prefs.iconSizeDp,
        prefs.iconPackPackage, prefs.showDrawerTabs, prefs.hiddenApps.size,
        prefs.swipeUpOpensDrawer, prefs.swipeDownExpandsNotifications,
    ).joinToString("|")

    private fun seedFirstRun() {
        val present = layout.hotseat.filterIsInstance<AppItem>().map { it.key() }.toMutableSet()
        present.addAll(layout.desktop.filterIsInstance<AppItem>().map { it.key() })
        for (app in appRepo.defaultDock()) {
            if (layout.hotseat.size >= dockCount) break
            if (!present.add(app.key())) continue
            model.addToHotseat(copyApp(app), layout.hotseat.size)
        }
    }

    // ------------------------------------------------------------------ wiring

    private fun wireViews() {
        pager.onPageChanged = { updateIndicator(it) }
        pager.isFocusable = false

        dragLayer.onSwipeUp = {
            if (!drawer.isOpen) openDrawer()
        }
        dragLayer.onSwipeDown = {
            if (prefs.swipeDownExpandsNotifications) {
                val expanded = LauncherAccessibilityService.expandNotifications()
                if (!expanded) {
                    Toast.makeText(this, R.string.accessibility_description, Toast.LENGTH_LONG).show()
                    runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
            }
        }

        drawerBar.setOnClickListener { openDrawer() }
        findViewById<View>(R.id.drawer_button).setOnClickListener { openDrawer() }

        drawer.onAppClick = { app -> launchApp(app) }
        drawer.onAppLongClick = { app, view -> beginDrag(copyApp(app), view, fromDrawer = true) }
        drawer.onCreateTab = { createTabDialog() }
        drawer.onTabLongClick = { tab, _ -> showTabMenu(tab) }

        folderView.onItemClick = { item -> if (item is AppItem) launchApp(item) }
        folderView.onItemLongClick = { item, view -> beginDrag(item, view, fromDrawer = false) }
        folderView.onRename = { folder, name -> model.rename(folder, name) }
        folderView.onDismiss = { }

    }

    private fun wireDragController() {
        dragController.onDragStarted = { _, fromDrawer ->
            if (fromDrawer) drawer.close()
            if (folderView.visibility == View.VISIBLE) folderView.dismiss()
            deleteTarget.visibility = if (fromDrawer) View.GONE else View.VISIBLE
        }
        dragController.onDragMove = { rawX, _ ->
            val width = dragLayer.width
            if (width > 0) {
                val now = SystemClock.uptimeMillis()
                if (now - lastPageFlip > 400) {
                    when {
                        rawX < width * 0.12f && pager.currentPage > 0 -> {
                            pager.setCurrentPage(pager.currentPage - 1, false)
                            lastPageFlip = now
                        }

                        rawX > width * 0.88f && pager.currentPage < pager.pageCount - 1 -> {
                            pager.setCurrentPage(pager.currentPage + 1, false)
                            lastPageFlip = now
                        }
                    }
                }
            }
        }
        dragController.onDrop = { item, fromDrawer, rawX, rawY, moved ->
            handleDrop(item, fromDrawer, rawX, rawY, moved)
        }
        dragController.onDragEnded = {
            deleteTarget.visibility = View.GONE
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(dragLayer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomBar.setPadding(0, 0, 0, bars.bottom)
            pager.setPadding(0, bars.top, 0, 0)
            insets
        }
    }

    // ------------------------------------------------------------------- build

    private fun rebuildAll() {
        if (!::dragController.isInitialized) return
        val pageCount = (layout.desktop.maxOfOrNull { it.screen } ?: 0) + 2
        val pages = ArrayList<View>(pageCount)
        for (screen in 0 until pageCount) {
            val page = CellLayout(this)
            page.setGrid(gridColumns, gridRows)
            page.gapPx = dp(2)
            page.iconSizePx = iconSizePx
            page.setPadding(dp(10), dp(6), dp(10), dp(6))
            page.widgetViewProvider = { item ->
                if (item.itemType == ItemType.APPWIDGET) widgetView(item) else null
            }
            page.iconProvider = { iconCache.iconFor(it) }
            page.labelProvider = { iconCache.labelFor(it) }
            page.onItemClick = { item -> onItemClicked(item) }
            page.onItemLongClick = { item, view -> beginDrag(item, view, fromDrawer = false) }
            page.onEmptyLongClick = { x, y -> showWorkspaceMenu(x, y) }
            page.bind(layout.desktop.filter { it.screen == screen })
            pages.add(page)
        }
        pager.setPages(pages)

        hotseat.setGrid(dockCount, 1)
        hotseat.gapPx = dp(2)
        hotseat.iconSizePx = iconSizePx
        hotseat.setPadding(dp(6), 0, dp(6), 0)
        hotseat.widgetViewProvider = { item ->
            if (item.itemType == ItemType.APPWIDGET) widgetView(item) else null
        }
        hotseat.iconProvider = { iconCache.iconFor(it) }
        hotseat.labelProvider = { iconCache.labelFor(it) }
        hotseat.onItemClick = { item -> onItemClicked(item) }
        hotseat.onItemLongClick = { item, view -> beginDrag(item, view, fromDrawer = false) }
        hotseat.onEmptyLongClick = { x, y -> showWorkspaceMenu(x, y) }
        hotseat.bind(layout.hotseat)

        drawer.configure(iconSizePx, { iconCache.iconFor(it) }, { iconCache.labelFor(it) })
        drawer.bind(appRepo.all(), layout.tabs, prefs.showDrawerTabs, prefs.hiddenApps)

        folderView.configure(iconSizePx, { iconCache.iconFor(it) }, { iconCache.labelFor(it) })
        updateIndicator(pager.currentPage)
        updateWidgetCellSize()
    }

    /** Wraps a hosted widget in a frame that owns selection chrome, resize and drag. */
    private fun widgetView(item: ItemInfo): View? {
        val hostView = widgetHost.viewFor(item) ?: return null
        val frame = WidgetFrame(this)
        frame.item = item
        frame.setCellSize(widgetCellWidth(), widgetCellHeight())
        frame.addView(
            hostView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        frame.onResize = { target, spanX, spanY ->
            widgetHost.updateSpans(target, spanX, spanY)
            model.updateSpans(target, spanX, spanY)
        }
        frame.onLongPress = { target -> beginDrag(target, frame, fromDrawer = false) }
        return frame
    }

    private fun widgetCellWidth(): Int = (pager.width - dp(20)).coerceAtLeast(dp(60)) / gridColumns

    private fun widgetCellHeight(): Int = (pager.height - dp(12)).coerceAtLeast(dp(60)) / gridRows

    private fun updateWidgetCellSize() {
        val width = pager.width - dp(20)
        val height = pager.height - dp(12)
        if (width > 0 && height > 0) {
            widgetHost.setCellSize(width / gridColumns, height / gridRows)
        }
    }

    /** Rebinds a single workspace page from the model instead of rebuilding every page. */
    private fun refreshScreen(screen: Int) {
        val page = pager.pageAt(screen) as? CellLayout ?: return
        page.bind(layout.desktop.filter { it.screen == screen })
    }

    private fun refreshHotseat() {
        hotseat.bind(layout.hotseat)
    }

    private fun refreshFolder() {
        if (folderView.visibility == View.VISIBLE) folderView.refresh()
    }

    private fun updateIndicator(current: Int) {
        indicator.removeAllViews()
        val count = pager.pageCount
        if (count <= 1) return
        for (index in 0 until count) {
            val dot = View(this)
            val size = dp(6)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginStart = dp(3)
            params.marginEnd = dp(3)
            dot.layoutParams = params
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getColor(if (index == current) R.color.dot_active else R.color.dot_inactive))
            }
            indicator.addView(dot)
        }
    }

    // ------------------------------------------------------------------ actions

    private fun onItemClicked(item: ItemInfo) {
        when (item) {
            is FolderItem -> folderView.show(item)
            is AppItem -> launchApp(item)
        }
    }

    private fun launchApp(app: AppItem) {
        val intent = app.intent ?: Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.component
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, app.title, Toast.LENGTH_SHORT).show() }
    }

    private fun openDrawer() {
        drawer.open()
    }

    private fun beginDrag(item: ItemInfo, view: View, fromDrawer: Boolean): Boolean {
        dragAnchor = view
        dragController.startDrag(item, view, fromDrawer)
        return true
    }

    // -------------------------------------------------------------- drop logic

    private fun handleDrop(item: ItemInfo, fromDrawer: Boolean, rawX: Float, rawY: Float, moved: Boolean) {
        if (fromDrawer && !moved && item is AppItem) {
            showDrawerItemMenu(item, dragLayer)
            return
        }
        if (!fromDrawer && !moved) {
            showItemMenu(item)
            return
        }
        if (!fromDrawer && deleteTarget.visibility == View.VISIBLE && inside(deleteTarget, rawX, rawY)) {
            model.detach(item)
            refreshScreen(item.screen)
            refreshHotseat()
            refreshFolder()
            return
        }

        if (folderView.visibility == View.VISIBLE && folderView.containsPoint(rawX, rawY)) {
            val folder = folderView.folder
            if (folder != null && item is AppItem) {
                model.addToFolder(folder, item)
                folderView.refresh()
            }
            return
        }

        val hotseatCell = cellAt(hotseat, rawX, rawY)
        if (hotseatCell != null) {
            placeInHotseat(item, hotseatCell[0])
            return
        }

        val screen = pager.pageUnder(rawX, rawY)
        val page = pager.pageAt(screen) as? CellLayout
        if (page != null) {
            val local = localPoint(page, rawX, rawY)
            val cell = page.hitCell(local[0], local[1])
            if (cell != null) {
                placeAt(item, screen, cell[0], cell[1], moved)
                return
            }
        }

        if (fromDrawer) {
            val current = pager.pageAt(pager.currentPage) as? CellLayout
            val free = current?.findVacantCell(1, 1)
            if (current != null && free != null) {
                placeAt(item, pager.currentPage, free[0], free[1], moved)
                return
            }
        }

    }

    private fun placeAt(item: ItemInfo, screen: Int, x: Int, y: Int, moved: Boolean) {
        val page = pager.pageAt(screen) as? CellLayout
        val occupied = page?.itemAt(x, y)
        if (occupied != null && occupied !== item) {
            if (occupied is FolderItem && item is AppItem) {
                model.addToFolder(occupied, item)
                return
            }
            if (occupied is AppItem && item is AppItem) {
                makeFolder(occupied, item, screen, x, y)
                return
            }
            if (occupied is FolderItem && item is FolderItem) {
                return
            }
        }
        model.moveToDesktop(item, screen, x, y)
    }

    private fun makeFolder(target: AppItem, dragged: AppItem, screen: Int, x: Int, y: Int) {
        val folder = FolderItem()
        folder.title = getString(R.string.folder_default_name)
        folder.container = Container.DESKTOP
        folder.screen = screen
        folder.cellX = x
        folder.cellY = y
        model.detach(target)
        model.detach(dragged)
        model.addToDesktop(folder, screen, x, y)
        model.addToFolder(folder, target)
        model.addToFolder(folder, dragged)
    }

    private fun placeInHotseat(item: ItemInfo, index: Int) {
        val slot = index.coerceIn(0, (dockCount - 1).coerceAtLeast(0))
        val existing = layout.hotseat.getOrNull(slot)
        if (existing != null && existing !== item) {
            val cell = firstVacantCell()
            model.moveToDesktop(existing, cell[0], cell[1], cell[2])
        }
        model.moveToHotseat(item, slot)
    }

    private fun firstVacantCell(): IntArray = model.firstVacantCell(gridColumns, gridRows)

    private fun applyChange(change: LauncherChange) {
        when (change) {
            is LauncherChange.ItemAdded -> {
                if (change.container == Container.DESKTOP) {
                    if (change.item.screen >= pager.pageCount - 1) rebuildAll() else refreshScreen(change.item.screen)
                } else {
                    refreshHotseat()
                }
            }

            is LauncherChange.ItemRemoved -> {
                refreshScreen(change.item.screen)
                refreshHotseat()
                refreshFolder()
            }

            is LauncherChange.ItemMoved -> {
                refreshScreen(change.item.screen)
                refreshScreen(change.fromScreen)
                refreshHotseat()
            }

            is LauncherChange.PositionsChanged -> {
                change.items.map { it.screen }.distinct().forEach { refreshScreen(it) }
                refreshHotseat()
            }

            is LauncherChange.TitleChanged -> {
                refreshScreen(change.item.screen)
                refreshHotseat()
                refreshFolder()
            }

            is LauncherChange.TabAdded,
            is LauncherChange.TabRemoved,
            is LauncherChange.CustomIconChanged -> {
                iconCache.clear()
                iconCache.customIcons.clear()
                iconCache.customIcons.putAll(layout.customIcons)
                rebuildAll()
            }
        }
    }

    // ------------------------------------------------------------------- menus

    private fun showItemMenu(item: ItemInfo) {
        val anchor = dragAnchor?.takeIf { it.isAttachedToWindow } ?: dragLayer
        val popup = PopupMenu(this, anchor)
        if (item is FolderItem) {
            popup.menu.add(0, 1, 0, R.string.menu_edit)
            popup.menu.add(0, 2, 1, R.string.menu_remove)
        } else if (item is AppItem) {
            popup.menu.add(0, 1, 0, R.string.menu_edit)
            popup.menu.add(0, 2, 1, R.string.menu_change_icon)
            popup.menu.add(0, 3, 2, R.string.menu_app_info)
            popup.menu.add(0, 4, 3, R.string.menu_uninstall)
            popup.menu.add(0, 5, 4, R.string.menu_remove)
        }
        popup.setOnMenuItemClickListener { entry ->
            when (entry.itemId) {
                1 -> if (item is FolderItem) renameFolder(item) else renameItem(item)
                2 -> if (item is FolderItem) {
                    model.detach(item)
                    refreshScreen(item.screen)
                    refreshHotseat()
                } else {
                    pickIcon(item as AppItem)
                }

                3 -> openAppInfo((item as AppItem).component)
                4 -> uninstall((item as AppItem).component)
                5 -> {
                    model.detach(item)
                    refreshScreen(item.screen)
                    refreshHotseat()
                    refreshFolder()
                }
            }
            true
        }
        popup.show()
    }

    private fun showDrawerItemMenu(app: AppItem, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.menu_add_to_home)
        popup.menu.add(0, 2, 1, R.string.menu_add_to_tab)
        popup.menu.add(0, 3, 2, R.string.menu_hide_app)
        popup.menu.add(0, 4, 3, R.string.menu_app_info)
        popup.setOnMenuItemClickListener { entry ->
            when (entry.itemId) {
                1 -> addToHome(app)
                2 -> addToTabDialog(app)
                3 -> {
                    prefs.hiddenApps = prefs.hiddenApps + app.key()
                    rebuildAll()
                }

                4 -> openAppInfo(app.component)
            }
            true
        }
        popup.show()
    }

    private fun showWorkspaceMenu(rawX: Float, rawY: Float) {
        val anchor = View(this)
        val location = IntArray(2)
        dragLayer.getLocationOnScreen(location)
        dragLayer.addView(
            anchor,
            FrameLayout.LayoutParams(dp(1), dp(1)).apply {
                leftMargin = (rawX - location[0]).toInt()
                topMargin = (rawY - location[1]).toInt()
            },
        )
        val popup = PopupMenu(this, anchor)
        popup.setOnDismissListener { dragLayer.removeView(anchor) }
        popup.menu.add(0, 1, 0, R.string.settings_title)
        popup.menu.add(0, 2, 1, "Wallpaper")
        popup.menu.add(0, 3, 2, R.string.widgets_title)
        popup.setOnMenuItemClickListener { entry ->
            when (entry.itemId) {
                1 -> startActivity(Intent(this, SettingsActivity::class.java))
                2 -> runCatching { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
                3 -> WidgetPicker.show(this) { info -> addWidget(info) }
            }
            true
        }
        popup.show()
    }

    private fun showTabMenu(tab: DrawerTab) {
        val popup = PopupMenu(this, dragLayer)
        popup.menu.add(0, 1, 0, R.string.menu_rename_tab)
        popup.menu.add(0, 2, 1, R.string.menu_remove_tab)
        popup.setOnMenuItemClickListener { entry ->
            when (entry.itemId) {
                1 -> promptForName(getString(R.string.menu_rename_tab), tab.name) { name ->
                    model.renameTab(tab, name)
                }

                2 -> {
                    model.removeTab(tab)
                    drawer.selectTab(0)
                }
            }
            true
        }
        popup.show()
    }

    private fun renameFolder(folder: FolderItem) {
        promptForName(getString(R.string.menu_edit), folder.title) { name ->
            model.rename(folder, name)
            if (folderView.folder === folder) folderView.show(folder)
        }
    }

    private fun renameItem(item: ItemInfo) {
        promptForName(getString(R.string.menu_edit), item.title) { name ->
            model.rename(item, name)
        }
    }

    private fun createTabDialog() {
        promptForName(getString(R.string.menu_new_tab), "") { name ->
            model.addTab(DrawerTab(0, name))
            drawer.selectTab(layout.tabs.size)
        }
    }

    private fun addToTabDialog(app: AppItem) {
        if (layout.tabs.isEmpty()) {
            createTabDialog()
            return
        }
        val names = layout.tabs.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_add_to_tab)
            .setItems(names) { _, which ->
                model.addComponentToTab(layout.tabs[which], app.key())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addToHome(app: AppItem) {
        val cell = firstVacantCell()
        val copy = copyApp(app)
        model.addToDesktop(copy, cell[0], cell[1], cell[2])
        pager.setCurrentPage(cell[0].coerceAtMost(pager.pageCount - 1), false)
        Toast.makeText(this, app.title, Toast.LENGTH_SHORT).show()
    }

    private fun promptForName(title: String, initial: String, onValue: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(text.length)
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) onValue(value)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        val provider = info.provider ?: return
        val width = (dragLayer.width - dp(20)).coerceAtLeast(dp(120))
        val cellPx = width / gridColumns
        val spanX = ((info.minWidth + cellPx - 1) / cellPx).coerceIn(1, gridColumns)
        val heightPx = (pager.height - dp(24)).coerceAtLeast(dp(120))
        val cellPy = heightPx / gridRows
        val minHeight = if (info.minResizeHeight > 0) info.minHeight else info.minHeight
        val spanY = ((minHeight + cellPy - 1) / cellPy).coerceIn(1, gridRows)

        val item = ItemInfo(itemType = ItemType.APPWIDGET)
        item.title = WidgetPicker.label(this, info)
        item.appWidgetProvider = provider
        item.spanX = spanX
        item.spanY = spanY
        item.container = Container.DESKTOP
        val cell = firstVacantCell()
        item.screen = cell[0]
        item.cellX = cell[1]
        item.cellY = cell[2]

        if (!widgetHost.bindNew(provider, item)) {
            pendingWidgetItem = item
            runCatching { bindWidget.launch(WidgetBindFlow.intent(item.appWidgetId, provider)) }
                .onFailure { Toast.makeText(this, R.string.widget_failed, Toast.LENGTH_SHORT).show() }
            return
        }
        model.addToDesktop(item, item.screen, item.cellX, item.cellY)
    }

    private fun pickIcon(app: AppItem) {
        pendingIconApp = app
        pickIcon.launch(arrayOf("image/*"))
    }

    private fun openAppInfo(component: ComponentName) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${component.packageName}"))
            )
        }
    }

    private fun uninstall(component: ComponentName) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${component.packageName}")))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun copyApp(app: AppItem): AppItem {
        val copy = AppItem(app.component)
        copy.title = app.title
        copy.icon = app.icon
        copy.intent = app.intent
        return copy
    }

    private fun inside(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + view.width &&
            rawY >= location[1] && rawY <= location[1] + view.height
    }

    private fun localPoint(view: View, rawX: Float, rawY: Float): FloatArray {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return floatArrayOf(rawX - location[0], rawY - location[1])
    }

    private fun cellAt(container: CellLayout, rawX: Float, rawY: Float): IntArray? {
        if (container.visibility != View.VISIBLE || container.width == 0) return null
        if (!inside(container, rawX, rawY)) return null
        val local = localPoint(container, rawX, rawY)
        val cell = container.hitCell(local[0], local[1]) ?: return null
        if (container === hotseat) return intArrayOf(cell[0])
        return cell
    }

    private fun registerBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.backPressed() -> Unit
                    folderView.visibility == View.VISIBLE -> folderView.dismiss()
                    else -> Unit
                }
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}