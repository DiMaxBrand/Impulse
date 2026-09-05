package eu.siacs.conversations.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class NotificationSetupActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val alwaysShowPermissionCards = intent?.getBooleanExtra(EXTRA_ALWAYS_SHOW_PERMISSION_CARDS, false) ?: false
        setContent {
            AppThemeWrapper {
                NotificationSetupScreen(
                    onDone = { finish() },
                    alwaysShowPermissionCards = alwaysShowPermissionCards,
                )
            }
        }
    }

    companion object {
        /** Set by Developer Options' trigger -- keeps the permission cards visible (with a
         * checkmark once granted) instead of hiding them, so a tester can toggle a permission
         * on/off in Settings and immediately see the card react. */
        const val EXTRA_ALWAYS_SHOW_PERMISSION_CARDS =
            "eu.siacs.conversations.EXTRA_ALWAYS_SHOW_PERMISSION_CARDS"
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun AppThemeWrapper(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val darkTheme = isSystemInDarkTheme()
        val colorScheme = dynamicOrStaticColorScheme(context, darkTheme)
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
