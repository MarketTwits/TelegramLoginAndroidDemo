package com.markettwits.devx.tgsignin.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.BuildConfig
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.component.TelegramDialog
import com.markettwits.devx.tgsignin.ui.viewmodel.AppLinkVerificationUiState
import com.markettwits.devx.tgsignin.ui.viewmodel.BackendReadinessUiState
import java.security.MessageDigest

@Composable
fun ConfigurationDialog(
    telegramConfig: TelegramLoginConfig,
    backendReadinessState: BackendReadinessUiState,
    appLinkVerificationState: AppLinkVerificationUiState,
    onRetryBackendReadiness: () -> Unit,
    onRetryAppLinkVerification: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appInfo = remember(context) { context.appConfigurationInfo() }

    TelegramDialog(
        title = stringResource(R.string.configuration_title),
        actionText = stringResource(R.string.close),
        onDismiss = onDismiss
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ConfigurationSection(
                    title = stringResource(R.string.application),
                    values = listOf(
                        ConfigurationValue(
                            appInfo.packageName,
                            stringResource(R.string.package_name)
                        ),
                        ConfigurationValue(appInfo.version, stringResource(R.string.version)),
                        ConfigurationValue(
                            BuildConfig.BUILD_TYPE,
                            stringResource(R.string.build_type)
                        ),
                        ConfigurationValue(appInfo.minSdk, stringResource(R.string.min_sdk)),
                        ConfigurationValue(appInfo.targetSdk, stringResource(R.string.target_sdk)),
                        ConfigurationValue(
                            appInfo.signingSha256,
                            stringResource(R.string.signing_sha256)
                        )
                    )
                )
                ConfigurationSection(
                    title = stringResource(R.string.telegram_login_sdk),
                    values = listOf(
                        ConfigurationValue(
                            BuildConfig.TELEGRAM_SDK_VERSION,
                            stringResource(R.string.sdk_version)
                        ),
                        ConfigurationValue(
                            telegramConfig.clientId,
                            stringResource(R.string.client_id)
                        ),
                        ConfigurationValue(
                            telegramConfig.redirectUri,
                            stringResource(R.string.redirect_uri)
                        ),
                        ConfigurationValue(
                            telegramConfig.redirectHost,
                            stringResource(R.string.redirect_host)
                        ),
                        ConfigurationValue(
                            telegramConfig.backendUrl,
                            stringResource(R.string.backend_url)
                        ),
                        ConfigurationValue(
                            stringResource(R.string.configured),
                            stringResource(R.string.status)
                        )
                    )
                )
                AppLinkVerificationSection(appLinkVerificationState, onRetryAppLinkVerification)
                BackendReadinessSection(backendReadinessState, onRetryBackendReadiness)
            }
        }
    }
}

@Composable
private fun AppLinkVerificationSection(
    state: AppLinkVerificationUiState,
    onRetry: () -> Unit
) {
    when (state) {
        AppLinkVerificationUiState.Checking -> ConfigurationStatusSection(
            title = R.string.app_link_verification,
            status = ConfigurationStatus.Checking(R.string.app_link_checking)
        )

        AppLinkVerificationUiState.Verified -> ConfigurationStatusSection(
            title = R.string.app_link_verification,
            status = ConfigurationStatus.Ready(R.string.app_link_verified)
        )

        is AppLinkVerificationUiState.Error -> ConfigurationStatusSection(
            title = R.string.app_link_verification,
            status = ConfigurationStatus.Error(
                title = R.string.app_link_not_verified,
                message = state.messageRes
            ),
            onRetry = onRetry
        )
    }
}

@Composable
private fun BackendReadinessSection(
    state: BackendReadinessUiState,
    onRetry: () -> Unit
) {
    when (state) {
        BackendReadinessUiState.Checking -> ConfigurationStatusSection(
            title = R.string.backend_service,
            status = ConfigurationStatus.Checking(R.string.backend_checking)
        )

        BackendReadinessUiState.Ready -> ConfigurationStatusSection(
            title = R.string.backend_service,
            status = ConfigurationStatus.Ready(R.string.backend_ready)
        )

        is BackendReadinessUiState.Error -> ConfigurationStatusSection(
            title = R.string.backend_service,
            status = ConfigurationStatus.Error(
                title = R.string.backend_not_ready,
                message = state.messageRes
            ),
            onRetry = onRetry
        )
    }
}

@Composable
private fun ConfigurationStatusSection(
    @StringRes title: Int,
    status: ConfigurationStatus,
    onRetry: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ConfigurationTitle(stringResource(title))
        when (status) {
            is ConfigurationStatus.Checking -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                ConfigurationBodyText(stringResource(status.message))
            }

            is ConfigurationStatus.Ready -> ConfigurationBodyText(
                text = stringResource(status.message),
                useMutedColor = false
            )

            is ConfigurationStatus.Error -> Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(status.title),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(status.message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    onRetry?.let { retry ->
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                retry()
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSection(title: String, values: List<ConfigurationValue>) {
    val notConfigured = stringResource(R.string.not_configured)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ConfigurationTitle(title)
        values.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = field.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                ConfigurationBodyText(field.value ?: notConfigured, useMutedColor = false)
            }
        }
    }
}

@Composable
private fun ConfigurationTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ConfigurationBodyText(text: String, useMutedColor: Boolean = true) {
    Text(
        text = text,
        color = if (useMutedColor) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun Context.appConfigurationInfo(): AppConfigurationInfo {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    return AppConfigurationInfo(
        packageName = packageName,
        version = "${packageInfo.versionName ?: "—"} (${packageInfo.versionCodeCompat()})",
        minSdk = applicationInfo.minSdkVersion.toString(),
        targetSdk = applicationInfo.targetSdkVersion.toString(),
        signingSha256 = signingCertificateSha256()
    )
}

private fun android.content.pm.PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

@Suppress("DEPRECATION")
private fun Context.signingCertificateSha256(): String {
    val signature = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo ?: return@runCatching null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.firstOrNull()
            } else {
                signingInfo.signingCertificateHistory.firstOrNull()
            }
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        }
    }.getOrNull() ?: return getString(R.string.unavailable)

    return MessageDigest.getInstance("SHA-256")
        .digest(signature.toByteArray())
        .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}

private sealed interface ConfigurationStatus {
    data class Checking(@StringRes val message: Int) : ConfigurationStatus
    data class Ready(@StringRes val message: Int) : ConfigurationStatus
    data class Error(
        @StringRes val title: Int,
        @StringRes val message: Int
    ) : ConfigurationStatus
}

private data class ConfigurationValue(val value: String?, val label: String)

private data class AppConfigurationInfo(
    val packageName: String,
    val version: String,
    val minSdk: String,
    val targetSdk: String,
    val signingSha256: String
)
