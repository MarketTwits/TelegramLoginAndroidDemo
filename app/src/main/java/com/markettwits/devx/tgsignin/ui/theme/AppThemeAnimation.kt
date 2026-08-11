package com.markettwits.devx.tgsignin.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.markettwits.devx.tgsignin.data.model.AppThemeMode
import kotlin.math.hypot

@Stable
class AppThemeAnimationState internal constructor(
    themeModes: StateFlow<AppThemeMode>,
    private val coroutineScope: CoroutineScope,
    internal val animationSpec: AnimationSpec<Float>,
    internal val useDynamicContent: Boolean
) {
    var uiMode by mutableStateOf(themeModes.value)
        private set

    var isAnimating by mutableStateOf(false)
        private set

    internal val recordStatus = MutableStateFlow(RecordStatus.Idle)
    internal var buttonPosition: Offset? by mutableStateOf(null)
        private set
    private var pendingMode: AppThemeMode? = null
    private var requestJob: Job? = null

    fun updateButtonPosition(bounds: Rect) {
        buttonPosition = bounds.center
    }

    fun animateTo(mode: AppThemeMode) {
        if (mode == uiMode || isAnimating) return
        pendingMode = mode
        requestJob?.cancel()
        requestJob = coroutineScope.launch {
            recordStatus.value = RecordStatus.Requested
            recordStatus.first { status -> status == RecordStatus.Recorded }
            uiMode = mode
            recordStatus.value = RecordStatus.Prepare
        }
    }

    init {
        coroutineScope.launch {
            themeModes.collectLatest { mode ->
                if (mode != pendingMode) uiMode = mode
            }
        }
        coroutineScope.launch {
            recordStatus.collect { status ->
                isAnimating = status != RecordStatus.Idle
                if (status == RecordStatus.Idle) pendingMode = null
            }
        }
    }
}

@Composable
fun rememberAppThemeAnimationState(
    themeModes: StateFlow<AppThemeMode>,
    animationSpec: AnimationSpec<Float> = tween(700),
    useDynamicContent: Boolean = true
): AppThemeAnimationState {
    val coroutineScope = rememberCoroutineScope()
    return remember(themeModes, coroutineScope, animationSpec, useDynamicContent) {
        AppThemeAnimationState(themeModes, coroutineScope, animationSpec, useDynamicContent)
    }
}

@Composable
fun AppThemeAnimationScope(
    state: AppThemeAnimationState,
    content: @Composable () -> Unit
) {
    val graphicsLayer = rememberGraphicsLayer()
    Box(
        modifier = Modifier.appThemeAnimation(
            state = state,
            mode = state.uiMode,
            graphicsLayer = graphicsLayer
        )
    ) {
        content()
    }
}

internal enum class RecordStatus {
    Idle,
    Requested,
    Recorded,
    Prepare
}

private fun Modifier.appThemeAnimation(
    state: AppThemeAnimationState,
    mode: AppThemeMode,
    graphicsLayer: GraphicsLayer
): Modifier = this then AppThemeAnimationElement(state, mode, graphicsLayer)

private data class AppThemeAnimationElement(
    val state: AppThemeAnimationState,
    val mode: AppThemeMode,
    val graphicsLayer: GraphicsLayer
) : ModifierNodeElement<AppThemeAnimationNode>() {
    override fun create() = AppThemeAnimationNode(state, mode, graphicsLayer)

    override fun InspectorInfo.inspectableProperties() {
        name = MODIFIER_NAME
        properties[PROPERTY_MODE] = mode
        properties[PROPERTY_DYNAMIC_CONTENT] = state.useDynamicContent
    }

    override fun update(node: AppThemeAnimationNode) {
        node.update(state, mode, graphicsLayer)
    }

    private companion object {
        const val MODIFIER_NAME = "appThemeAnimation"
        const val PROPERTY_MODE = "mode"
        const val PROPERTY_DYNAMIC_CONTENT = "useDynamicContent"
    }
}

private class AppThemeAnimationNode(
    private var state: AppThemeAnimationState,
    private var mode: AppThemeMode,
    private var graphicsLayer: GraphicsLayer
) : Modifier.Node(), DrawModifierNode {
    private var animationProgress = 0f
    private var previousImage: ImageBitmap? = null
    private var currentImage: ImageBitmap? = null
    private var phase = Phase.Idle
    private var recordJob: Job? = null
    private var animationJob: Job? = null

    private enum class Phase { Idle, Intercept, Animate }

    override fun onAttach() {
        observeRecordRequests()
    }

    private fun observeRecordRequests() {
        recordJob?.cancel()
        recordJob = coroutineScope.launch {
            state.recordStatus.collectLatest { status ->
                when (status) {
                    RecordStatus.Requested -> {
                        val image = graphicsLayer.toImageBitmap()
                        previousImage = image
                        currentImage = image
                        state.recordStatus.value = RecordStatus.Recorded
                    }
                    RecordStatus.Prepare -> phase = Phase.Intercept
                    RecordStatus.Idle, RecordStatus.Recorded -> Unit
                }
            }
        }
    }

    fun update(
        newState: AppThemeAnimationState,
        newMode: AppThemeMode,
        newGraphicsLayer: GraphicsLayer
    ) {
        graphicsLayer = newGraphicsLayer
        if (state != newState) {
            state = newState
            observeRecordRequests()
        }
        if (mode != newMode) {
            mode = newMode
            if (state.recordStatus.value == RecordStatus.Prepare || phase == Phase.Intercept) {
                runAnimation()
            } else {
                phase = Phase.Idle
                previousImage = null
                currentImage = null
                invalidateDraw()
            }
        }
    }

    private fun runAnimation() {
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            animationProgress = 0f
            phase = Phase.Animate
            previousImage = currentImage
            currentImage = graphicsLayer.toImageBitmap()
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = state.animationSpec
            ) { value, _ ->
                animationProgress = value
                invalidateDraw()
            }
            phase = Phase.Idle
            state.recordStatus.value = RecordStatus.Idle
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val old = previousImage
        val new = currentImage
        when (phase) {
            Phase.Intercept if old != null -> drawImage(old)
            Phase.Animate if old != null && new != null -> {
                drawImage(old)
                drawContractingTarget(
                    image = new,
                    progress = animationProgress,
                    center = state.buttonPosition ?: size.center,
                    useDynamicContent = state.useDynamicContent
                )
            }
            else -> {
                graphicsLayer.record { this@draw.drawContent() }
                drawLayer(graphicsLayer)
            }
        }
    }

    private fun ContentDrawScope.drawContractingTarget(
        image: ImageBitmap,
        progress: Float,
        center: Offset,
        useDynamicContent: Boolean
    ) {
        val farthestX = if (center.x < size.width / 2f) size.width else 0f
        val farthestY = if (center.y < size.height / 2f) size.height else 0f
        val radius = hypot(farthestX - center.x, farthestY - center.y) * (1f - progress)
        val circle = Path().apply { addOval(Rect(center = center, radius = radius)) }
        clipPath(path = circle, clipOp = ClipOp.Difference) {
            if (useDynamicContent) this@drawContractingTarget.drawContent() else drawImage(image)
        }
    }
}
