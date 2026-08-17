package com.markettwits.devx.tgsignin.ui.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiCatalog
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.model.formattedPhoneNumberAsYouType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TelegramIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        ),
        modifier = modifier.shadow(3.dp, CircleShape)
    ) {
        Icon(icon, contentDescription)
    }
}

@Composable
fun TelegramSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun TelegramChoice(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        content()
    }
}

@Composable
fun TelegramTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { message -> ({ Text(message) }) },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}

object InternationalPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val formatted = original.formattedPhoneNumberAsYouType()
        val originalToTransformed = IntArray(original.length + 1)
        var formattedCursor = 0
        original.forEachIndexed { index, character ->
            while (formattedCursor < formatted.length && formatted[formattedCursor] != character) {
                formattedCursor++
            }
            if (formattedCursor < formatted.length) formattedCursor++
            originalToTransformed[index + 1] = formattedCursor
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, original.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val transformedOffset = offset.coerceIn(0, formatted.length)
                return originalToTransformed.indexOfLast { it <= transformedOffset }
                    .coerceAtLeast(0)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@Composable
fun TelegramPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = if (secondary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        }
    ) {
        if (content != null) content() else Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TelegramDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val haptics = LocalHapticFeedback.current
    TextButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.error) }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = if (icon == null) 0.dp else 8.dp)
        )
    }
}

@Composable
fun TelegramConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    }) { Text(dismissText) }
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onConfirm()
                    }) {
                        Text(
                            confirmText,
                            color = if (destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelegramDialog(
    title: String,
    actionText: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(actionText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun TelegramEmojiSetDropdown(
    expanded: Boolean,
    catalog: ProfileEmojiCatalog,
    selectedEmoji: ProfileEmojiSelection,
    onEmojiSelected: (ProfileEmojiSelection) -> Unit,
    onDismiss: () -> Unit
) {
    val language = LocalConfiguration.current.locales[0].language
    var activeSetId by remember { mutableStateOf(selectedEmoji.setId) }
    val activeSet = catalog.sets.firstOrNull { it.id == activeSetId } ?: catalog.sets.first()
    val enabledEmojis = remember(activeSet) { activeSet.emojis.filter { it.enabled } }
    var visibleCount by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(expanded, selectedEmoji, catalog) {
        if (expanded && catalog.sets.any { it.id == selectedEmoji.setId }) {
            activeSetId = selectedEmoji.setId
        }
    }
    LaunchedEffect(expanded, activeSetId, enabledEmojis) {
        visibleCount = 0
        if (expanded) {
            repeat(minOf(enabledEmojis.size, 15)) { index ->
                delay((if (index == 0) 20 else 28).milliseconds)
                visibleCount = index + 1
            }
            visibleCount = enabledEmojis.size
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.width(332.dp)
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(catalog.sets, key = { it.id }) { set ->
                        val thumbnail = set.emojis.firstOrNull { it.id == set.thumbnailEmojiId }
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeSetId = set.id
                            },
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = if (set.id == activeSet.id) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                ProfileEmojiImage(
                                    emoji = thumbnail,
                                    contentDescription = set.label(language),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.heightIn(min = 112.dp, max = 224.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = enabledEmojis,
                        key = { _, emoji -> "${emoji.setId}/${emoji.id}" }
                    ) { index, emoji ->
                        Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = index < visibleCount,
                                enter = fadeIn(tween(140)) + scaleIn(
                                    initialScale = 0.72f,
                                    animationSpec = tween(180)
                                ) + slideInHorizontally(
                                    animationSpec = tween(180),
                                    initialOffsetX = { it / 2 }
                                )
                            ) {
                                val selection = ProfileEmojiSelection(emoji.setId, emoji.id)
                                Surface(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onEmojiSelected(selection)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    shape = CircleShape,
                                    color = if (selection == selectedEmoji) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                    contentColor = if (selection == selectedEmoji) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        ProfileEmojiImage(
                                            emoji = emoji,
                                            animate = selection == selectedEmoji,
                                            contentDescription = "${activeSet.label(language)} ${index + 1}",
                                            modifier = Modifier.size(38.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
