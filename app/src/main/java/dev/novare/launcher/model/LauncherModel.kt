package dev.novare.launcher.model

import android.content.Context
import dev.novare.launcher.db.LauncherStore

/**
 * Owns the in-memory launcher graph and keeps it in sync with [LauncherStore].
 *
 * Each mutation performs its database writes before notifying [onChange], so an
 * observer can rebuild from the model without another load. All mutations are
 * safe to call with an item that currently lives inside a folder.
 */
class LauncherModel(context: Context) {

    private val store = LauncherStore(context.applicationContext ?: context)

    val layout: LauncherLayout = LauncherLayout()

    var onChange: ((LauncherChange) -> Unit)? = null

    fun load() {
        val loaded = store.load()
        layout.desktop.clear()
        layout.desktop.addAll(loaded.desktop)
        layout.hotseat.clear()
        layout.hotseat.addAll(loaded.hotseat)
        layout.tabs.clear()
        layout.tabs.addAll(loaded.tabs)
        layout.customIcons.clear()
        layout.customIcons.putAll(loaded.customIcons)
    }

    fun addToDesktop(item: ItemInfo, screen: Int, x: Int, y: Int) {
        val removal = detachInternal(item)
        val fromScreen = item.screen
        val fromContainer = removal?.container ?: item.container
        val affected = finishRemoval(removal)

        item.container = Container.DESKTOP
        item.screen = screen
        item.cellX = x
        item.cellY = y
        if (item.spanX < 1) item.spanX = 1
        if (item.spanY < 1) item.spanY = 1
        layout.desktop.add(item)
        affected.addAll(reindexDesktop())

        store.upsert(item, Container.DESKTOP, item.rank)
        val positions = affected.distinct()
        store.updatePositions(positions)

        if (removal != null) {
            onChange?.invoke(LauncherChange.ItemMoved(item, fromScreen, fromContainer))
        } else {
            onChange?.invoke(LauncherChange.ItemAdded(item, Container.DESKTOP, item.rank))
        }
        if (positions.isNotEmpty()) onChange?.invoke(LauncherChange.PositionsChanged(positions))
    }

    fun addToHotseat(item: ItemInfo, index: Int) = placeInHotseat(item, index)

    fun addToFolder(folder: FolderItem, item: ItemInfo) {
        if (folder === item) return
        val removal = detachInternal(item, preserveEmptyFolder = folder)
        val fromScreen = item.screen
        val fromContainer = removal?.container ?: item.container
        val affected = finishRemoval(removal)

        item.container = folder.id.toInt()
        folder.contents.add(item)
        affected.addAll(reindexFolder(folder))

        val rank = folder.contents.lastIndex
        store.upsert(item, folder.id.toInt(), rank)
        val positions = affected.distinct()
        store.updatePositions(positions)

        if (removal != null) {
            onChange?.invoke(LauncherChange.ItemMoved(item, fromScreen, fromContainer))
        } else {
            onChange?.invoke(LauncherChange.ItemAdded(item, folder.id.toInt(), rank))
        }
        if (positions.isNotEmpty()) onChange?.invoke(LauncherChange.PositionsChanged(positions))
    }

    fun moveToDesktop(item: ItemInfo, screen: Int, x: Int, y: Int) = addToDesktop(item, screen, x, y)

    fun moveToHotseat(item: ItemInfo, index: Int) = placeInHotseat(item, index)

    fun removeItem(item: ItemInfo) {
        val removal = detachInternal(item) ?: return
        val affected = finishRemoval(removal)
        val folder = item as? FolderItem
        if (folder != null) {
            for (child in folder.contents) store.deleteById(child.id)
            folder.contents.clear()
        }
        store.deleteById(item.id)
        val positions = affected.distinct()
        store.updatePositions(positions)

        onChange?.invoke(LauncherChange.ItemRemoved(item, removal.container))
        if (positions.isNotEmpty()) onChange?.invoke(LauncherChange.PositionsChanged(positions))
    }

    fun rename(item: ItemInfo, title: String) {
        if (item.title == title) return
        item.title = title
        val location = locate(item)
        store.upsert(
            item,
            location?.container ?: item.container,
            location?.index ?: item.rank
        )
        onChange?.invoke(LauncherChange.TitleChanged(item))
    }

    fun updateSpans(item: ItemInfo, spanX: Int, spanY: Int) {
        item.spanX = spanX.coerceAtLeast(1)
        item.spanY = spanY.coerceAtLeast(1)
        store.updatePositions(listOf(item))
        onChange?.invoke(LauncherChange.PositionsChanged(listOf(item)))
    }

    fun renameTab(tab: DrawerTab, name: String) {
        if (tab.name == name) return
        tab.name = name
        val index = layout.tabs.indexOfFirst { it === tab }
        store.upsertDrawerTab(tab, if (index >= 0) index else layout.tabs.size)
        onChange?.invoke(LauncherChange.TabAdded(tab))
    }

