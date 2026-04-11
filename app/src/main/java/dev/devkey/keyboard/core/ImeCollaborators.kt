package dev.devkey.keyboard.core

/**
 * Holds all collaborator references for the IME.
 *
 * Populated by [ImeInitializer] / [ImeCollaboratorWiring] during onCreate,
 * then accessed by LatinIME one-liner delegates and by [ImeLifecycleDelegate].
 * Using a holder keeps LatinIME's field count low.
 */
internal class ImeCollaborators {
    lateinit var feedbackManager: FeedbackManager
    lateinit var swipeHandler: SwipeActionHandler
    lateinit var puncHeuristics: PunctuationHeuristics
    lateinit var keyEventSender: KeyEventSender
    lateinit var preferenceObserver: PreferenceObserver
    lateinit var suggestionCoordinator: SuggestionCoordinator
    lateinit var suggestionPicker: SuggestionPicker
    lateinit var inputDispatcher: InputDispatcher
    lateinit var inputHandlers: InputHandlers
    lateinit var modifierHandler: ModifierHandler
    lateinit var inputViewSetup: InputViewSetup
    lateinit var dictionaryManager: DictionaryManager
    lateinit var notificationController: NotificationController
    lateinit var optionsMenuHandler: OptionsMenuHandler
    lateinit var candidateViewManager: CandidateViewManager
}
