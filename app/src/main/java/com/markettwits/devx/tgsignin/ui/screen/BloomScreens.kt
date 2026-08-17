package com.markettwits.devx.tgsignin.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.markettwits.devx.tgsignin.R
import com.markettwits.devx.tgsignin.data.model.AuthenticationResult
import com.markettwits.devx.tgsignin.data.model.ProfileDraft
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiCatalog
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.ProfileIntent
import com.markettwits.devx.tgsignin.data.model.ProfileTopic
import com.markettwits.devx.tgsignin.data.model.TelegramIdentity
import com.markettwits.devx.tgsignin.data.model.asPhoneNumberInput
import com.markettwits.devx.tgsignin.data.model.formattedInternationalPhoneNumber
import com.markettwits.devx.tgsignin.data.model.isValidOptionalInternationalPhoneNumber
import com.markettwits.devx.tgsignin.data.model.normalizedInternationalPhoneNumberOrNull
import com.markettwits.devx.tgsignin.data.model.normalizedTelegramPhoneNumberOrNull
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepository
import com.markettwits.devx.tgsignin.ui.component.InternationalPhoneVisualTransformation
import com.markettwits.devx.tgsignin.ui.component.ProfileEmojiImage
import com.markettwits.devx.tgsignin.ui.component.TelegramChoice
import com.markettwits.devx.tgsignin.ui.component.TelegramConfirmationDialog
import com.markettwits.devx.tgsignin.ui.component.TelegramDestructiveButton
import com.markettwits.devx.tgsignin.ui.component.TelegramEmojiGroupChoice
import com.markettwits.devx.tgsignin.ui.component.TelegramEmojiSetDropdown
import com.markettwits.devx.tgsignin.ui.component.TelegramIconAction
import com.markettwits.devx.tgsignin.ui.component.TelegramPrimaryButton
import com.markettwits.devx.tgsignin.ui.component.TelegramSection
import com.markettwits.devx.tgsignin.ui.component.TelegramTextField
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private const val SETUP_PAGE_COUNT = 4
private const val EMOJI_SET_PREFETCH_COUNT = 20

