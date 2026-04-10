package dev.devkey.keyboard.ui.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.export.DevKeyBackup
import dev.devkey.keyboard.data.export.ExportManager
import dev.devkey.keyboard.data.export.ImportManager
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DevKeySettingsActivity : ComponentActivity() {

    private lateinit var database: DevKeyDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var exportManager: ExportManager
    private lateinit var importManager: ImportManager
    private lateinit var macroRepository: MacroRepository
    private lateinit var commandModeRepository: CommandModeRepository

    private var pendingBackup: DevKeyBackup? by mutableStateOf(null)
    private var showConflictDialog by mutableStateOf(false)
    private var currentNav by mutableStateOf(SettingsNav.MAIN)
    private var versionName: String = "unknown"

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportToUri(it) }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        settingsRepository = SettingsRepository(prefs)

        database = DevKeyDatabase.getInstance(this)
        exportManager = ExportManager(database)
        importManager = ImportManager(database)
        macroRepository = MacroRepository(database.macroDao())
        commandModeRepository = CommandModeRepository(database.commandAppDao())

        versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DevKeyThemeColors.kbBg)
                ) {
                    SettingsNavGraph(
                        currentNav = currentNav,
                        onNavigate = { currentNav = it },
                        settingsRepository = settingsRepository,
                        macroRepository = macroRepository,
                        commandModeRepository = commandModeRepository,
                        database = database,
                        versionName = versionName,
                        showConflictDialog = showConflictDialog,
                        pendingBackup = pendingBackup,
                        onStartExport = { startExport() },
                        onStartImport = { startImport() },
                        onConflictStrategy = { strategy ->
                            showConflictDialog = false
                            pendingBackup?.let { backup -> performImport(backup, strategy) }
                            pendingBackup = null
                        },
                        onConflictDismiss = {
                            showConflictDialog = false
                            pendingBackup = null
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::settingsRepository.isInitialized) {
            settingsRepository.close()
        }
    }

    private fun navigateBack() {
        when (currentNav) {
            SettingsNav.MAIN -> finish()
            SettingsNav.MACRO_MANAGER -> currentNav = SettingsNav.MACROS
            SettingsNav.COMMAND_APPS -> currentNav = SettingsNav.COMMAND_MODE
            SettingsNav.CUSTOM_DICTIONARY -> currentNav = SettingsNav.PREDICTION
            else -> currentNav = SettingsNav.MAIN
        }
    }

    private fun startExport() {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val filename = "devkey-backup-$date.json"
        createDocumentLauncher.launch(filename)
    }

    private fun startImport() {
        openDocumentLauncher.launch(arrayOf("application/json"))
    }

    private fun toast(msg: String, long: Boolean = false) {
        val len = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(this, msg, len).show()
    }

    private fun exportToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                exportManager.exportToUri(this@DevKeySettingsActivity, uri, versionName)
                toast("Data exported")
            } catch (e: Exception) {
                toast("Export failed: ${e.message}", long = true)
            }
        }
    }

    private fun importFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: run {
                    toast("Could not read file")
                    return@launch
                }

                val result = importManager.deserialize(jsonString)
                if (result.isFailure) {
                    toast("Invalid backup file: ${result.exceptionOrNull()?.message}", long = true)
                    return@launch
                }

                pendingBackup = result.getOrThrow()
                showConflictDialog = true
            } catch (e: Exception) {
                toast("Import failed: ${e.message}", long = true)
            }
        }
    }

    private fun performImport(backup: DevKeyBackup, strategy: ImportManager.ConflictStrategy) {
        lifecycleScope.launch {
            try {
                val result = importManager.importBackup(backup, strategy)
                toast(
                    "Imported: ${result.macros} macros, ${result.words} words, " +
                        "${result.commandApps} apps, ${result.clipboard} clips",
                    long = true
                )
            } catch (e: Exception) {
                toast("Import failed: ${e.message}", long = true)
            }
        }
    }
}
