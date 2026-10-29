package eu.siacs.conversations.ui

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** Puts the Compose contact screen into a view (the activity is still Java). */
object ContactDetailsHost {
    @JvmStatic
    fun setup(view: ComposeView, state: ContactDetailsState, actions: ContactDetailsActions) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            ImpulseExpressiveTheme {
                ContactDetailsScreen(state, actions)
            }
        }
    }
}
