package dev.devkey.keyboard.ui.keyboard

import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.devkey.keyboard.core.KeyboardActionListener

/**
 * Factory that creates a ComposeView properly configured for use inside an InputMethodService.
 *
 * InputMethodService doesn't implement LifecycleOwner, SavedStateRegistryOwner, or
 * ViewModelStoreOwner, so we need to provide these manually for ComposeView to work.
 */
object ComposeKeyboardViewFactory {

    private var lifecycleOwner: IMELifecycleOwner? = null

    /**
     * Install lifecycle/savedstate/viewmodel owners on a view (typically the IME decor view).
     * On API 36+, InputMethodService requires ViewTreeLifecycleOwner on the window decor.
     */
    @JvmStatic
    fun installLifecycleOwner(view: View) {
        val owner = createFreshOwner()
        lifecycleOwner = owner

        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
    }

    /**
     * Create a ComposeView that renders the DevKeyKeyboard composable.
     *
     * @param context The InputMethodService context.
     * @param actionListener The keyboard action listener (typically the LatinIME itself).
     * @return A View containing the Compose keyboard UI.
     */
    @JvmStatic
    fun create(
        context: Context,
        actionListener: KeyboardActionListener
    ): View {
        // Always create a fresh lifecycle owner to prevent stale Compose state
        // across onCreateInputView() recreations (fixes BUG-02: 123 button)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        lifecycleOwner?.viewModelStore?.clear()
        val owner = createFreshOwner()
        lifecycleOwner = owner

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                DevKeyKeyboard(actionListener)
            }
        }

        return composeView
    }

    /**
     * Creates a new [IMELifecycleOwner] fully initialized through ON_RESUME.
     */
    private fun createFreshOwner(): IMELifecycleOwner {
        val owner = IMELifecycleOwner()
        owner.performRestore(null)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return owner
    }

    /**
     * Minimal lifecycle/savedstate/viewmodel owner for hosting Compose inside an IME.
     */
    private class IMELifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun performRestore(savedState: android.os.Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }
}
