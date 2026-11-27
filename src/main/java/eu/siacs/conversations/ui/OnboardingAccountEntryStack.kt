package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/**
 * The first onboarding screen is deliberately one persistent stack, rather than a list page
 * replaced by a form page. A selected choice owns the same slot and grows around its fields.
 */
@Composable
internal fun OnboardingAccountEntryStack(
    creating: Boolean?,
    simulatedRouteTitle: String?,
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
    formEnabled: Boolean,
    onCreate: () -> Unit,
    onLogIn: () -> Unit,
    onRestore: () -> Unit,
    onQr: () -> Unit,
    onCertificate: () -> Unit,
    certificateEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val selection = creating != null || simulatedRouteTitle != null
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RotatableOnboardingLogo()
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.welcome_intro_body),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(2.dp))

        // This is the original Create Account card's slot. AnimatedContent sizes the same
        // surface from its compact decision state to the credentials form, in place.
        AnimatedContent(
            targetState = creating == true,
            transitionSpec = {
                (fadeIn(tween(110)) + slideInVertically(tween(220)) { it / 14 }) togetherWith
                    (fadeOut(tween(90)) + slideOutVertically(tween(160)) { -it / 18 }) using
                    SizeTransform(clip = false)
            },
            label = "create_account_card_transform",
        ) { expanded ->
            if (expanded) {
                OnboardingAccountCard(
                    creating = true,
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
                    enabled = formEnabled,
                )
            } else {
                OnboardingChoiceCard(
                    title = stringResource(R.string.create_account),
                    detail = stringResource(R.string.onboarding_create_account_detail),
                    emphasized = true,
                    onClick = onCreate,
                )
            }
        }

        // When a card is selected, its neighbours retreat instead of a new full-screen card
        // being presented. The Log in form keeps the original Log in slot before it grows.
        AnimatedVisibility(
            visible = !selection || creating == false,
            enter = fadeIn(tween(120)) + slideInVertically(tween(180)) { it / 16 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(170)) { -it / 16 },
        ) {
            AnimatedContent(
                targetState = creating == false,
                transitionSpec = {
                    (fadeIn(tween(110)) + slideInVertically(tween(220)) { it / 14 }) togetherWith
                        (fadeOut(tween(90)) + slideOutVertically(tween(160)) { -it / 18 }) using
                        SizeTransform(clip = false)
                },
                label = "login_card_transform",
            ) { expanded ->
                if (expanded) {
                    OnboardingAccountCard(
                        creating = false,
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
                        enabled = formEnabled,
                    )
                } else {
                    OnboardingChoiceCard(
                        title = stringResource(R.string.log_in),
                        detail = stringResource(R.string.onboarding_log_in_detail),
                        onClick = onLogIn,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !selection,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)) + slideOutVertically(tween(150)) { -it / 12 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.onboarding_other_start_options),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExpressiveGroupRow(GroupPosition.TOP) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.restore_backup)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onRestore),
                    )
                }
                ExpressiveGroupRow(GroupPosition.MIDDLE) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.scan_qr_code)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onQr),
                    )
                }
                ExpressiveGroupRow(GroupPosition.BOTTOM) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_add_account_with_certificate)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = certificateEnabled, onClick = onCertificate),
                    )
                }
            }
        }

        simulatedRouteTitle?.let { routeTitle ->
            // The developer replay deliberately has a safe, route-specific result instead of
            // pretending Restore, QR, and certificate login are all ordinary password login.
            OnboardingChoiceCard(
                title = routeTitle,
                detail = stringResource(R.string.onboarding_simulated_account_route),
                onClick = {},
            )
        }
    }
}
