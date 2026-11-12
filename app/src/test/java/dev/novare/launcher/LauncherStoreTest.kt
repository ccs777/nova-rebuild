package dev.novare.launcher

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.novare.launcher.db.LauncherStore
import dev.novare.launcher.model.AppItem
import dev.novare.launcher.model.Container
import dev.novare.launcher.model.DrawerTab
import dev.novare.launcher.model.FolderItem
import dev.novare.launcher.model.ItemInfo
import dev.novare.launcher.model.ItemType
import dev.novare.launcher.model.LauncherLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherStoreTest {

    private lateinit var store: LauncherStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = LauncherStore(context)
        store.clear()
    }

    @Test
    fun saveThenLoadRoundTripsLayout() {
        store.save(sampleLayout())

        val loaded = store.load()

        assertEquals(2, loaded.desktop.size)
        val app = loaded.desktop.filterIsInstance<AppItem>().single()
        assertEquals(component("browser", "BrowserActivity"), app.component)
        assertEquals("Browser", app.title)
        assertEquals(0, app.screen)
        assertEquals(2, app.cellX)
        assertEquals(1, app.cellY)
        assertEquals(Container.DESKTOP, app.container)
        assertTrue(app.id > 0L)

        val folder = loaded.desktop.filterIsInstance<FolderItem>().single()
        assertEquals("Tools", folder.title)
        assertEquals(1, folder.screen)
        assertEquals(0, folder.cellX)
        assertEquals(3, folder.cellY)
        assertEquals(Container.DESKTOP, folder.container)
        assertEquals(2, folder.contents.size)
        assertEquals(listOf("Calculator", "Notes"), folder.contents.map { it.title })
        assertEquals(listOf(0, 1), folder.contents.map { it.rank })
        // Children are stored with the folder row id as their container.
        assertTrue(folder.contents.all { it.container == folder.id.toInt() })
        assertEquals(2, folder.contents.map { it.id }.toSet().size)

        assertEquals(4, loaded.hotseat.size)
        assertEquals(listOf("Phone", "Messages", "Camera", "Mail"), loaded.hotseat.map { it.title })
        assertEquals(listOf(0, 1, 2, 3), loaded.hotseat.map { it.rank })
        assertTrue(loaded.hotseat.all { it.container == Container.HOTSEAT })
        assertEquals(
            listOf(component("phone", "PhoneActivity"), component("messages", "MessagesActivity")),
            loaded.hotseat.take(2).map { (it as AppItem).component },
        )

        assertEquals(1, loaded.tabs.size)
        val tab = loaded.tabs.single()
        assertEquals("Media", tab.name)
        assertEquals(setOf(browserKey(), toolsKey()), tab.components)

        assertEquals(mapOf(browserKey() to "content://dev.novare.icons/browser"), loaded.customIcons)
    }

    @Test
    fun widgetRowRoundTripsProviderAndSpans() {
        val layout = LauncherLayout()
        val widget = ItemInfo(itemType = ItemType.APPWIDGET)
        widget.title = "Chrome search"
        widget.appWidgetId = 42
        widget.appWidgetProvider = ComponentName("com.android.chrome", "org.chromium.chrome.browser.searchwidget.SearchWidgetProvider")
        widget.spanX = 3
        widget.spanY = 2
        widget.container = Container.DESKTOP
        layout.desktop.add(widget)

        store.save(layout)
        val loaded = store.load().desktop.single()

        assertEquals(ItemType.APPWIDGET, loaded.itemType)
        assertEquals(42, loaded.appWidgetId)
        assertEquals(widget.appWidgetProvider, loaded.appWidgetProvider)
        assertEquals(3, loaded.spanX)
        assertEquals(2, loaded.spanY)
        assertTrue(loaded.id > 0L)
    }

    @Test
    fun clearEmptiesEveryTable() {
        store.save(sampleLayout())

        store.clear()

        val loaded = store.load()
        assertTrue(loaded.desktop.isEmpty())
        assertTrue(loaded.hotseat.isEmpty())
        assertTrue(loaded.tabs.isEmpty())
        assertTrue(loaded.customIcons.isEmpty())
    }

    private fun sampleLayout(): LauncherLayout {
        val layout = LauncherLayout()

        val browser = AppItem(component("browser", "BrowserActivity"))
        browser.title = "Browser"
        browser.screen = 0
        browser.cellX = 2
        browser.cellY = 1
        layout.desktop.add(browser)

        val folder = FolderItem()
        folder.title = "Tools"
        folder.screen = 1
        folder.cellX = 0
        folder.cellY = 3
        folder.contents.add(componentApp("tools", "CalculatorActivity", "Calculator", 0))
        folder.contents.add(componentApp("tools", "NotesActivity", "Notes", 0))
        layout.desktop.add(folder)

        listOf(
            componentApp("phone", "PhoneActivity", "Phone", 0),
            componentApp("messages", "MessagesActivity", "Messages", 1),
            componentApp("camera", "CameraActivity", "Camera", 2),
            componentApp("mail", "MailActivity", "Mail", 3),
        ).forEach { layout.hotseat.add(it) }

        layout.tabs.add(
            DrawerTab(
                name = "Media",
                components = linkedSetOf(browserKey(), toolsKey()),
            )
        )
        layout.customIcons[browserKey()] = "content://dev.novare.icons/browser"
        return layout
    }

    private fun componentApp(pkg: String, cls: String, title: String, cellX: Int): AppItem =
        AppItem(component(pkg, cls)).apply {
            this.title = title
            this.cellX = cellX
        }

    private fun component(pkg: String, cls: String): ComponentName =
        ComponentName("com.example.$pkg", "com.example.$pkg.$cls")

    private fun browserKey(): String = component("browser", "BrowserActivity").flattenToString()

    private fun toolsKey(): String = component("tools", "CalculatorActivity").flattenToString()
}
