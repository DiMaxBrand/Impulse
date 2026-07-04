package eu.siacs.conversations.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ExpressiveGroupRow
import eu.siacs.conversations.ui.GroupPosition
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.update.UpdatePreferences

class DeveloperOptionsActivity : ActionBarActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ImpulseExpressiveTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.developer_options_title)) },
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
                    Box(modifier = Modifier.padding(padding).padding(16.dp)) {
                        ExpressiveGroupRow(GroupPosition.SINGLE) {
                            ListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.developer_options_reset_pause_timer))
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.developer_options_reset_pause_timer_summary))
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    UpdatePreferences(this@DeveloperOptionsActivity).sheetDismissedUntil = 0L
                                    Toast.makeText(
                                        this@DeveloperOptionsActivity,
                                        R.string.developer_options_reset_pause_timer_done,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
