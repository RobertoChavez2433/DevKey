package dev.devkey.keyboard.core

import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Vibrator
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.*
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.dictionary.loader.PluginManager
import dev.devkey.keyboard.language.LanguageSwitcher
import dev.devkey.keyboard.compose.ComposeSequenceLoader
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.clipboard.DevKeyClipboardManager
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Thin shell that orchestrates all LatinIME.onCreate() initialization.
 * Heavy lifting delegated to [ImeStateLoader] and [ImeCollaboratorWiring].
 */
internal class ImeInitializer(
    private val ime: LatinIME,
    private val state: ImeState,
    private val col: ImeCollaborators,
    private val settingsRepository: SettingsRepository
) {

    fun initialize(): String {
        val context: Context = ime
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val res = context.resources

        val feedbackManager = FeedbackManager(
            context, context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator, settingsRepository
        )
        KeyboardSwitcher.init(ime)
        ComposeSequenceLoader.load(context)

        // Clipboard + command mode
        val clipboardRepo = ClipboardRepository(DevKeyDatabase.getInstance(context).clipboardHistoryDao())
        val clipboardManager = DevKeyClipboardManager(context, clipboardRepo).also { it.startListening() }
        ime.clipboardManager = clipboardManager
        val cmdDetector = CommandModeDetector(CommandModeRepository(DevKeyDatabase.getInstance(context).commandAppDao()))
        SessionDependencies.commandModeDetector = cmdDetector
        ime.commandModeDetector = cmdDetector

        // Language + keyboard switcher
        val conf = res.configuration
        state.mOrientation = conf.orientation
        val languageSwitcher = LanguageSwitcher(ime).also { it.loadLocales(prefs) }
        state.mLanguageSwitcher = languageSwitcher
        state.mKeyboardSwitcher = KeyboardSwitcher.getInstance().also { it.setLanguageSwitcher(languageSwitcher) }
        val systemLocale = conf.locales[0]
        languageSwitcher.setSystemLocale(systemLocale)
        ime.mSystemLocale = systemLocale.toString()
        val inputLanguage = languageSwitcher.getInputLanguage() ?: systemLocale.toString()

        // State + collaborators via loader
        val loader = ImeStateLoader(ime, state, col, settingsRepository)
        loader.loadPrefs(prefs, res)
        col.feedbackManager = feedbackManager
        col.swipeHandler = loader.createSwipeHandler(prefs, res)
        col.keyEventSender = loader.createKeyEventSender(context, res)
        col.puncHeuristics = loader.createPuncHeuristics()
        state.updateKeyboardOptions(settingsRepository)

        // Plugins + receivers
        PluginManager.getPluginDictionaries(context.applicationContext)
        val pluginManager = PluginManager(ime)
        ime.mPluginManager = pluginManager
        val pFilter = IntentFilter().apply { addDataScheme("package"); addAction("android.intent.action.PACKAGE_ADDED"); addAction("android.intent.action.PACKAGE_REPLACED"); addAction("android.intent.action.PACKAGE_REMOVED") }
        ime.registerReceiver(pluginManager, pFilter, Context.RECEIVER_NOT_EXPORTED)
        if (KeyMapGenerator.isDebugBuild(context)) ime.registerReceiver(pluginManager, IntentFilter("dev.devkey.keyboard.RESCAN_PLUGINS"), Context.RECEIVER_EXPORTED)
        ime.registerReceiver(feedbackManager.ringerModeReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION), Context.RECEIVER_NOT_EXPORTED)
        col.notificationController = NotificationController(context, { ime.requestShowSelf(InputMethodManager.SHOW_FORCED) })
        col.notificationController.setNotification(state.mKeyboardNotification)

        if (KeyMapGenerator.isDebugBuild(context)) { ime.mDebugReceivers = dev.devkey.keyboard.debug.DebugReceiverManager(context).also { it.registerAll() } }

        return inputLanguage
    }

    fun initPhase2() = ImeCollaboratorWiring(ime, state, col, settingsRepository).wire()
}
