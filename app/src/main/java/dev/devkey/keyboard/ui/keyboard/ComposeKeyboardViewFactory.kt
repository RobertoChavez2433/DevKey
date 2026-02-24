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
import dev.devkey.keyboard.LatinKeyboardBaseView

/**
 * Factory that creates a ComposeView properly configured for use inside an InputMethodService.
 *
 * InputMethodService doesn't implement LifecycleOwner, SavedStateRegistryOwner, or
 * ViewModelStoreOwner, so we need to provide these manually for ComposeView to work.
 */
object ComposeKeyboardViewFactory {

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
        actionListener: LatinKeyboardBaseView.OnKeyboardActionListener
    ): View {
        val lifecycleOwner = IMELifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                DevKeyKeyboard(actionListener)
            }
        }

        return composeView
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
