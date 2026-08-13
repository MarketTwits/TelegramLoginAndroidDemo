package com.markettwits.devx.tgsignin.ui.screen

import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneNumberUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.markettwits.devx.tgsignin.BuildConfig
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.TelegramUser
import com.markettwits.devx.tgsignin.data.telegram.TelegramLoginConfig
import com.markettwits.devx.tgsignin.ui.component.ConfigurationInfoButton
import com.markettwits.devx.tgsignin.ui.component.TelegramConfirmationDialog
import com.markettwits.devx.tgsignin.ui.component.TelegramDialog
import com.markettwits.devx.tgsignin.ui.component.TelegramSnackbar
import com.markettwits.devx.tgsignin.ui.model.AppLinkVerificationUiState
import com.markettwits.devx.tgsignin.ui.model.BackendReadinessUiState

@Composable
fun ProfileScreen(
    user: TelegramUser,
    telegramConfig: TelegramLoginConfig,
    backendReadinessState: BackendReadinessUiState,
    appLinkVerificationState: AppLinkVerificationUiState,
    messages: Flow<Int>,
    onLogout: () -> Unit,
    onRetryBackendReadiness: () -> Unit,
    onRetryAppLinkVerification: () -> Unit
) {
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    var initialCollapseApplied by rememberSaveable { mutableStateOf(false) }
    val collapseRangePx = with(LocalDensity.current) {
        (PROFILE_HERO_EXPANDED_HEIGHT - PROFILE_HERO_COLLAPSED_HEIGHT).toPx()
    }
    val collapsedScrollPosition = collapseRangePx.roundToInt()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val collapseProgress by remember(scrollState, collapseRangePx) {
        derivedStateOf {
            if (initialCollapseApplied) {
                (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }
    var headerMoved by remember { mutableStateOf(false) }
    val settleHeader: () -> Unit = {
        coroutineScope.launch {
            if (scrollState.value in 1 until collapsedScrollPosition) {
                val target = if (scrollState.value < collapsedScrollPosition / 2) {
                    0
                } else {
                    collapsedScrollPosition
                }
                scrollState.animateScrollTo(target, tween(durationMillis = 260))
            }
        }
    }

    LaunchedEffect(scrollState, collapsedScrollPosition, initialCollapseApplied) {
        if (!initialCollapseApplied) {
            snapshotFlow { scrollState.maxValue }
                .first { maxValue -> maxValue >= collapsedScrollPosition }
            scrollState.scrollTo(collapsedScrollPosition)
            initialCollapseApplied = true
        }
    }

    LaunchedEffect(collapseProgress) {
        if (collapseProgress > 0.05f) {
            headerMoved = true
        } else if (headerMoved) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            headerMoved = false
        }
    }

    LaunchedEffect(messages) {
        messages.collectLatest { messageRes ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resources.getString(messageRes))
        }
    }

    LaunchedEffect(scrollState, collapsedScrollPosition) {
        snapshotFlow { scrollState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling && scrollState.value in 1 until collapsedScrollPosition) {
                    val target = if (scrollState.value < collapsedScrollPosition / 2) {
                        0
                    } else {
                        collapsedScrollPosition
                    }
                    scrollState.animateScrollTo(target, tween(durationMillis = 260))
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(PROFILE_HERO_EXPANDED_HEIGHT))
            ProfileInformation(
                user = user,
                onCopy = { field ->
                    field.value?.let { value ->
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText(field.label, value))
                            )
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.copied_message, field.label)
                            )
                        }
                    }
                }
            )
            Spacer(
                Modifier.height(
                    28.dp + (PROFILE_HERO_EXPANDED_HEIGHT - PROFILE_HERO_COLLAPSED_HEIGHT)
                )
            )
        }

        ProfileHero(
            user = user,
            collapseProgress = collapseProgress,
            scrollState = scrollState,
            onDragStopped = settleHeader,
            onToggle = {
                coroutineScope.launch {
                    val target = if (collapseProgress < 0.5f) collapseRangePx.roundToInt() else 0
                    scrollState.animateScrollTo(target, tween(durationMillis = 420))
                }
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 6.dp, start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { showLogoutConfirmation = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.shadow(3.dp, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = stringResource(R.string.logout_account_description),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            ConfigurationInfoButton(
                hasError = backendReadinessState is BackendReadinessUiState.Error ||
                    appLinkVerificationState is AppLinkVerificationUiState.Error,
                onClick = { showConfiguration = true }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) { data ->
            TelegramSnackbar(message = data.visuals.message)
        }
    }

    if (showLogoutConfirmation) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmation = false }
        )
    }

    if (showConfiguration) {
        ConfigurationDialog(
            telegramConfig = telegramConfig,
            backendReadinessState = backendReadinessState,
            appLinkVerificationState = appLinkVerificationState,
            onRetryBackendReadiness = onRetryBackendReadiness,
            onRetryAppLinkVerification = onRetryAppLinkVerification,
            onDismiss = { showConfiguration = false }
        )
    }
}

