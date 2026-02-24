package dev.devkey.keyboard.ui.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class SettingsNav {
    MAIN, MACRO_MANAGER, COMMAND_APPS
}

class DevKeySettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var exportManager: ExportManager
    private lateinit var importManager: ImportManager
    private lateinit var macroRepository: MacroRepository
    private lateinit var commandModeRepository: CommandModeRepository

    private var pendingBackup: DevKeyBackup? by mutableStateOf(null)
    private var showConflictDialog by mutableStateOf(false)
    private var currentNav by mutableStateOf(SettingsNav.MAIN)

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

        val database = DevKeyDatabase.getInstance(this)
        exportManager = ExportManager(database)
        importManager = ImportManager(database)
        macroRepository = MacroRepository(database.macroDao())
        commandModeRepository = CommandModeRepository(database.commandAppDao())

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DevKeyTheme.keyboardBackground)
                ) {
                    when (currentNav) {
                        SettingsNav.MAIN -> {
                            SettingsScreen(
                                settingsRepository = settingsRepository,
                                versionName = versionName,
                                onNavigateToMacroManager = {
                                    currentNav = SettingsNav.MACRO_MANAGER
                                },
                                onNavigateToCommandApps = {
                                    currentNav = SettingsNav.COMMAND_APPS
                                },
                                onExport = { startExport() },
                                onImport = { startImport() }
                            )

                            if (showConflictDialog) {
                                ImportConflictDialog(
                                    onStrategy = { strategy ->
                                        showConflictDialog = false
                                        pendingBackup?.let { backup ->
                                            performImport(backup, strategy)
                                        }
                                        pendingBackup = null
                                    },
                                    onDismiss = {
                                        showConflictDialog = false
                                        pendingBackup = null
                                    }
                                )
                            }
                        }

                        SettingsNav.MACRO_MANAGER -> {
                            MacroManagerScreen(
                                macroRepository = macroRepository,
                                onBack = { currentNav = SettingsNav.MAIN }
                            )
                        }

                        SettingsNav.COMMAND_APPS -> {
                            CommandAppManagerScreen(
                                repository = commandModeRepository,
                                onBack = { currentNav = SettingsNav.MAIN }
                            )
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentNav != SettingsNav.MAIN) {
            currentNav = SettingsNav.MAIN
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::settingsRepository.isInitialized) {
            settingsRepository.close()
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

    private fun exportToUri(uri: Uri) {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        lifecycleScope.launch {
            try {
                exportManager.exportToUri(this@DevKeySettingsActivity, uri, versionName)
                Toast.makeText(this@DevKeySettingsActivity, "Data exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@DevKeySettingsActivity,
                    "Export failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: run {
                    Toast.makeText(
                        this@DevKeySettingsActivity,
                        "Could not read file",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val result = importManager.deserialize(jsonString)
                if (result.isFailure) {
                    Toast.makeText(
                        this@DevKeySettingsActivity,
                        "Invalid backup file: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                pendingBackup = result.getOrThrow()
                showConflictDialog = true
            } catch (e: Exception) {
                Toast.makeText(
                    this@DevKeySettingsActivity,
                    "Import failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performImport(backup: DevKeyBackup, strategy: ImportManager.ConflictStrategy) {
        lifecycleScope.launch {
            try {
                val result = importManager.importBackup(backup, strategy)
                Toast.makeText(
                    this@DevKeySettingsActivity,
                    "Imported: ${result.macros} macros, ${result.words} words, " +
                            "${result.commandApps} apps, ${result.clipboard} clips",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@DevKeySettingsActivity,
                    "Import failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