    fun addTab(tab: DrawerTab) {
        if (layout.tabs.any { it === tab }) return
        layout.tabs.add(tab)
        store.upsertDrawerTab(tab, layout.tabs.lastIndex)
        onChange?.invoke(LauncherChange.TabAdded(tab))
    }

    fun removeTab(tab: DrawerTab) {
        val index = layout.tabs.indexOfFirst { it === tab }
        if (index < 0) return
        layout.tabs.removeAt(index)
        store.deleteDrawerTab(tab.id)
        layout.tabs.forEachIndexed { order, remaining -> store.upsertDrawerTab(remaining, order) }
        onChange?.invoke(LauncherChange.TabRemoved(tab))
    }

    fun addComponentToTab(tab: DrawerTab, component: String) {
        if (!tab.components.add(component)) return
        var index = layout.tabs.indexOfFirst { it === tab }
        if (index < 0) {
            layout.tabs.add(tab)
            index = layout.tabs.lastIndex
            store.upsertDrawerTab(tab, index)
            onChange?.invoke(LauncherChange.TabAdded(tab))
            return
        }
        store.upsertDrawerTab(tab, index)
    }

    fun setCustomIcon(component: String, uri: String) {
        layout.customIcons[component] = uri
        store.upsertCustomIcon(component, uri)
        onChange?.invoke(LauncherChange.CustomIconChanged(component))
    }

    /**
     * Removes [item] from whichever list or folder currently holds it, deleting
     * a folder that this empties. Returns false when the item is not in the model.
     */
    fun detach(item: ItemInfo): Boolean {
        val removal = detachInternal(item) ?: return false
        val affected = finishRemoval(removal)
        store.deleteById(item.id)
        val positions = affected.distinct()
        store.updatePositions(positions)

        onChange?.invoke(LauncherChange.ItemRemoved(item, removal.container))
        if (positions.isNotEmpty()) onChange?.invoke(LauncherChange.PositionsChanged(positions))
        return true
    }

    /** First free anchor cell in reading order, creating a new screen when full. */
    /** Writes one already-placed item back, e.g. after a widget id was re-bound. */
    fun persistItem(item: ItemInfo) {
        val container = when {
            layout.hotseat.contains(item) -> Container.HOTSEAT
            layout.desktop.contains(item) -> Container.DESKTOP
            else -> item.container
        }
        val rank = when (container) {
            Container.HOTSEAT -> layout.hotseat.indexOf(item)
            Container.DESKTOP -> layout.desktop.indexOf(item)
            else -> item.rank
        }
        store.upsert(item, container, rank)
    }

    fun firstVacantCell(columns: Int, rows: Int): IntArray {
        val maxScreen = (layout.desktop.maxOfOrNull { it.screen } ?: 0) + 1
        for (screen in 0..maxScreen) {
            for (y in 0 until rows) {
                for (x in 0 until columns) {
                    if (isCellFree(screen, x, y)) return intArrayOf(screen, x, y)
                }
            }
        }
        return intArrayOf(maxScreen + 1, 0, 0)
    }

    /** Re-seats desktop items that no longer fit [columns] x [rows]; returns true when anything was re-seated. */
    fun normalize(columns: Int, rows: Int): Boolean {
        var changed = false
        val occupied = HashSet<Long>()
        val affected = ArrayList<ItemInfo>()
        val ordered = layout.desktop.sortedWith(
            compareBy({ it.screen }, { it.rank }, { it.cellY }, { it.cellX })
        )
        for (item in ordered) {
            val fits = item.cellX >= 0 && item.cellY >= 0 &&
                item.cellX + item.spanX <= columns && item.cellY + item.spanY <= rows &&
                keyOf(item.screen, item.cellX, item.cellY) !in occupied
            if (!fits) {
                affected.add(item)
                var placed = false
                for (screen in 0..(ordered.maxOfOrNull { it.screen } ?: 0) + 1) {
                    for (y in 0 until rows) {
                        for (x in 0 until columns) {
                            if (keyOf(screen, x, y) in occupied) continue
                            if (desktopCellTaken(screen, x, y)) continue
                            item.screen = screen
                            item.cellX = x
                            item.cellY = y
                            placed = true
                            break
                        }
                        if (placed) break
                    }
                    if (placed) break
                }
                changed = true
            }
            for (dy in 0 until item.spanY) {
                for (dx in 0 until item.spanX) {
                    occupied.add(keyOf(item.screen, item.cellX + dx, item.cellY + dy))
                }
            }
        }
        if (affected.isNotEmpty()) {
            val positions = affected.distinct()
            store.updatePositions(positions)
            onChange?.invoke(LauncherChange.PositionsChanged(positions))
        }
        return changed
    }

    // --------------------------------------------------------------- internals