@Composable
private fun ProfileHero(
    user: TelegramUser,
    collapseProgress: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    onDragStopped: () -> Unit,
    onToggle: () -> Unit
) {
    val collapsePhotoDescription = stringResource(R.string.collapse_profile_photo)
    val expandPhotoDescription = stringResource(R.string.expand_profile_photo)
    val telegramName = stringResource(R.string.telegram)
    val height = PROFILE_HERO_EXPANDED_HEIGHT -
        ((PROFILE_HERO_EXPANDED_HEIGHT - PROFILE_HERO_COLLAPSED_HEIGHT) * collapseProgress)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .pointerInput(scrollState, onToggle, onDragStopped) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dragStart = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                        change.consume()
                        scrollState.dispatchRawDelta(-overSlop.y)
                    }
                    if (dragStart == null) {
                        onToggle()
                    } else {
                        drag(dragStart.id) { change ->
                            val delta = change.positionChange().y
                            if (delta != 0f) {
                                change.consume()
                                scrollState.dispatchRawDelta(-delta)
                            }
                        }
                        onDragStopped()
                    }
                }
            }
    ) {
        BlurredAvatarBackground(user)

        if (user.pictureUrl != null) {
            RemoteAvatar(
                user = user,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - collapseProgress },
                contentScale = ContentScale.Crop,
                shimmer = true
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.04f + (0.06f * collapseProgress)),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(112.dp)
                .graphicsLayer {
                    alpha = collapseProgress
                    val avatarScale = 0.82f + (0.18f * collapseProgress)
                    scaleX = avatarScale
                    scaleY = avatarScale
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            RemoteAvatar(
                user = user,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
                shimmer = true
            )
        }

        var nameWidth by remember { mutableIntStateOf(0) }
        val startInset = with(LocalDensity.current) { 24.dp.toPx() }
        val centeredX = ((constraints.maxWidth - nameWidth) / 2f).coerceAtLeast(startInset)
        val nameX = startInset + ((centeredX - startInset) * collapseProgress)

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(nameX.roundToInt(), 0) }
                .padding(bottom = 24.dp)
                .onSizeChanged { nameWidth = it.width },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = user.name ?: user.username?.let { "@$it" } ?: telegramName,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            user.username?.let { username ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "@$username",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics {
                    role = Role.Button
                    contentDescription = if (collapseProgress < 0.5f) {
                        collapsePhotoDescription
                    } else {
                        expandPhotoDescription
                    }
                    onClick {
                        onToggle()
                        true
                    }
                }
        )
    }
}

@Composable
private fun BlurredAvatarBackground(user: TelegramUser) {
    if (user.pictureUrl != null) {
        RemoteAvatar(
            user = user,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.16f
                    scaleY = 1.16f
                }
                .blur(36.dp),
            contentScale = ContentScale.Crop,
            shimmer = false
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        )
    }
}

@Composable
private fun RemoteAvatar(
    user: TelegramUser,
    modifier: Modifier,
    contentScale: ContentScale,
    shimmer: Boolean
) {
    val pictureUrl = user.pictureUrl
    if (pictureUrl == null) {
        AvatarPlaceholder(user = user, modifier = modifier, shimmer = false)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(pictureUrl)
            .crossfade(true)
            .build(),
        contentDescription = stringResource(R.string.user_avatar),
        contentScale = contentScale,
        modifier = modifier,
        loading = { AvatarPlaceholder(user, Modifier.fillMaxSize(), shimmer) },
        error = { AvatarPlaceholder(user, Modifier.fillMaxSize(), false) }
    )
}

