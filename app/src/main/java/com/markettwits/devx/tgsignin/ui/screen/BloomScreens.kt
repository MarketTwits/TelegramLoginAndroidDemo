package com.markettwits.devx.tgsignin.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.AvatarSource
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.model.ProfileTopic
import com.markettwits.devx.tgsignin.ui.model.bloomVisualSpec
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ProfileSetupScreen(
    session: AuthenticationResult,
    draft: ProfileDraft,
    isSaving: Boolean,
    isOffline: Boolean,
    messages: Flow<Int>,
    onDraftChanged: (ProfileDraft) -> Unit,
    onSave: (ProfileDraft) -> Unit,
    onCancel: () -> Unit
) {
    val isEditing = session.profile != null
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var displayNameTouched by rememberSaveable { mutableStateOf(false) }
    var headlineTouched by rememberSaveable { mutableStateOf(false) }
    var topicLimitReached by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(messages) {
        messages.collectLatest { snackbar.showSnackbar(resources.getString(it)) }
    }
    LaunchedEffect(session.telegram.pictureUrl, draft.avatarSource) {
        if (session.telegram.pictureUrl == null && draft.avatarSource == AvatarSource.TELEGRAM) {
            onDraftChanged(draft.copy(avatarSource = AvatarSource.BLOOM))
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(40.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(if (isEditing) R.string.bloom_edit_title else R.string.bloom_setup_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                if (isEditing) {
                    TextButton(onClick = onCancel, enabled = !isSaving) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
            Text(
                stringResource(
                    if (isEditing) R.string.bloom_edit_subtitle else R.string.bloom_setup_subtitle
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isOffline) OfflineBanner()
            AvatarChoice(session, draft, onDraftChanged)
            OutlinedTextField(
                value = draft.displayName,
                onValueChange = {
                    displayNameTouched = true
                    if (it.length <= 80) onDraftChanged(draft.copy(displayName = it))
                },
                label = { Text(stringResource(R.string.bloom_display_name)) },
                supportingText = if (displayNameTouched && draft.displayName.isBlank()) {
                    { Text(stringResource(R.string.required_field)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = displayNameTouched && draft.displayName.isBlank()
            )
            Text(stringResource(R.string.bloom_intent_title), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileIntent.entries.forEach { intent ->
                    FilterChip(
                        selected = draft.intent == intent,
                        onClick = { onDraftChanged(draft.copy(intent = intent)) },
                        label = { Text(intentLabel(intent)) }
                    )
                }
            }
            OutlinedTextField(
                value = draft.headline,
                onValueChange = {
                    headlineTouched = true
                    if (it.length <= 120) onDraftChanged(draft.copy(headline = it))
                },
                label = { Text(intentPrompt(draft.intent)) },
                supportingText = when {
                    headlineTouched && draft.headline.isBlank() -> {
                        { Text(stringResource(R.string.required_field)) }
                    }
                    draft.headline.length >= 100 -> {
                        { Text("${draft.headline.length}/120") }
                    }
                    else -> null
                },
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                isError = headlineTouched && draft.headline.isBlank()
            )
            Text(stringResource(R.string.bloom_topics_title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.bloom_topics_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTopic.entries.forEach { topic ->
                    val selected = topic in draft.topics
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val topics = if (selected) draft.topics - topic else {
                                if (draft.topics.size < 3) {
                                    topicLimitReached = false
                                    draft.topics + topic
                                } else {
                                    topicLimitReached = true
                                    draft.topics
                                }
                            }
                            if (selected) topicLimitReached = false
                            onDraftChanged(draft.copy(topics = topics))
                        },
                        label = { Text(topicLabel(topic)) }
                    )
                }
            }
            if (topicLimitReached) {
                Text(
                    stringResource(R.string.bloom_topics_limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TelegramIdentitySummary(session)
            Button(
                onClick = { onSave(draft) },
                enabled = draft.isValid && !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (session.profile == null) R.string.bloom_create_profile else R.string.bloom_save_profile))
                }
            }
        }
    }
}

@Composable
private fun AvatarChoice(
    session: AuthenticationResult,
    draft: ProfileDraft,
    onDraftChanged: (ProfileDraft) -> Unit
) {
    Text(stringResource(R.string.bloom_avatar_title), fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (session.telegram.pictureUrl != null) {
            OutlinedCard(
                onClick = { onDraftChanged(draft.copy(avatarSource = AvatarSource.TELEGRAM)) },
                border = BorderStroke(
                    if (draft.avatarSource == AvatarSource.TELEGRAM) 2.dp else 1.dp,
                    if (draft.avatarSource == AvatarSource.TELEGRAM) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.weight(1f)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = session.telegram.pictureUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                    Text(stringResource(R.string.bloom_telegram_photo), Modifier.padding(start = 8.dp))
                }
            }
        }
        OutlinedCard(
            onClick = { onDraftChanged(draft.copy(avatarSource = AvatarSource.BLOOM)) },
            border = BorderStroke(
                if (draft.avatarSource == AvatarSource.BLOOM) 2.dp else 1.dp,
                if (draft.avatarSource == AvatarSource.BLOOM) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            modifier = Modifier.weight(1f)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BloomVisual(session.profile?.visualSeed ?: session.account.id, Modifier.size(40.dp))
                Text(stringResource(R.string.bloom_my_bloom), Modifier.padding(start = 8.dp))
            }
        }
    }
    Text(
        stringResource(
            if (draft.avatarSource == AvatarSource.TELEGRAM) R.string.bloom_telegram_selected
            else R.string.bloom_service_selected
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun BloomProfileScreen(
    session: AuthenticationResult,
    isOffline: Boolean,
    messages: Flow<Int>,
    onEdit: () -> Unit,
    onLogout: () -> Unit
) {
    val profile = requireNotNull(session.profile)
    val scroll = rememberScrollState()
    val collapseRangePx = with(LocalDensity.current) { 140.dp.toPx() }
    val collapse by remember(scroll, collapseRangePx) {
        derivedStateOf { (scroll.value / collapseRangePx).coerceIn(0f, 1f) }
    }
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var lastBoundary by remember { mutableStateOf(0) }
    LaunchedEffect(messages) {
        messages.collectLatest { snackbar.showSnackbar(resources.getString(it)) }
    }
    LaunchedEffect(scroll, collapseRangePx) {
        snapshotFlow { scroll.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                val range = collapseRangePx.toInt()
                if (!scrolling && scroll.value in 1 until range) {
                    val target = if (scroll.value < range / 2) 0 else range
                    scroll.animateScrollTo(target)
                }
            }
    }
    LaunchedEffect(collapse) {
        val boundary = when {
            collapse <= 0.01f -> 0
            collapse >= 0.99f -> 1
            else -> -1
        }
        if (boundary >= 0 && lastBoundary != boundary) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            lastBoundary = boundary
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).navigationBarsPadding()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.fillMaxWidth().height((300 - (140 * collapse)).dp)
            ) {
                Column(
                    Modifier.statusBarsPadding().padding(top = 44.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ProfileAvatar(session, Modifier.size((112 - (52 * collapse)).dp))
                    Text(
                        profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    session.telegram.username?.let { Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(intentLabel(profile.intent), color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isOffline) OfflineBanner()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.bloom_current_signal), fontWeight = FontWeight.Bold)
                        Text(intentLabel(profile.intent), color = MaterialTheme.colorScheme.primary)
                        Text(profile.headline, style = MaterialTheme.typography.titleMedium)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.topics.forEach { topic ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(topicLabel(topic), Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.bloom_membership), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.bloom_member_number, session.account.memberNumber))
                        Text(stringResource(R.string.bloom_joined, formatDate(session.account.registeredAt)))
                        Text(stringResource(R.string.bloom_last_sign_in, formatDate(session.account.lastLoginAt)))
                        Text(pluralStringResource(
                            R.plurals.bloom_login_count,
                            session.account.loginCount,
                            session.account.loginCount
                        ))
                    }
                }
                TelegramIdentitySummary(session)
                Spacer(Modifier.height(140.dp))
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.bloom_edit_profile))
            }
            IconButton(onClick = { confirmLogout = true }) {
                Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.logout_account_description))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_title)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) {
                    Text(stringResource(R.string.logout_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ProfileAvatar(session: AuthenticationResult, modifier: Modifier) {
    val profile = requireNotNull(session.profile)
    if (profile.avatarSource == AvatarSource.TELEGRAM && session.telegram.pictureUrl != null) {
        AsyncImage(
            model = session.telegram.pictureUrl,
            contentDescription = stringResource(R.string.user_avatar),
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        BloomVisual(profile.visualSeed, modifier)
    }
}

@Composable
fun BloomVisual(seed: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.bloom_visual_description)
    val spec = remember(seed) { bloomVisualSpec(seed) }
    val first = Color.hsv(spec.primaryHue, .58f, .92f)
    val second = Color.hsv(spec.secondaryHue, .52f, .78f)
    val centerColor = MaterialTheme.colorScheme.surface.copy(alpha = .9f)
    Canvas(modifier.clip(CircleShape).semantics { contentDescription = description }) {
        drawCircle(first)
        val orbit = size.minDimension * .27f
        val radius = size.minDimension * .19f
        repeat(spec.petalCount) { index ->
            val angle = (2 * PI * index / spec.petalCount) + spec.rotationFraction
            rotate(index * (360f / spec.petalCount)) {
                drawCircle(
                    second.copy(alpha = .72f),
                    radius,
                    Offset(center.x + cos(angle).toFloat() * orbit, center.y + sin(angle).toFloat() * orbit)
                )
            }
        }
        drawCircle(centerColor, size.minDimension * .15f)
    }
}

@Composable
private fun TelegramIdentitySummary(session: AuthenticationResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.bloom_telegram_connection), fontWeight = FontWeight.Bold)
            session.telegram.username?.let {
                Text(stringResource(R.string.bloom_connected_as, "@$it"))
            } ?: Text(stringResource(R.string.bloom_identity_connected))
            if (session.telegram.phoneVerified) Text(stringResource(R.string.bloom_phone_verified))
            Text(
                stringResource(R.string.bloom_identity_provider),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
        Text(stringResource(R.string.bloom_offline), Modifier.fillMaxWidth().padding(12.dp))
    }
}

@Composable
private fun intentLabel(value: ProfileIntent): String = stringResource(
    when (value) {
        ProfileIntent.BUILDING -> R.string.bloom_intent_building
        ProfileIntent.HELPING -> R.string.bloom_intent_helping
        ProfileIntent.EXPLORING -> R.string.bloom_intent_exploring
    }
)

@Composable
private fun intentPrompt(value: ProfileIntent): String = stringResource(
    when (value) {
        ProfileIntent.BUILDING -> R.string.bloom_prompt_building
        ProfileIntent.HELPING -> R.string.bloom_prompt_helping
        ProfileIntent.EXPLORING -> R.string.bloom_prompt_exploring
    }
)

@Composable
private fun topicLabel(value: ProfileTopic): String = stringResource(
    when (value) {
        ProfileTopic.ANDROID -> R.string.bloom_topic_android
        ProfileTopic.BACKEND -> R.string.bloom_topic_backend
        ProfileTopic.DESIGN -> R.string.bloom_topic_design
        ProfileTopic.SECURITY -> R.string.bloom_topic_security
        ProfileTopic.OPEN_SOURCE -> R.string.bloom_topic_open_source
        ProfileTopic.AI -> R.string.bloom_topic_ai
        ProfileTopic.PRODUCT -> R.string.bloom_topic_product
        ProfileTopic.TELEGRAM -> R.string.telegram
        ProfileTopic.OTHER -> R.string.bloom_topic_other
    }
)

private fun formatDate(value: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(requireNotNull(parser.parse(value)))
}.getOrDefault(value.substringBefore('T'))