    private class Location(val container: Int, val index: Int, val folder: FolderItem?)

    private class Removal(
        val container: Int,
        val folder: FolderItem?,
        val emptiedFolder: FolderItem?,
    )

    private fun placeInHotseat(item: ItemInfo, index: Int) {
        val removal = detachInternal(item)
        val fromScreen = item.screen
        val fromContainer = removal?.container ?: item.container
        val affected = finishRemoval(removal)

        item.container = Container.HOTSEAT
        val slot = index.coerceIn(0, layout.hotseat.size)
        layout.hotseat.add(slot, item)
        affected.addAll(reindexHotseat())

        store.upsert(item, Container.HOTSEAT, item.rank)
        val positions = affected.distinct()
        store.updatePositions(positions)

        if (removal != null) {
            onChange?.invoke(LauncherChange.ItemMoved(item, fromScreen, fromContainer))
        } else {
            onChange?.invoke(LauncherChange.ItemAdded(item, Container.HOTSEAT, item.rank))
        }
        if (positions.isNotEmpty()) onChange?.invoke(LauncherChange.PositionsChanged(positions))
    }

    private fun locate(item: ItemInfo): Location? {
        val desktopIndex = layout.desktop.indexOfFirst { it === item }
        if (desktopIndex >= 0) return Location(Container.DESKTOP, desktopIndex, null)
        val hotseatIndex = layout.hotseat.indexOfFirst { it === item }
        if (hotseatIndex >= 0) return Location(Container.HOTSEAT, hotseatIndex, null)
        for (folder in layout.desktop.filterIsInstance<FolderItem>()) {
            val childIndex = folder.contents.indexOfFirst { it === item }
            if (childIndex >= 0) return Location(folder.id.toInt(), childIndex, folder)
        }
        return null
    }

    private fun detachInternal(
        item: ItemInfo,
        preserveEmptyFolder: FolderItem? = null,
    ): Removal? {
        val location = locate(item) ?: return null
        when {
            location.container == Container.DESKTOP -> layout.desktop.removeAt(location.index)
            location.container == Container.HOTSEAT -> layout.hotseat.removeAt(location.index)
            else -> location.folder?.contents?.removeAt(location.index)
        }
        val folder = location.folder
        val emptied = if (folder != null && folder.contents.isEmpty() && folder !== preserveEmptyFolder) {
            layout.desktop.remove(folder)
            folder
        } else {
            null
        }
        return Removal(location.container, folder, emptied)
    }

    private fun finishRemoval(removal: Removal?): ArrayList<ItemInfo> {
        val affected = ArrayList<ItemInfo>()
        if (removal == null) return affected
        removal.emptiedFolder?.let { if (it.id > 0L) store.deleteById(it.id) }
        when (removal.container) {
            Container.DESKTOP -> if (removal.emptiedFolder == null) affected.addAll(reindexDesktop())
            Container.HOTSEAT -> affected.addAll(reindexHotseat())
            else -> removal.folder?.let { if (it.contents.isNotEmpty()) affected.addAll(reindexFolder(it)) }
        }
        if (removal.emptiedFolder != null) affected.addAll(reindexDesktop())
        return affected
    }

    private fun reindexDesktop(): ArrayList<ItemInfo> {
        val changed = ArrayList<ItemInfo>()
        layout.desktop.forEachIndexed { index, item ->
            if (item.rank != index) {
                item.rank = index
                changed.add(item)
            }
        }
        return changed
    }

    private fun reindexHotseat(): ArrayList<ItemInfo> {
        val changed = ArrayList<ItemInfo>()
        layout.hotseat.forEachIndexed { index, item ->
            if (item.rank != index || item.screen != 0 || item.cellX != index || item.cellY != 0) {
                item.rank = index
                item.screen = 0
                item.cellX = index
                item.cellY = 0
                changed.add(item)
            }
        }
        return changed
    }

    private fun reindexFolder(folder: FolderItem): ArrayList<ItemInfo> {
        val changed = ArrayList<ItemInfo>()
        folder.contents.forEachIndexed { index, child ->
            if (child.rank != index) {
                child.rank = index
                changed.add(child)
            }
        }
        return changed
    }

    private fun isCellFree(screen: Int, x: Int, y: Int): Boolean = layout.desktop.none { item ->
        item.screen == screen &&
            x >= item.cellX && x < item.cellX + item.spanX &&
            y >= item.cellY && y < item.cellY + item.spanY
    }

    private fun desktopCellTaken(screen: Int, x: Int, y: Int): Boolean = layout.desktop.any { item ->
        item.screen == screen &&
            x >= item.cellX && x < item.cellX + item.spanX &&
            y >= item.cellY && y < item.cellY + item.spanY
    }

    private fun keyOf(screen: Int, x: Int, y: Int): Long =
        (screen.toLong() shl 40) or (x.toLong() shl 20) or y.toLong()
}