@Composable
private fun AvatarPlaceholder(
    user: TelegramUser,
    modifier: Modifier,
    shimmer: Boolean
) {
    val transition = rememberInfiniteTransition(label = "avatarShimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_250, easing = LinearEasing)
        ),
        label = "avatarShimmerOffset"
    )
    val shimmerBrush = if (shimmer) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f),
                MaterialTheme.colorScheme.primary
            ),
            start = Offset(shimmerOffset - 420f, 0f),
            end = Offset(shimmerOffset, 420f)
        )
    } else {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
        )
    }
    Box(
        modifier = modifier.background(shimmerBrush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user.initials(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProfileInformation(
    user: TelegramUser,
    onCopy: (ProfileValue) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileCard(
            fields = listOf(
                ProfileValue(user.phoneNumber?.formattedPhone(), stringResource(R.string.phone)),
                ProfileValue(user.username?.let { "@$it" }, stringResource(R.string.username))
            ),
            onCopy = onCopy
        )
        ProfileCard(
            title = stringResource(R.string.telegram_data),
            fields = listOf(
                ProfileValue(user.id, stringResource(R.string.telegram_id)),
                ProfileValue(user.name, stringResource(R.string.name)),
                ProfileValue(user.givenName, stringResource(R.string.given_name)),
                ProfileValue(user.familyName, stringResource(R.string.family_name)),
                ProfileValue(
                    if (user.phoneNumber == null) {
                        null
                    } else if (user.phoneVerified) {
                        stringResource(R.string.yes)
                    } else {
                        stringResource(R.string.no)
                    },
                    stringResource(R.string.phone_verified)
                )
            ),
            onCopy = onCopy
        )
    }
}

@Composable
private fun ProfileCard(
    fields: List<ProfileValue>,
    title: String? = null,
    onCopy: (ProfileValue) -> Unit
) {
    var openedFieldIndex by remember { mutableStateOf<Int?>(null) }
    val notProvided = stringResource(R.string.not_provided)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            if (title != null) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            fields.forEachIndexed { index, field ->
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = field.value != null) {
                                openedFieldIndex = index
                            }
                            .padding(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Text(
                            text = field.value ?: notProvided,
                            color = if (field.value == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = field.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DropdownMenu(
                        expanded = openedFieldIndex == index,
                        onDismissRequest = { openedFieldIndex = null },
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.width(164.dp)
                    ) {
                        Surface(
                            onClick = {
                                openedFieldIndex = null
                                onCopy(field)
                            },
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    text = stringResource(R.string.copy),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                if (index != fields.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 18.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

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
    val appInfo = remember(context) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        AppConfigurationInfo(
            packageName = context.packageName,
            version = buildString {
                append(packageInfo.versionName ?: "—")
                append(" (")
                append(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                )
                append(')')
            },
            minSdk = context.applicationInfo.minSdkVersion.toString(),
            targetSdk = context.applicationInfo.targetSdkVersion.toString(),
            signingSha256 = context.signingCertificateSha256()
        )
    }

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
                            ProfileValue(appInfo.packageName, stringResource(R.string.package_name)),
                            ProfileValue(appInfo.version, stringResource(R.string.version)),
                            ProfileValue(BuildConfig.BUILD_TYPE, stringResource(R.string.build_type)),
                            ProfileValue(appInfo.minSdk, stringResource(R.string.min_sdk)),
                            ProfileValue(appInfo.targetSdk, stringResource(R.string.target_sdk)),
                            ProfileValue(appInfo.signingSha256, stringResource(R.string.signing_sha256))
                        )
                    )
                    ConfigurationSection(
                        title = stringResource(R.string.telegram_login_sdk),
                        values = listOf(
                            ProfileValue(BuildConfig.TELEGRAM_SDK_VERSION, stringResource(R.string.sdk_version)),
                            ProfileValue(telegramConfig.clientId, stringResource(R.string.client_id)),
                            ProfileValue(telegramConfig.redirectUri, stringResource(R.string.redirect_uri)),
                            ProfileValue(telegramConfig.redirectHost, stringResource(R.string.redirect_host)),
                            ProfileValue(telegramConfig.backendUrl, stringResource(R.string.backend_url)),
                            ProfileValue(
                                stringResource(R.string.configured),
                                stringResource(R.string.status)
                            )
                        )
                    )
                    AppLinkVerificationSection(
                        state = appLinkVerificationState,
                        onRetry = onRetryAppLinkVerification
                    )
                    BackendReadinessSection(
                        state = backendReadinessState,
                        onRetry = onRetryBackendReadiness
                    )
            }
        }
    }
}

@Composable
private fun AppLinkVerificationSection(
    state: AppLinkVerificationUiState,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.app_link_verification),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        when (state) {
            AppLinkVerificationUiState.Checking -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.app_link_checking),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            AppLinkVerificationUiState.Verified -> Text(
                text = stringResource(R.string.app_link_verified),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )

            is AppLinkVerificationUiState.Error -> Row(
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
                        text = stringResource(R.string.app_link_not_verified),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(state.messageRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendReadinessSection(
    state: BackendReadinessUiState,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.backend_service),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        when (state) {
            BackendReadinessUiState.Checking -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.backend_checking),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            BackendReadinessUiState.Ready -> Text(
                text = stringResource(R.string.backend_ready),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )

            is BackendReadinessUiState.Error -> Row(
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
                        text = stringResource(R.string.backend_not_ready),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(state.messageRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSection(
    title: String,
    values: List<ProfileValue>
) {
    val notConfigured = stringResource(R.string.not_configured)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        values.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = field.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = field.value ?: notConfigured,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    TelegramConfirmationDialog(
        title = stringResource(R.string.logout_title),
        message = buildString {
            append(stringResource(R.string.logout_confirmation))
            append("\n\n")
            append(stringResource(R.string.logout_data_warning))
        },
        confirmText = stringResource(R.string.logout_title),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true
    )
}

private data class ProfileValue(val value: String?, val label: String)

private val PROFILE_HERO_EXPANDED_HEIGHT = 500.dp
private val PROFILE_HERO_COLLAPSED_HEIGHT = 280.dp

private data class AppConfigurationInfo(
    val packageName: String,
    val version: String,
    val minSdk: String,
    val targetSdk: String,
    val signingSha256: String
)


@Suppress("DEPRECATION")
private fun Context.signingCertificateSha256(): String {
    val signature = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo ?: return@runCatching null
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.firstOrNull()
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures
                ?.firstOrNull()
        }
    }.getOrNull() ?: return getString(R.string.unavailable)

    return MessageDigest.getInstance(SIGNING_DIGEST_ALGORITHM)
        .digest(signature.toByteArray())
        .joinToString(SIGNING_DIGEST_SEPARATOR) { byte ->
            SIGNING_DIGEST_BYTE_FORMAT.format(byte.toInt() and UNSIGNED_BYTE_MASK)
        }
}

private fun String.formattedPhone(): String =
    PhoneNumberUtils.formatNumber(this, Locale.getDefault().country) ?: this

private fun TelegramUser.initials(): String = (username ?: name ?: DEFAULT_PROFILE_INITIALS)
    .trim()
    .split(Regex(INITIALS_SEPARATOR_PATTERN))
    .take(MAX_INITIALS_PARTS)
    .joinToString(EMPTY_TEXT) { it.take(INITIALS_CHARACTERS_PER_PART).uppercase() }

private const val DEFAULT_PROFILE_INITIALS = "TG"
private const val EMPTY_TEXT = ""
private const val INITIALS_SEPARATOR_PATTERN = "\\s+"
private const val MAX_INITIALS_PARTS = 2
private const val INITIALS_CHARACTERS_PER_PART = 1
private const val SIGNING_DIGEST_ALGORITHM = "SHA-256"
private const val SIGNING_DIGEST_SEPARATOR = ":"
private const val SIGNING_DIGEST_BYTE_FORMAT = "%02X"
private const val UNSIGNED_BYTE_MASK = 0xFF
