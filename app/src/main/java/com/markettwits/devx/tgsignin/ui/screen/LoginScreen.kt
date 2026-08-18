package com.markettwits.devx.tgsignin.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.TelegramScope
import com.markettwits.devx.tgsignin.ui.component.showTelegramSnackbar
import com.markettwits.devx.tgsignin.ui.viewmodel.LoginState
import com.markettwits.devx.tgsignin.ui.viewmodel.LoginUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    snackbarHostState: SnackbarHostState,
    sessionExpired: Boolean = false,
    onScopesChanged: (Set<TelegramScope>) -> Unit,
    onLogin: () -> Unit
) {
    var pickerExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val cancelledMessage = stringResource(R.string.login_cancelled)
    val sessionExpiredMessage = stringResource(R.string.session_expired)
    val errorMessage = when (val state = uiState.loginState) {
        is LoginState.Error -> stringResource(state.messageRes)
        else -> null
    }

    LaunchedEffect(uiState.loginState, cancelledMessage, sessionExpiredMessage, errorMessage, sessionExpired) {
        if (sessionExpired) {
            snackbarHostState.showTelegramSnackbar(sessionExpiredMessage, isError = true)
            return@LaunchedEffect
        }
        when (uiState.loginState) {
            LoginState.Cancelled -> snackbarHostState.showTelegramSnackbar(cancelledMessage)
            is LoginState.Error -> errorMessage?.let {
                snackbarHostState.showTelegramSnackbar(it, isError = true)
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 148.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            TelegramPlane()
            Spacer(Modifier.size(36.dp))
            Text(
                stringResource(R.string.telegram),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.login_tagline),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(32.dp))
            RequestedDataSelector(
                scopes = uiState.requestedScopes,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    pickerExpanded = true
                }
            )
        }
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onLogin()
            },
            enabled = uiState.loginState !is LoginState.AwaitingConfirmation &&
                uiState.loginState !is LoginState.Verifying,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 36.dp)
                .defaultMinSize(minHeight = 54.dp),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (uiState.loginState is LoginState.AwaitingConfirmation || uiState.loginState is LoginState.Verifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.size(10.dp))
            }
            Text(
                when (uiState.loginState) {
                    LoginState.AwaitingConfirmation -> stringResource(R.string.awaiting_confirmation)
                    LoginState.Verifying -> stringResource(R.string.verifying_sign_in)
                    else -> stringResource(R.string.sign_in_with_telegram)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (pickerExpanded) {
        RequestedDataBottomSheet(
            scopes = uiState.requestedScopes,
            onScopesChanged = onScopesChanged,
            onDismiss = { pickerExpanded = false }
        )
    }
}

@Composable
private fun TelegramPlane() {
    Surface(modifier = Modifier.size(132.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_tg_without_rounded),
            contentDescription = stringResource(R.string.telegram_logo),
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

@Composable
private fun RequestedDataSelector(
    scopes: Set<TelegramScope>,
    onClick: () -> Unit
) {
    val requestedData = buildList {
        add(stringResource(R.string.telegram_id))
        if (TelegramScope.Profile in scopes) add(stringResource(R.string.profile_short))
        if (TelegramScope.Phone in scopes) add(stringResource(R.string.phone_short))
    }.joinToString(stringResource(R.string.requested_data_separator))
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.requested_data_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.size(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(requestedData, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestedDataBottomSheet(
    scopes: Set<TelegramScope>,
    onScopesChanged: (Set<TelegramScope>) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.sign_in_data_title),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(R.string.sign_in_data_description),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ScopeRow(
                icon = Icons.Outlined.AccountCircle,
                title = stringResource(R.string.telegram_id),
                subtitle = stringResource(R.string.required_field),
                selected = true,
                enabled = false,
                onClick = {}
            )
            ScopeRow(
                icon = Icons.Outlined.PersonOutline,
                title = stringResource(R.string.profile),
                subtitle = stringResource(R.string.profile_scope_description),
                selected = TelegramScope.Profile in scopes,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScopesChanged(scopes.toggle(TelegramScope.Profile))
                }
            )
            ScopeRow(
                icon = Icons.Outlined.PhoneAndroid,
                title = stringResource(R.string.phone_number),
                subtitle = stringResource(R.string.phone_scope_description),
                selected = TelegramScope.Phone in scopes,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScopesChanged(scopes.toggle(TelegramScope.Phone))
                }
            )
            TextButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.done), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ScopeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SelectionCheck(selected = selected)
    }
}

@Composable
private fun SelectionCheck(selected: Boolean) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun Set<TelegramScope>.toggle(scope: TelegramScope): Set<TelegramScope> =
    if (scope in this) this - scope else this + scope
