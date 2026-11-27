package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The selected account card keeps its place and grows around the form it reveals. */
@Composable
internal fun OnboardingAccountCard(
    creating: Boolean,
    registrationDomain: String,
    address: String,
    onAddressChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    addressError: String?,
    passwordError: String?,
    hostname: String,
    onHostnameChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    serverError: String?,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        ),
        shape = RoundedCornerShape(32.dp),
        color = if (creating) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        AnimatedContent(
            targetState = creating,
            transitionSpec = {
                (slideInHorizontally { it / 7 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 7 } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            label = "account_card_contents",
        ) { isCreating ->
            OnboardingAccountForm(
                creating = isCreating,
                registrationDomain = registrationDomain,
                address = address,
                onAddressChange = onAddressChange,
                password = password,
                onPasswordChange = onPasswordChange,
                addressError = addressError,
                passwordError = passwordError,
                hostname = hostname,
                onHostnameChange = onHostnameChange,
                port = port,
                onPortChange = onPortChange,
                serverError = serverError,
                enabled = enabled,
                modifier = Modifier.padding(22.dp),
                showHeading = true,
                scrollable = false,
            )
        }
    }
}
