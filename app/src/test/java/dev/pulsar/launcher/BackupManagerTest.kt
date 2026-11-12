package dev.pulsar.launcher

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.pulsar.launcher.db.LauncherPrefs
import dev.pulsar.launcher.db.LauncherStore
import dev.pulsar.launcher.model.AppItem
import dev.pulsar.launcher.model.Container
import dev.pulsar.launcher.model.DrawerTab
import dev.pulsar.launcher.model.FolderItem
import dev.pulsar.launcher.model.LauncherLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: LauncherPrefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LauncherStore(context).clear()
        prefs = LauncherPrefs(context)
        prefs.clear()
    }

    @Test
    fun exportThenImportRestoresLayoutAndPreferences() {
        prefs.gridColumns = 6
        prefs.hiddenApps = setOf(browserKey(), "com.example.tools/com.example.tools.NotesActivity")
        val layout = sampleLayout()

        val file = File.createTempFile("nova-backup", ".zip")
        val uri = Uri.fromFile(file)
        val exported = BackupManager.export(context, uri, layout, prefs)
        assertTrue(exported.isSuccess)

        val imported = BackupManager.import(context, uri)
        assertTrue(imported.isSuccess)
        val restored = imported.getOrThrow()

        val desktop = restored.layout.desktop
        assertEquals(2, desktop.size)
        val app = desktop.filterIsInstance<AppItem>().single()
        assertEquals(component("browser", "BrowserActivity"), app.component)
        assertEquals("Browser", app.title)
        assertEquals(2, app.cellX)
        assertEquals(1, app.cellY)

        val folder = desktop.filterIsInstance<FolderItem>().single()
        assertEquals("Tools", folder.title)
        assertEquals(1, folder.screen)
        assertEquals(0, folder.cellX)
        assertEquals(3, folder.cellY)
        assertEquals(listOf("Calculator", "Notes"), folder.contents.map { it.title })
        assertEquals(
            listOf(
                component("tools", "CalculatorActivity"),
                component("tools", "NotesActivity"),
            ),
            folder.contents.map { (it as AppItem).component },
        )

        assertEquals(4, restored.layout.hotseat.size)
        assertEquals(listOf("Phone", "Messages", "Camera", "Mail"), restored.layout.hotseat.map { it.title })
        assertEquals(listOf(0, 1, 2, 3), restored.layout.hotseat.map { it.cellX })
        assertTrue(restored.layout.hotseat.all { it.container == Container.HOTSEAT })

        assertEquals(1, restored.layout.tabs.size)
        val tab = restored.layout.tabs.single()
        assertEquals("Media", tab.name)
        assertEquals(setOf(browserKey(), toolsKey()), tab.components)

        assertEquals(mapOf(browserKey() to "content://dev.pulsar.icons/browser"), restored.layout.customIcons)

        assertEquals(prefs.snapshot(), restored.preferences)
        assertEquals("6", restored.preferences["workspace_columns"])
        assertEquals(prefs.hiddenApps, restored.preferences["hidden_apps"])
    }

    @Test
    fun importRejectsZipWithForeignMeta() {
        val file = File.createTempFile("not-a-backup", ".zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("meta.json"))
            zip.write("""{"format":"other-launcher","version":1}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("favorites.json"))
            zip.write("""{"desktop":[]}""".toByteArray())
            zip.closeEntry()
        }

        val imported = BackupManager.import(context, Uri.fromFile(file))
        assertTrue(imported.isFailure)
        assertFalse(imported.isSuccess)
    }

    private fun sampleLayout(): LauncherLayout {
        val layout = LauncherLayout()

        val browser = AppItem(component("browser", "BrowserActivity"))
        browser.title = "Browser"
        browser.cellX = 2
        browser.cellY = 1
        layout.desktop.add(browser)

        val folder = FolderItem()
        folder.title = "Tools"
        folder.screen = 1
        folder.cellX = 0
        folder.cellY = 3
        folder.contents.add(AppItem(component("tools", "CalculatorActivity")).apply { title = "Calculator" })
        folder.contents.add(AppItem(component("tools", "NotesActivity")).apply { title = "Notes" })
        layout.desktop.add(folder)

        listOf(
            "Phone" to component("phone", "PhoneActivity"),
            "Messages" to component("messages", "MessagesActivity"),
            "Camera" to component("camera", "CameraActivity"),
            "Mail" to component("mail", "MailActivity"),
        ).forEach { (title, component) ->
            layout.hotseat.add(AppItem(component).apply { this.title = title })
        }

        layout.tabs.add(
            DrawerTab(
                name = "Media",
                components = linkedSetOf(browserKey(), toolsKey()),
            )
        )
        layout.customIcons[browserKey()] = "content://dev.pulsar.icons/browser"
        return layout
    }

    private fun component(pkg: String, cls: String): ComponentName =
        ComponentName("com.example.$pkg", "com.example.$pkg.$cls")

    private fun browserKey(): String = component("browser", "BrowserActivity").flattenToString()

    private fun toolsKey(): String = component("tools", "CalculatorActivity").flattenToString()
}
