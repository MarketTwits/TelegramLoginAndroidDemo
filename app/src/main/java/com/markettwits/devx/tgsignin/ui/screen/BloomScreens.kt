package com.markettwits.devx.tgsignin.ui.screen

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.AvatarSource
import com.markettwits.devx.tgsignin.data.model.PROFILE_EMOJIS
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.model.ProfileTopic
import com.markettwits.devx.tgsignin.ui.component.TelegramChoice
import com.markettwits.devx.tgsignin.ui.component.TelegramPrimaryButton
import com.markettwits.devx.tgsignin.ui.component.TelegramSection
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    LaunchedEffect(draft.avatarSource) {
        if (draft.avatarSource != AvatarSource.BLOOM) {
            onDraftChanged(draft.copy(avatarSource = AvatarSource.BLOOM))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(42.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.bloom_edit_title else R.string.bloom_setup_title
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.bloom_edit_subtitle else R.string.bloom_setup_subtitle
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isEditing) TextButton(onClick = onCancel, enabled = !isSaving) {
                    Text(stringResource(R.string.cancel))
                }
            }
            if (isOffline) OfflineBanner()

            TelegramSection(title = stringResource(R.string.bloom_avatar_title)) {
                Text(
                    stringResource(R.string.bloom_emoji_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PROFILE_EMOJIS.forEach { emoji ->
                        TelegramChoice(
                            selected = draft.emoji == emoji,
                            onClick = {
                                onDraftChanged(
                                    draft.copy(avatarSource = AvatarSource.BLOOM, emoji = emoji)
                                )
                            }
                        ) {
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            TelegramSection(title = stringResource(R.string.bloom_about_you)) {
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
                    isError = displayNameTouched && draft.displayName.isBlank(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.bloom_intent_title),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileIntent.entries.forEach { intent ->
                        TelegramChoice(
                            selected = draft.intent == intent,
                            onClick = { onDraftChanged(draft.copy(intent = intent)) }
                        ) {
                            Text(intentLabel(intent), Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                        }
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
                        draft.headline.length >= 100 -> ({ Text("${draft.headline.length}/120") })
                        else -> null
                    },
                    isError = headlineTouched && draft.headline.isBlank(),
                    minLines = 2,
                    maxLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TelegramSection(title = stringResource(R.string.bloom_topics_title)) {
                Text(
                    stringResource(R.string.bloom_topics_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileTopic.entries.forEach { topic ->
                        val selected = topic in draft.topics
                        TelegramChoice(
                            selected = selected,
                            onClick = {
                                val topics = if (selected) {
                                    topicLimitReached = false
                                    draft.topics - topic
                                } else if (draft.topics.size < 3) {
                                    topicLimitReached = false
                                    draft.topics + topic
                                } else {
                                    topicLimitReached = true
                                    draft.topics
                                }
                                onDraftChanged(draft.copy(topics = topics))
                            }
                        ) {
                            Text(topicLabel(topic), Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                    }
                }
                if (topicLimitReached) Text(
                    stringResource(R.string.bloom_topics_limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            TelegramIdentitySummary(session)
            TelegramPrimaryButton(
                text = stringResource(
                    if (isEditing) R.string.bloom_save_profile else R.string.bloom_create_profile
                ),
                onClick = { onSave(draft) },
                enabled = draft.isValid && !isSaving,
                content = if (isSaving) {
                    { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else null
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProfileWelcomeScreen(session: AuthenticationResult, onComplete: () -> Unit) {
    val profile = requireNotNull(session.profile)
    val pages = listOf(
        WelcomePage(profile.emoji, R.string.bloom_welcome_ready, R.string.bloom_welcome_ready_text),
        WelcomePage("✅", R.string.bloom_welcome_identity, R.string.bloom_welcome_identity_text),
        WelcomePage("💬", R.string.bloom_welcome_signal, R.string.bloom_welcome_signal_text)
    )
    val pagerState = rememberPagerState(pageCount = pages::size)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                EmojiAvatar(page.emoji, Modifier.size(132.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            pages.indices.forEach { index ->
                Surface(
                    modifier = Modifier.size(if (index == pagerState.currentPage) 9.dp else 7.dp),
                    shape = CircleShape,
                    color = if (index == pagerState.currentPage) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ) {}
            }
        }
        Spacer(Modifier.height(24.dp))
        TelegramPrimaryButton(
            text = stringResource(
                if (pagerState.currentPage == pages.lastIndex) R.string.bloom_welcome_start
                else R.string.bloom_welcome_next
            ),
            onClick = {
                if (pagerState.currentPage == pages.lastIndex) onComplete()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        )
    }
}

@Composable
fun BloomProfileScreen(
    session: AuthenticationResult,
    isOffline: Boolean,
    messages: Flow<Int>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLogout: () -> Unit
) {
    val profile = requireNotNull(session.profile)
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(messages) {
        messages.collectLatest { snackbar.showSnackbar(resources.getString(it)) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(42.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, stringResource(R.string.bloom_edit_profile))
                }
                IconButton(onClick = { confirmLogout = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.logout_account_description))
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EmojiAvatar(profile.emoji, Modifier.size(104.dp))
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                session.telegram.username?.let {
                    Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(intentLabel(profile.intent), color = MaterialTheme.colorScheme.primary)
            }
            if (isOffline) OfflineBanner()

            TelegramSection(title = stringResource(R.string.bloom_current_signal)) {
                Text(profile.headline, style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.topics.forEach { topic ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(topicLabel(topic), Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            TelegramSection(title = stringResource(R.string.bloom_membership)) {
                ProfileValue(stringResource(R.string.bloom_member_number, session.account.memberNumber))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ProfileValue(stringResource(R.string.bloom_joined, formatDate(session.account.registeredAt)))
                ProfileValue(stringResource(R.string.bloom_last_sign_in, formatDate(session.account.lastLoginAt)))
                ProfileValue(pluralStringResource(
                    R.plurals.bloom_login_count,
                    session.account.loginCount,
                    session.account.loginCount
                ))
            }
            TelegramIdentitySummary(session)
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.bloom_delete_profile),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.bloom_delete_title)) },
        text = { Text(stringResource(R.string.bloom_delete_confirmation)) },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; onDelete() }) {
                Text(stringResource(R.string.bloom_delete_profile), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
    if (confirmLogout) AlertDialog(
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

@Composable
private fun EmojiAvatar(emoji: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, style = MaterialTheme.typography.displayMedium)
        }
    }
}

@Composable
private fun TelegramIdentitySummary(session: AuthenticationResult) {
    TelegramSection(title = stringResource(R.string.bloom_telegram_connection)) {
        Text(
            session.telegram.username?.let {
                stringResource(R.string.bloom_connected_as, "@$it")
            } ?: stringResource(R.string.bloom_identity_connected),
            fontWeight = FontWeight.Medium
        )
        if (session.telegram.phoneVerified) {
            Text(stringResource(R.string.bloom_phone_verified))
        }
        Text(
            stringResource(R.string.bloom_identity_provider),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileValue(value: String) {
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
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

private data class WelcomePage(val emoji: String, val title: Int, val body: Int)

private fun formatDate(value: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(requireNotNull(parser.parse(value)))
}.getOrDefault(value.substringBefore('T'))
