package dev.novare.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.novare.launcher.db.LauncherStore
import dev.novare.launcher.model.AppItem
import dev.novare.launcher.model.FolderItem
import dev.novare.launcher.model.LauncherLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ItemMappingTest {

    private lateinit var store: LauncherStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = LauncherStore(context)
        store.clear()
    }

    @Test
    fun appItemWithIntentKeepsItsComponentAcrossSaveAndLoad() {
        val component = ComponentName("com.example.mail", "com.example.mail.MailActivity")
        val layout = LauncherLayout()
        val app = AppItem(component)
        app.title = "Mail"
        app.intent = Intent(Intent.ACTION_VIEW).apply { setComponent(component) }
        layout.desktop.add(app)

        store.save(layout)

        val loaded = store.load().desktop.single()
        assertTrue(loaded is AppItem)
        val loadedApp = loaded as AppItem
        assertEquals(component, loadedApp.component)
        assertEquals(component, loadedApp.intent?.component)
        assertEquals("Mail", loadedApp.title)
    }

    @Test
    fun folderWithoutChildrenLoadsBackAsFolder() {
        val layout = LauncherLayout()
        val folder = FolderItem()
        folder.title = "Empty"
        folder.screen = 0
        folder.cellX = 2
        folder.cellY = 3
        layout.desktop.add(folder)

        store.save(layout)

        val loaded = store.load().desktop.single()
        assertTrue(loaded is FolderItem)
        val loadedFolder = loaded as FolderItem
        assertEquals("Empty", loadedFolder.title)
        assertEquals(2, loadedFolder.cellX)
        assertEquals(3, loadedFolder.cellY)
        assertTrue(loadedFolder.contents.isEmpty())
    }
}
