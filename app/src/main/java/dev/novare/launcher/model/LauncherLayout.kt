package dev.novare.launcher.model

class DrawerTab(
    var id: Long = 0L,
    var name: String = "",
    val components: MutableSet<String> = LinkedHashSet(),
)

class LauncherLayout(
    val desktop: MutableList<ItemInfo> = ArrayList(),
    val hotseat: MutableList<ItemInfo> = ArrayList(),
    val tabs: MutableList<DrawerTab> = ArrayList(),
    val customIcons: MutableMap<String, String> = LinkedHashMap(),
)