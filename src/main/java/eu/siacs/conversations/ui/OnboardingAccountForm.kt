package eu.siacs.conversations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/** Controlled form: the owner keeps the password in memory and handles validation and networking. */
@Composable
internal fun OnboardingAccountForm(
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
    modifier: Modifier = Modifier,
) {
    var showPassword by remember { mutableStateOf(false) }
    var showServerOptions by remember { mutableStateOf(hostname.isNotEmpty()) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(if (creating) R.string.create_account else R.string.log_in),
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(if (creating) R.string.username else R.string.account_settings_jabber_id)) },
            suffix = if (!address.contains('@')) ({ Text("@${if (creating) registrationDomain else "on-chat.ru"}") }) else null,
            supportingText = addressError?.let { error -> { Text(error) } },
            isError = addressError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = enabled,
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.password)) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painter = painterResource(if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility_24dp),
                        contentDescription = stringResource(if (showPassword) R.string.onboarding_hide_password else R.string.onboarding_show_password),
                    )
                }
            },
            supportingText = passwordError?.let { error -> { Text(error) } },
            isError = passwordError != null,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = enabled,
        )
        TextButton(onClick = { showServerOptions = !showServerOptions }) {
            Text(stringResource(R.string.onboarding_server_options))
        }
        if (showServerOptions) {
            OutlinedTextField(value = hostname, onValueChange = onHostnameChange,
                label = { Text(stringResource(R.string.onboarding_connection_host)) },
                supportingText = { Text(stringResource(R.string.onboarding_connection_host_detail)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
            OutlinedTextField(value = port, onValueChange = onPortChange,
                label = { Text(stringResource(R.string.account_settings_port)) },
                supportingText = serverError?.let { error -> { Text(error) } },
                isError = serverError != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
        }
    }
}
