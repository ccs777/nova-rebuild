package dev.pulsar.launcher

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import dev.pulsar.launcher.app.IconPackManager
import dev.pulsar.launcher.db.LauncherPrefs
import dev.pulsar.launcher.db.LauncherStore

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "nova"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("backup_create")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.requestBackup()
            true
        }
        findPreference<Preference>("backup_restore")?.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.requestRestore()
            true
        }
        findPreference<Preference>("reset")?.setOnPreferenceClickListener {
            confirmReset()
            true
        }
        findPreference<Preference>("hidden_apps")?.setOnPreferenceClickListener {
            showHiddenApps()
            true
        }
        installIconPackPreference()
    }

    private fun installIconPackPreference() {
        val category = findPreference<PreferenceCategory>("cat_drawer") ?: return
        val packs = IconPackManager(requireContext()).packs()
        val values = arrayOf("") + packs.map { it.pkg }
        val labels = arrayOf(getString(R.string.none)) + packs.map { it.label }
        val preference = ListPreference(requireContext()).apply {
            key = "icon_pack"
            title = getString(R.string.pref_icon_pack)
            entries = labels
            entryValues = values
            setDefaultValue("")
            value = LauncherPrefs(requireContext()).iconPackPackage ?: ""
            summary = "%s"
        }
        preference.order = 100
        category.addPreference(preference)
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val context = requireContext()
                LauncherStore(context).clear()
                val prefs = LauncherPrefs(context)
                prefs.clear()
                prefs.bumpLayoutRevision()
                Toast.makeText(context, R.string.reset_done, Toast.LENGTH_LONG).show()
                activity?.recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHiddenApps() {
        val context = requireContext()
        val prefs = LauncherPrefs(context)
        val hidden = prefs.hiddenApps.toList()
        if (hidden.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.drawer_hidden) + ": 0", Toast.LENGTH_SHORT).show()
            return
        }
        val pm = context.packageManager
        val labels = hidden.map { key ->
            val pkg = key.substringBefore('/')
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }.toTypedArray()
        val checked = BooleanArray(hidden.size) { true }
        AlertDialog.Builder(context)
            .setTitle(R.string.drawer_hidden)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val stillHidden = hidden.filterIndexed { index, _ -> checked[index] }.toSet()
                prefs.hiddenApps = stillHidden
                activity?.recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}