package dev.novare.launcher.model

/** Incremental description of what a [LauncherModel] mutation changed. */
sealed class LauncherChange {

    data class ItemAdded(val item: ItemInfo, val container: Int, val rank: Int) : LauncherChange()

    data class ItemRemoved(val item: ItemInfo, val container: Int) : LauncherChange()

    data class ItemMoved(val item: ItemInfo, val fromScreen: Int, val fromContainer: Int) : LauncherChange()

    data class PositionsChanged(val items: List<ItemInfo>) : LauncherChange()

    data class TitleChanged(val item: ItemInfo) : LauncherChange()

    data class TabAdded(val tab: DrawerTab) : LauncherChange()

    data class TabRemoved(val tab: DrawerTab) : LauncherChange()

    data class CustomIconChanged(val component: String) : LauncherChange()
}
