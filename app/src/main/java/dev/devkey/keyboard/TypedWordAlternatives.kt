package dev.devkey.keyboard

class TypedWordAlternatives : WordAlternatives {
    internal var word: WordComposer? = null
    private var suggestionsProvider: ((WordComposer) -> List<CharSequence>)? = null

    constructor()

    constructor(
        chosenWord: CharSequence?,
        wordComposer: WordComposer?,
        suggestionsProvider: (WordComposer) -> List<CharSequence>
    ) : super(chosenWord) {
        word = wordComposer
        this.suggestionsProvider = suggestionsProvider
    }

    override fun getOriginalWord(): CharSequence? = word?.getTypedWord()

    override fun getAlternatives(): List<CharSequence>? = word?.let { suggestionsProvider?.invoke(it) }
}
