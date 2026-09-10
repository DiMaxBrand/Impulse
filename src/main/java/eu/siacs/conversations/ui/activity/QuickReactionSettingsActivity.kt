package eu.siacs.conversations.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.ui.QuickReactionPickerContent

/** Settings → Interface → "Quick Reactions". Reuses [QuickReactionPickerContent], the same
 * composable the double-tap-on-message trigger is expected to use later (see TODO.md) -- one
 * implementation of the picker UI, shown from two different places. */
class QuickReactionSettingsActivity : ActionBarActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appSettings = AppSettings(this)
        setContent {
            ImpulseExpressiveTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.pref_quick_reactions)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        QuickReactionPickerContent(
                            initialEmoji = appSettings.quickReactionEmoji,
                            initialRemember = appSettings.isQuickReactionRemember,
                            onSave = { emoji, remember ->
                                appSettings.setQuickReaction(emoji, remember)
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }
}