@Composable
fun ProfileSetupScreen(
    session: AuthenticationResult,
    draft: ProfileDraft,
    isSaving: Boolean,
    isOffline: Boolean,
    onDraftChanged: (ProfileDraft) -> Unit,
    onSave: (ProfileDraft) -> Unit,
    onCancel: () -> Unit
) {
    val emojiRepository: ProfileEmojiRepository = koinInject()
    val emojiCatalog by emojiRepository.catalog.collectAsState()
    val isEditing = session.profile != null
    val pagerState = rememberPagerState(pageCount = { SETUP_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var showPageError by rememberSaveable { mutableStateOf(false) }
    var topicLimitReached by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { showPageError = false }

    fun goBack() {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else if (isEditing) {
            onCancel()
        }
    }

    BackHandler(enabled = !isSaving && (isEditing || pagerState.currentPage > 0), onBack = ::goBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(top = 58.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        if (isEditing) R.string.bloom_edit_title else R.string.bloom_setup_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(10.dp))
                SetupProgress(currentPage = pagerState.currentPage)
                if (isOffline) {
                    OfflineBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (page) {
                            0 -> NameSetupPage(
                                session = session,
                                draft = draft,
                                showError = showPageError,
                                onDraftChanged = onDraftChanged
                            )
                            1 -> SignalSetupPage(
                                draft = draft,
                                showError = showPageError,
                                onDraftChanged = onDraftChanged
                            )
                            2 -> TopicsSetupPage(
                                draft = draft,
                                showError = showPageError,
                                topicLimitReached = topicLimitReached,
                                onTopicLimitChanged = { topicLimitReached = it },
                                onDraftChanged = onDraftChanged
                            )
                            else -> BloomSetupPage(
                                draft = draft,
                                catalog = emojiCatalog,
                                onEmojiSelected = { selection ->
                                    emojiRepository.recordRecent(selection)
                                    onDraftChanged(draft.copy(emojiStatus = selection))
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pagerState.currentPage > 0) {
                        TelegramPrimaryButton(
                            text = stringResource(R.string.back),
                            onClick = ::goBack,
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f),
                            secondary = true
                        )
                    }
                    TelegramPrimaryButton(
                        text = stringResource(
                            if (pagerState.currentPage == SETUP_PAGE_COUNT - 1) {
                                if (isEditing) R.string.bloom_save_profile
                                else R.string.bloom_create_profile
                            } else {
                                R.string.bloom_next
                            }
                        ),
                        onClick = {
                            if (!isSetupPageValid(pagerState.currentPage, draft)) {
                                showPageError = true
                            } else if (pagerState.currentPage == SETUP_PAGE_COUNT - 1) {
                                onSave(draft)
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        content = if (isSaving) {
                            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else null
                    )
                }
            }

            if (isEditing || pagerState.currentPage > 0) {
                TelegramIconAction(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = ::goBack,
                    enabled = !isSaving,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 6.dp, start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupProgress(currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(SETUP_PAGE_COUNT) { page ->
            Surface(
                modifier = Modifier.size(if (page == currentPage) 9.dp else 7.dp),
                shape = CircleShape,
                color = if (page <= currentPage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ) {}
        }
    }
}

@Composable
private fun NameSetupPage(
    session: AuthenticationResult,
    draft: ProfileDraft,
    showError: Boolean,
    onDraftChanged: (ProfileDraft) -> Unit
) {
    SetupPageHeader(R.string.bloom_step_name_title, R.string.bloom_step_name_subtitle)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TelegramAvatar(session.telegram, Modifier.size(104.dp))
    }
    TelegramTextField(
        value = draft.displayName,
        onValueChange = { if (it.length <= 80) onDraftChanged(draft.copy(displayName = it)) },
        label = stringResource(R.string.bloom_display_name),
        supportingText = if (showError && draft.displayName.isBlank()) {
            stringResource(R.string.required_field)
        } else null,
        isError = showError && draft.displayName.isBlank(),
        singleLine = true
    )
    val invalidPhone = !draft.phoneNumber.isValidOptionalInternationalPhoneNumber()
    TelegramTextField(
        value = draft.phoneNumber,
        onValueChange = {
            onDraftChanged(
                draft.copy(phoneNumber = it.asPhoneNumberInput(), phoneNumberEdited = true)
            )
        },
        label = stringResource(R.string.bloom_phone_optional),
        supportingText = stringResource(
            if (showError && invalidPhone) R.string.bloom_phone_invalid else R.string.bloom_phone_hint
        ),
        isError = showError && invalidPhone,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        visualTransformation = InternationalPhoneVisualTransformation
    )
}

@Composable
private fun SignalSetupPage(
    draft: ProfileDraft,
    showError: Boolean,
    onDraftChanged: (ProfileDraft) -> Unit
) {
    SetupPageHeader(R.string.bloom_step_signal_title, R.string.bloom_step_signal_subtitle)
    TelegramSection {
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
        TelegramTextField(
            value = draft.headline,
            onValueChange = { if (it.length <= 120) onDraftChanged(draft.copy(headline = it)) },
            label = intentPrompt(draft.intent),
            supportingText = when {
                showError && draft.headline.isBlank() -> stringResource(R.string.required_field)
                draft.headline.length >= 100 -> "${draft.headline.length}/120"
                else -> null
            },
            isError = showError && draft.headline.isBlank(),
            minLines = 2,
            maxLines = 3
        )
    }
}

@Composable
private fun TopicsSetupPage(
    draft: ProfileDraft,
    showError: Boolean,
    topicLimitReached: Boolean,
    onTopicLimitChanged: (Boolean) -> Unit,
    onDraftChanged: (ProfileDraft) -> Unit
) {
    SetupPageHeader(R.string.bloom_step_topics_title, R.string.bloom_topics_hint)
    TelegramSection {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileTopic.entries.forEach { topic ->
                val selected = topic in draft.topics
                TelegramChoice(
                    selected = selected,
                    onClick = {
                        val topics = when {
                            selected -> (draft.topics - topic).also { onTopicLimitChanged(false) }
                            draft.topics.size < 3 -> (draft.topics + topic).also {
                                onTopicLimitChanged(false)
                            }
                            else -> draft.topics.also { onTopicLimitChanged(true) }
                        }
                        onDraftChanged(draft.copy(topics = topics))
                    }
                ) {
                    Text(topicLabel(topic), Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
        if (topicLimitReached || (showError && draft.topics.isEmpty())) {
            Text(
                stringResource(
                    if (topicLimitReached) R.string.bloom_topics_limit
                    else R.string.bloom_topics_required
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun BloomSetupPage(
    draft: ProfileDraft,
    catalog: ProfileEmojiCatalog?,
    onEmojiSelected: (ProfileEmojiSelection) -> Unit
) {
    val language = LocalConfiguration.current.locales[0].language
    val emojiRepository: ProfileEmojiRepository = koinInject()
    SetupPageHeader(R.string.bloom_step_emoji_title, R.string.bloom_step_emoji_subtitle)
    TelegramSection {
        if (catalog == null) {
            Box(Modifier
                .fillMaxWidth()
                .height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@TelegramSection
        }
        val selected = catalog.resolve(draft.emojiStatus)
        var activeSetId by rememberSaveable(catalog.version) { mutableStateOf(selected.setId) }
        val activeSet = catalog.sets.firstOrNull { it.id == activeSetId } ?: catalog.sets.first()
        LaunchedEffect(activeSet.id) {
            emojiRepository.prefetch(activeSet.emojis.take(EMOJI_SET_PREFETCH_COUNT))
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(catalog.sets, key = { it.id }) { set ->
                val thumbnail = set.emojis.firstOrNull { it.id == set.thumbnailEmojiId }
                TelegramEmojiGroupChoice(
                    selected = set.id == activeSet.id,
                    label = set.label(language),
                    onClick = { activeSetId = set.id },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    ProfileEmojiImage(
                        emoji = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        val enabledEmojis = remember(activeSet) { activeSet.emojis.filter { it.enabled } }
        LazyHorizontalGrid(
            rows = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = enabledEmojis,
                key = { _, emoji -> "${emoji.setId}/${emoji.id}" }
            ) { index, emoji ->
                val selection = ProfileEmojiSelection(emoji.setId, emoji.id)
                TelegramChoice(
                    selected = selection == selected,
                    onClick = { onEmojiSelected(selection) }
                ) {
                    ProfileEmojiImage(
                        emoji = emoji,
                        contentDescription = "${activeSet.label(language)} ${index + 1}",
                        modifier = Modifier
                            .padding(6.dp)
                            .size(38.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(draft.displayName, style = MaterialTheme.typography.titleLarge)
            ProfileEmojiImage(
                emoji = catalog.emoji(selected),
                modifier = Modifier
                    .padding(start = 7.dp)
                    .size(32.dp)
                    .clip(CircleShape)
            )
        }
        Text(
            draft.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SetupPageHeader(title: Int, subtitle: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun isSetupPageValid(page: Int, draft: ProfileDraft): Boolean = when (page) {
    0 -> draft.displayName.isNotBlank() && draft.displayName.trim().length <= 80 &&
            draft.phoneNumber.isValidOptionalInternationalPhoneNumber()
    1 -> draft.headline.isNotBlank() && draft.headline.trim().length <= 120
    2 -> draft.topics.size in 1..3
    else -> draft.isValid
}

@Composable
fun BloomProfileScreen(
    session: AuthenticationResult,
    isOffline: Boolean,
    onEmojiChanged: (ProfileEmojiSelection) -> Unit,
    onDelete: () -> Unit
) {
    val emojiRepository: ProfileEmojiRepository = koinInject()
    val emojiCatalog by emojiRepository.catalog.collectAsState()
    val recentEmojiSelections by emojiRepository.recentSelections.collectAsState()
    val profile = requireNotNull(session.profile)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
    var initialCollapseApplied by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val collapseRangePx = with(LocalDensity.current) {
        (BLOOM_HERO_EXPANDED_HEIGHT - BLOOM_HERO_COLLAPSED_HEIGHT).toPx()
    }
    val collapsedScrollPosition = collapseRangePx.roundToInt()
    val collapseProgress by remember(scrollState, collapseRangePx) {
        derivedStateOf {
            if (initialCollapseApplied) {
                (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }
    var lastHeroBoundary by remember { mutableStateOf<Int?>(null) }
    val settleHeader: () -> Unit = {
        scope.launch {
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
                .first { it >= collapsedScrollPosition }
            scrollState.scrollTo(collapsedScrollPosition)
            initialCollapseApplied = true
        }
    }
    LaunchedEffect(collapseProgress, initialCollapseApplied) {
        if (!initialCollapseApplied) return@LaunchedEffect
        val boundary = when {
            collapseProgress <= 0.05f -> 0
            collapseProgress >= 0.95f -> 1
            else -> null
        }
        if (boundary != null) {
            if (lastHeroBoundary != null && boundary != lastHeroBoundary) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHeroBoundary = boundary
        }
    }
    LaunchedEffect(scrollState, collapsedScrollPosition) {
        snapshotFlow { scrollState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling && scrollState.value in 1 until collapsedScrollPosition) {
                    settleHeader()
                }
            }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(BLOOM_HERO_EXPANDED_HEIGHT))
                if (isOffline) OfflineBanner()

                TelegramSection(title = stringResource(R.string.bloom_current_signal)) {
                    Text(profile.headline, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.topics.forEach { topic ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
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
                TelegramIdentitySummary(session, profile.phoneNumber)
                TelegramDestructiveButton(
                    text = stringResource(R.string.bloom_delete_account),
                    onClick = { confirmDelete = true },
                    icon = Icons.Outlined.DeleteOutline
                )
                Spacer(
                    Modifier.height(
                        28.dp + (BLOOM_HERO_EXPANDED_HEIGHT - BLOOM_HERO_COLLAPSED_HEIGHT)
                    )
                )
            }

            BloomProfileHero(
                identity = session.telegram,
                displayName = profile.displayName,
                emojiStatus = emojiCatalog?.resolve(profile.emojiStatus) ?: profile.emojiStatus,
                emojiCatalog = emojiCatalog,
                recentEmojiSelections = recentEmojiSelections,
                collapseProgress = collapseProgress,
                scrollState = scrollState,
                onDragStopped = settleHeader,
                onToggle = {
                    scope.launch {
                        val target = if (collapseProgress < 0.5f) collapsedScrollPosition else 0
                        scrollState.animateScrollTo(target, tween(durationMillis = 420))
                    }
                },
                onEmojiClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (emojiCatalog != null) showEmojiPicker = !showEmojiPicker
                },
                emojiMenuExpanded = showEmojiPicker,
                onEmojiMenuDismiss = { showEmojiPicker = false },
                onEmojiSelected = { selection ->
                    showEmojiPicker = false
                    emojiRepository.recordRecent(selection)
                    if (selection != profile.emojiStatus) onEmojiChanged(selection)
                }
            )
        }
    }

    if (confirmDelete) TelegramConfirmationDialog(
        title = stringResource(R.string.bloom_delete_title),
        message = stringResource(R.string.bloom_delete_confirmation),
        confirmText = stringResource(R.string.bloom_delete_account),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { confirmDelete = false; onDelete() },
        onDismiss = { confirmDelete = false },
        destructive = true
    )
}

@Composable
private fun BloomProfileHero(
    identity: TelegramIdentity,
    displayName: String,
    emojiStatus: ProfileEmojiSelection,
    emojiCatalog: ProfileEmojiCatalog?,
    recentEmojiSelections: List<ProfileEmojiSelection>,
    collapseProgress: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    onDragStopped: () -> Unit,
    onToggle: () -> Unit,
    onEmojiClick: () -> Unit,
    emojiMenuExpanded: Boolean,
    onEmojiMenuDismiss: () -> Unit,
    onEmojiSelected: (ProfileEmojiSelection) -> Unit
) {
    val collapseDescription = stringResource(R.string.collapse_profile_photo)
    val expandDescription = stringResource(R.string.expand_profile_photo)
    val height = BLOOM_HERO_EXPANDED_HEIGHT -
        ((BLOOM_HERO_EXPANDED_HEIGHT - BLOOM_HERO_COLLAPSED_HEIGHT) * collapseProgress)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
    ) {
        BloomBlurredAvatarBackground(identity)
        if (identity.pictureUrl != null) {
            val progress = collapseProgress.coerceIn(0f, 1f)
            val avatarWidth = maxWidth - ((maxWidth - BLOOM_HERO_AVATAR_SIZE) * progress)
            val avatarHeight = BLOOM_HERO_EXPANDED_HEIGHT -
                    ((BLOOM_HERO_EXPANDED_HEIGHT - BLOOM_HERO_AVATAR_SIZE) * progress)
            val avatarShape = RoundedCornerShape(percent = (50f * progress).roundToInt())

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(avatarWidth, avatarHeight)
                    .clip(avatarShape)
            ) {
                BloomRemoteAvatar(
                    identity = identity,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    shimmer = true
                )
                BloomAvatarVerticalEdgeBlur(
                    identity = identity,
                    expandedFraction = 1f - progress
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(BLOOM_HERO_AVATAR_SIZE)
                    .graphicsLayer { alpha = collapseProgress },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                BloomAvatarPlaceholder(
                    identity = identity,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    shimmer = false
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.04f + 0.06f * collapseProgress),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
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
                .semantics {
                    role = Role.Button
                    contentDescription = if (collapseProgress < 0.5f) {
                        collapseDescription
                    } else {
                        expandDescription
                    }
                    onClick { onToggle(); true }
                }
        )

        BloomProfileIdentity(
            displayName = displayName,
            username = identity.username,
            emojiStatus = emojiStatus,
            emojiCatalog = emojiCatalog,
            recentEmojiSelections = recentEmojiSelections,
            collapseProgress = collapseProgress,
            onEmojiClick = onEmojiClick,
            emojiMenuExpanded = emojiMenuExpanded,
            onEmojiMenuDismiss = onEmojiMenuDismiss,
            onEmojiSelected = onEmojiSelected,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun BloomProfileIdentity(
    displayName: String,
    username: String?,
    emojiStatus: ProfileEmojiSelection,
    emojiCatalog: ProfileEmojiCatalog?,
    recentEmojiSelections: List<ProfileEmojiSelection>,
    collapseProgress: Float,
    onEmojiClick: () -> Unit,
    emojiMenuExpanded: Boolean,
    onEmojiMenuDismiss: () -> Unit,
    onEmojiSelected: (ProfileEmojiSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val startInset = with(density) { 24.dp.roundToPx() }
    val badgeGap = with(density) { 7.dp.roundToPx() }
    val usernameGap = with(density) { 4.dp.roundToPx() }

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Text(
                text = displayName,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                Surface(
                    onClick = onEmojiClick,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.28f)
                ) {
                    ProfileEmojiImage(
                        emoji = emojiCatalog?.emoji(emojiStatus),
                        contentDescription = stringResource(R.string.bloom_step_emoji_title),
                        modifier = Modifier
                            .padding(4.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                    )
                }
                emojiCatalog?.let { catalog ->
                    TelegramEmojiSetDropdown(
                        expanded = emojiMenuExpanded,
                        catalog = catalog,
                        selectedEmoji = emojiStatus,
                        recentSelections = recentEmojiSelections,
                        onEmojiSelected = onEmojiSelected,
                        onDismiss = onEmojiMenuDismiss
                    )
                }
            }
            username?.let {
                Text(
                    text = "@$it",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) { measurables, constraints ->
        val badge = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val centeredNameWidth = constraints.maxWidth - (badge.width + badgeGap) * 2
        val expandedNameWidth = constraints.maxWidth - startInset * 2 - badge.width - badgeGap
        val nameMaxWidth = minOf(centeredNameWidth, expandedNameWidth).coerceAtLeast(0)
        val name = measurables[0].measure(
            constraints.copy(minWidth = 0, maxWidth = nameMaxWidth, minHeight = 0)
        )
        val usernamePlaceable = measurables.getOrNull(2)?.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = (constraints.maxWidth - startInset * 2).coerceAtLeast(0),
                minHeight = 0
            )
        )
        val rowHeight = maxOf(name.height, badge.height)
        val contentHeight = rowHeight + if (usernamePlaceable == null) {
            0
        } else {
            usernameGap + usernamePlaceable.height
        }
        val progress = collapseProgress.coerceIn(0f, 1f)
        val collapsedNameX = (constraints.maxWidth - name.width) / 2
        val nameX = (startInset + (collapsedNameX - startInset) * progress).roundToInt()
        val expandedUsernameX = usernamePlaceable?.let {
            startInset + (name.width - it.width) / 2
        } ?: 0
        val collapsedUsernameX = usernamePlaceable?.let {
            (constraints.maxWidth - it.width) / 2
        } ?: 0
        val usernameX = (expandedUsernameX +
            (collapsedUsernameX - expandedUsernameX) * progress).roundToInt()

        layout(constraints.maxWidth, contentHeight) {
            name.placeRelative(nameX, (rowHeight - name.height) / 2)
            badge.placeRelative(nameX + name.width + badgeGap, (rowHeight - badge.height) / 2)
            usernamePlaceable?.placeRelative(
                usernameX.coerceIn(0, constraints.maxWidth - usernamePlaceable.width),
                rowHeight + usernameGap
            )
        }
    }
}

@Composable
private fun BloomAvatarVerticalEdgeBlur(
    identity: TelegramIdentity,
    expandedFraction: Float
) {
    val progress = expandedFraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black,
                            0.1f to Color.Transparent,
                            0.58f to Color.Transparent,
                            0.82f to Color.Black.copy(alpha = 0.76f),
                            1f to Color.Black
                        )
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        BloomRemoteAvatar(
            identity = identity,
            modifier = Modifier
                .fillMaxSize()
                .blur(BLOOM_AVATAR_EDGE_BLUR_RADIUS * progress),
            contentScale = ContentScale.Crop,
            shimmer = false
        )
    }
}

@Composable
private fun BloomBlurredAvatarBackground(identity: TelegramIdentity) {
    if (identity.pictureUrl != null) {
        BloomRemoteAvatar(
            identity = identity,
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
private fun BloomRemoteAvatar(
    identity: TelegramIdentity,
    modifier: Modifier,
    contentScale: ContentScale,
    shimmer: Boolean
) {
    val pictureUrl = identity.pictureUrl
    if (pictureUrl == null) {
        BloomAvatarPlaceholder(identity, modifier, false)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(pictureUrl).crossfade(true).build(),
        contentDescription = stringResource(R.string.user_avatar),
        contentScale = contentScale,
        modifier = modifier,
        loading = { BloomAvatarPlaceholder(identity, Modifier.fillMaxSize(), shimmer) },
        error = { BloomAvatarPlaceholder(identity, Modifier.fillMaxSize(), false) }
    )
}

@Composable
private fun BloomAvatarPlaceholder(
    identity: TelegramIdentity,
    modifier: Modifier,
    shimmer: Boolean
) {
    val transition = rememberInfiniteTransition(label = "bloomAvatarShimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_250, easing = LinearEasing)
        ),
        label = "bloomAvatarShimmerOffset"
    )
    val brush = if (shimmer) {
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
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
    }
    Box(modifier.background(brush), contentAlignment = Alignment.Center) {
        Text(
            identity.suggestedDisplayName().trim().split(Regex("\\s+")).take(2)
                .joinToString("") { it.take(1).uppercase() },
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TelegramAvatar(identity: TelegramIdentity, modifier: Modifier = Modifier) {
    val pictureUrl = identity.pictureUrl
    Surface(modifier = modifier.clip(CircleShape), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        if (pictureUrl == null) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    identity.suggestedDisplayName().take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(pictureUrl).crossfade(true).build(),
                contentDescription = stringResource(R.string.user_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { CircularProgressIndicator(Modifier
                    .align(Alignment.Center)
                    .size(24.dp), strokeWidth = 2.dp) },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            identity.suggestedDisplayName().take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun TelegramIdentitySummary(session: AuthenticationResult, profilePhoneNumber: String?) {
    profilePhoneNumber?.let { phoneNumber ->
        val isTelegramNumber = phoneNumber.normalizedInternationalPhoneNumberOrNull() ==
                session.telegram.phoneNumber?.normalizedTelegramPhoneNumberOrNull()
        TelegramSection(title = stringResource(R.string.bloom_contact)) {
            Text(
                phoneNumber.formattedInternationalPhoneNumber(),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    if (session.telegram.phoneVerified && isTelegramNumber) {
                        R.string.bloom_phone_verified
                    } else {
                        R.string.bloom_phone_profile
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    TelegramSection(title = stringResource(R.string.bloom_telegram_connection)) {
        Text(
            session.telegram.username?.let { stringResource(R.string.bloom_connected_as, "@$it") }
                ?: stringResource(R.string.bloom_identity_connected),
            fontWeight = FontWeight.Medium
        )
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
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(stringResource(R.string.bloom_offline), Modifier
            .fillMaxWidth()
            .padding(12.dp))
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

private val BLOOM_HERO_EXPANDED_HEIGHT = 500.dp
private val BLOOM_HERO_COLLAPSED_HEIGHT = 280.dp
private val BLOOM_HERO_AVATAR_SIZE = 112.dp
private val BLOOM_AVATAR_EDGE_BLUR_RADIUS = 28.dp
