package dev.devkey.keyboard

abstract class WordAlternatives {
    protected var mChosenWord: CharSequence? = null

    constructor()

    constructor(chosenWord: CharSequence?) {
        mChosenWord = chosenWord
    }

    override fun hashCode(): Int = mChosenWord.hashCode()

    abstract fun getOriginalWord(): CharSequence?

    fun getChosenWord(): CharSequence? = mChosenWord

    abstract fun getAlternatives(): List<CharSequence>?
}
