package eu.siacs.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

internal const val ONBOARDING_TOUR_PAGES = 5

@Composable
internal fun OnboardingTour(page: Int, modifier: Modifier = Modifier) {
    val title = listOf(R.string.welcome_intro_title, R.string.welcome_edit_title, R.string.welcome_pin_title, R.string.welcome_menu_title, R.string.welcome_glass_title)[page]
    val body = listOf(R.string.welcome_intro_body, R.string.welcome_edit_body, R.string.welcome_pin_body, R.string.welcome_menu_body, R.string.welcome_glass_body)[page]
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (page) {
            1 -> LoopingWelcomeDemo(R.raw.welcome_edit_demo)
            3 -> WelcomeScreenshot(R.drawable.welcome_context_menu)
            4 -> LoopingWelcomeDemo(R.raw.welcome_glass_demo)
            else -> Box(Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (page == 2) R.drawable.ic_push_pin_24dp else R.drawable.ic_chat_24dp), null,
                    Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(stringResource(title), Modifier.padding(top = 32.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(body), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
