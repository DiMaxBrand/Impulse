package eu.siacs.conversations.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.FeatureFlag
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ExpressiveGroupRow
import eu.siacs.conversations.ui.GroupPosition
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.utils.FeatureFlagPreferences

class FeatureFlagsActivity : ActionBarActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ImpulseExpressiveTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.feature_flags_title)) },
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
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feature_flags_disclaimer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        // Iterates the registry directly — adding a new FeatureFlag entry is the
                        // only step needed for it to show up here, no other UI change required.
                        val flags = FeatureFlag.entries
                        flags.forEachIndexed { index, flag ->
                            val position = when {
                                flags.size == 1 -> GroupPosition.SINGLE
                                index == 0 -> GroupPosition.TOP
                                index == flags.lastIndex -> GroupPosition.BOTTOM
                                else -> GroupPosition.MIDDLE
                            }
                            ExpressiveGroupRow(position) {
                                FeatureFlagRow(flag)
                            }
                            if (position != GroupPosition.BOTTOM && position != GroupPosition.SINGLE) {
                                Spacer(Modifier.height(2.dp))
                            } else if (index != flags.lastIndex) {
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureFlagRow(flag: FeatureFlag) {
    val context = LocalContext.current
    val prefs = remember(context) { FeatureFlagPreferences(context) }
    var override by remember(flag) { mutableStateOf(prefs.getOverride(flag)) }
    // null (Default) is its own distinct segment, not the same as either explicit value — the
    // whole point of the tri-state is telling "never touched this" apart from "deliberately set
    // to whatever the default currently happens to be".
    val selectedIndex = when (override) {
        null -> 0
        true -> 1
        false -> 2
    }
    val labels = listOf(
        stringResource(R.string.feature_flag_option_default),
        stringResource(R.string.feature_flag_option_on),
        stringResource(R.string.feature_flag_option_off),
    )
    ListItem(
        headlineContent = { Text(stringResource(flag.titleRes)) },
        supportingContent = {
            Column {
                Text(stringResource(flag.descriptionRes))
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                            selected = index == selectedIndex,
                            onClick = {
                                val newOverride = when (index) {
                                    1 -> true
                                    2 -> false
                                    else -> null
                                }
                                override = newOverride
                                prefs.setOverride(flag, newOverride)
                            },
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
