package dev.devkey.keyboard.core

import dev.devkey.keyboard.ASCII_SPACE
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Phase 2 collaborator wiring: creates collaborators that cross-reference
 * each other and assigns them on [col].
 *
 * Split from [ImeInitializer] to keep both files under the 200-line limit.
 * Must be called after Phase 1 fields (feedbackManager, keyEventSender, etc.)
 * are assigned on [col].
 */
internal class ImeCollaboratorWiring(
    private val ime: LatinIME,
    private val state: ImeState,
    private val col: ImeCollaborators,
    private val settingsRepository: SettingsRepository
) {

    fun wire() {
        col.suggestionCoordinator = SuggestionCoordinator(state, ime, ime)
        col.modifierHandler = ModifierHandler(state, ime, col.feedbackManager, col.keyEventSender, settingsRepository)
        col.suggestionPicker = SuggestionPicker(
            state, col.suggestionCoordinator, ime, ime,
            updateShiftKeyState = { attr -> col.modifierHandler.updateShiftKeyState(attr) },
            sendSpace = {
                col.keyEventSender.sendModifiableKeyChar(ASCII_SPACE.toChar())
                col.modifierHandler.updateShiftKeyState(ime.currentInputEditorInfo)
            },
            onKey = { code, codes, x, y -> ime.onKey(code, codes, x, y) },
            getShiftState = { col.modifierHandler.getShiftState() }
        )
        val inputHandlers = InputHandlers(
            state, ime, col.suggestionCoordinator, col.suggestionPicker,
            col.modifierHandler, col.keyEventSender, col.puncHeuristics
        )
        val optionsMenuHandler = OptionsMenuHandler(
            context = ime, resources = ime.resources,
            handleClose = { ime.handleClose() }, getImeWindow = { ime.window?.window }
        )
        val inputDispatcher = InputDispatcher(
            state, inputHandlers, ime, ime, col.keyEventSender,
            handleClose = { ime.handleClose() },
            isShowingOptionDialog = { optionsMenuHandler.isShowingOptionDialog() },
            onOptionKeyPressed = { optionsMenuHandler.onOptionKeyPressed() },
            onOptionKeyLongPressed = { optionsMenuHandler.onOptionKeyLongPressed() },
            toggleLanguage = { reset, next -> col.dictionaryManager.toggleLanguage(reset, next) },
            updateShiftKeyState = { attr -> col.modifierHandler.updateShiftKeyState(attr) },
            commitTyped = { ic, manual -> inputHandlers.commitTyped(ic, manual) },
            maybeRemovePreviousPeriod = { text -> col.puncHeuristics.maybeRemovePreviousPeriod(text) },
            abortCorrection = { force -> col.suggestionCoordinator.abortCorrection(force) },
            getShiftState = { col.modifierHandler.getShiftState() }
        )
        col.preferenceObserver = PreferenceObserver(
            state, ime, ime, col.feedbackManager, col.swipeHandler, col.puncHeuristics,
            col.notificationController, settingsRepository,
            updateKeyboardOptions = { state.updateKeyboardOptions(settingsRepository) },
            toggleLanguage = { col.dictionaryManager.toggleLanguage(reset = true, next = true) },
            setNextSuggestions = { col.suggestionCoordinator.setNextSuggestions() },
            isPredictionOn = { state.isPredictionOn(col.suggestionCoordinator.isPredictionWanted()) }
        )
        val inputViewSetup = InputViewSetup(
            state, ime, ime, col.suggestionCoordinator, col.preferenceObserver, settingsRepository, ime,
            resetPrediction = { state.resetPrediction() },
            loadSettings = { col.preferenceObserver.loadSettings() },
            updateShiftKeyState = { attr -> col.modifierHandler.updateShiftKeyState(attr) },
            isPredictionOn = { state.isPredictionOn(col.suggestionCoordinator.isPredictionWanted()) },
            toggleLanguage = { reset, next -> col.dictionaryManager.toggleLanguage(reset, next) }
        )
        inputViewSetup.commandModeDetector = ime.commandModeDetector
        col.dictionaryManager = DictionaryManager(
            state, ime, ime, ime, col.preferenceObserver, col.puncHeuristics, ime,
            reloadKeyboards = { state.reloadKeyboards(settingsRepository) },
            updateShiftKeyState = { attr -> col.modifierHandler.updateShiftKeyState(attr) },
            isPredictionOn = { state.isPredictionOn(col.suggestionCoordinator.isPredictionWanted()) }
        )

        // Compose sequence buffers
        state.mComposeBuffer = dev.devkey.keyboard.compose.ComposeSequence(
            onComposedText = { text -> ime.onText(text) },
            updateShiftState = { attr -> col.modifierHandler.updateShiftKeyState(attr) },
            editorInfoProvider = { ime.currentInputEditorInfo }
        )
        state.mDeadAccentBuffer = dev.devkey.keyboard.compose.DeadAccentSequence(
            onComposedText = { text -> ime.onText(text) },
            updateShiftState = { attr -> col.modifierHandler.updateShiftKeyState(attr) },
            editorInfoProvider = { ime.currentInputEditorInfo }
        )

        SessionDependencies.resetPredictionState = { state.resetPrediction() }

        col.inputHandlers = inputHandlers
        col.inputDispatcher = inputDispatcher
        col.inputViewSetup = inputViewSetup
        col.optionsMenuHandler = optionsMenuHandler
    }
}
