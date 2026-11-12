package dev.pulsar.launcher

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dev.pulsar.launcher.db.LauncherPrefs
import dev.pulsar.launcher.db.LauncherStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME)
    ) { uri ->
        if (uri != null) exportBackup(uri)
    }

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = R.id.settings_container }
        setContentView(container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    fun requestBackup() {
        val stamp = SimpleDateFormat("yyyy-MM-dd_kk-mm", Locale.US).format(Date())
        createDocument.launch("nova-$stamp.pulsar.zip")
    }

    fun requestRestore() {
        openDocument.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    private fun exportBackup(uri: android.net.Uri) {
        val prefs = LauncherPrefs(this)
        val layout = LauncherStore(this).load()
        BackupManager.export(this, uri, layout, prefs)
            .onSuccess { toast(getString(R.string.backup_saved)) }
            .onFailure { toast(getString(R.string.backup_failed, it.message ?: it.javaClass.simpleName)) }
    }

    private fun importBackup(uri: android.net.Uri) {
        BackupManager.import(this, uri)
            .onSuccess {
                BackupManager.applyRestore(this, it)
                toast(getString(R.string.restore_done))
            }
            .onFailure { toast(getString(R.string.restore_failed, it.message ?: it.javaClass.simpleName)) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}