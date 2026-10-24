package studio.hypertext.curfew.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.*
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun CurfewWelcomeScreen(
    onContinue: () -> Unit,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    posture: Posture? = null,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val actualPosture = posture ?: adaptiveInfo.windowPosture
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val brandMotion = rememberWelcomeMotion(motionEnabled, 0)
    val copyMotion = rememberWelcomeMotion(motionEnabled, 45)
    val sunMotion = rememberWelcomeMotion(motionEnabled, 80)
    val actionMotion = rememberWelcomeMotion(motionEnabled, 120)
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { windowOrigin = it.positionInWindow() }) {
        val horizontalHinge = actualPosture.hingeList.firstOrNull { !it.isVertical && (it.isSeparating || it.isOccluding) }
        val paired = (maxWidth > maxHeight && maxWidth / density.fontScale.coerceAtLeast(1f) >= 600.dp) ||
            actualPosture.hingeList.any { it.isVertical && (it.isSeparating || it.isOccluding) }
        if (paired || horizontalHinge != null) {
            WelcomeLandscape(sunMotion, Modifier.matchParentSize(), ambient = true)
            if (horizontalHinge != null) {
                val heightPx = with(density) { maxHeight.toPx() }
                val above = (horizontalHinge.bounds.top - windowOrigin.y).coerceIn(0f, heightPx)
                val below = (horizontalHinge.bounds.bottom - windowOrigin.y).coerceIn(above, heightPx)
                // Material's horizontal pane scaffold avoids book hinges; tabletop needs its actual vertical regions.
                Column(Modifier.fillMaxSize()) {
                    WelcomeIntroduction(brandMotion, copyMotion, Modifier.fillMaxWidth().height(with(density) { above.toDp() }), centered = true)
                    Spacer(Modifier.height(with(density) { (below - above).toDp() }))
                    WelcomeActions(onContinue, copyMotion, actionMotion, Modifier.fillMaxWidth().weight(1f), centered = true)
                }
            } else {
                val directive = calculatePaneScaffoldDirective(
                    WindowAdaptiveInfo(adaptiveInfo.windowSizeClass, actualPosture), HingePolicy.AlwaysAvoid,
                ).copy(maxHorizontalPartitions = 2, horizontalPartitionSpacerSize = 24.dp, defaultPanePreferredWidth = (maxWidth - 24.dp) / 2)
                SupportingPaneScaffold(
                    directive = directive,
                    value = ThreePaneScaffoldValue(PaneAdaptedValue.Expanded, PaneAdaptedValue.Expanded, PaneAdaptedValue.Hidden),
                    mainPane = { AnimatedPane { WelcomeIntroduction(brandMotion, copyMotion, Modifier.fillMaxSize(), centered = true) } },
                    supportingPane = { AnimatedPane { WelcomeActions(onContinue, copyMotion, actionMotion, Modifier.fillMaxSize(), centered = true) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            val compositionHeight = minOf(maxHeight, 880.dp)
            val landscapeHeight = if (density.fontScale >= 1.5f) 64.dp else (maxHeight * .28f).coerceIn(112.dp, 256.dp)
            Column(
                Modifier.fillMaxSize().drawBehind { drawRect(colors.secondaryContainer, Offset(0f, size.height / 2), Size(size.width, size.height / 2)) }
                    .verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
            ) {
                Layout(
                    modifier = Modifier.fillMaxWidth().background(colors.surface),
                    content = {
                        WelcomeIntroduction(brandMotion, copyMotion, Modifier.fillMaxWidth())
                        Column {
                            WelcomeLandscape(sunMotion, Modifier.fillMaxWidth().height(landscapeHeight))
                            WelcomeActions(onContinue, copyMotion, actionMotion, Modifier.fillMaxWidth().background(colors.secondaryContainer))
                        }
                    },
                ) { children, constraints ->
                    // Measure the commitment first so enlarged text determines how much room remains.
                    val commitment = children[1].measure(constraints.copy(minHeight = 0))
                    val introduction = children[0].measure(constraints.copy(
                        minHeight = (compositionHeight.roundToPx() - commitment.height).coerceAtLeast(0),
                    ))
                    layout(constraints.maxWidth, introduction.height + commitment.height) {
                        introduction.placeRelative(0, 0)
                        commitment.placeRelative(0, introduction.height)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeIntroduction(brandMotion: WelcomeMotion, copyMotion: WelcomeMotion, modifier: Modifier, centered: Boolean = false) {
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(
        if (centered) WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical else WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )).testTag("welcome-introduction").semantics { isTraversalGroup = true }, contentAlignment = Alignment.Center) {
        val fontScale = LocalDensity.current.fontScale
        val gutter = if (maxWidth < 360.dp) 24.dp else 32.dp
        val readableWidth = (maxWidth - gutter * 2) / fontScale
        val titleStyle = when {
            readableWidth < 200.dp -> MaterialTheme.typography.headlineMedium
            readableWidth < 260.dp -> MaterialTheme.typography.headlineLarge
            readableWidth < 280.dp -> MaterialTheme.typography.displaySmall
            else -> MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp)
        }
        val brandGrowth = if (centered) minOf(
            ((readableWidth - 216.dp) / 200.dp).coerceIn(0f, 1f),
            ((maxHeight - 320.dp) / 240.dp).coerceIn(0f, 1f),
        ) else 0f
        val height = if (centered) maxHeight else 0.dp
        val scroll = if (centered) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            Modifier.widthIn(max = 584.dp).fillMaxWidth().then(scroll).heightIn(min = height).padding(horizontal = gutter, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.entrance(brandMotion, 6.dp), horizontalArrangement = Arrangement.spacedBy((10 + 4 * brandGrowth).dp), verticalAlignment = Alignment.CenterVertically) {
                if (readableWidth >= 160.dp) CurfewHorizonMark(Modifier.size((28 + 24 * brandGrowth).dp, (24 + 20 * brandGrowth).dp))
                Text("Curfew", style = MaterialTheme.typography.headlineSmall.copy(fontSize = (24 + 20 * brandGrowth).sp, lineHeight = (32 + 20 * brandGrowth).sp))
            }
            Column(Modifier.entrance(copyMotion, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your nights.\nYour mornings.", style = titleStyle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
            }
        }
    }
}

@Composable
private fun WelcomeActions(onContinue: () -> Unit, copyMotion: WelcomeMotion, motion: WelcomeMotion, modifier: Modifier, centered: Boolean = false) {
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(
        if (centered) WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical else WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
    )).testTag("welcome-actions").semantics { isTraversalGroup = true }, contentAlignment = Alignment.Center) {
        val gutter = if (maxWidth < 360.dp) 24.dp else 32.dp
        val height = if (centered) maxHeight else 0.dp
        val scroll = if (centered) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.widthIn(max = if (centered) 424.dp else 584.dp).fillMaxWidth().then(scroll).heightIn(min = height).navigationBarsPadding()
            .padding(horizontal = gutter, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Text("Wind down on time. Wake up when you planned.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).entrance(copyMotion, 16.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).entrance(motion, 8.dp)) { Text("Get started") }
        }
    }
}

@Composable
private fun WelcomeLandscape(motion: WelcomeMotion, modifier: Modifier = Modifier, ambient: Boolean = false) {
    val ground = MaterialTheme.colorScheme.secondaryContainer
    val distant = lerp(ground, MaterialTheme.colorScheme.secondary, .28f)
    Canvas(modifier.clipToBounds()) {
        val sunRadius = if (ambient) minOf(size.width * .12f, size.height * .14f) else minOf(size.width * .21f, size.height * .43f)
        val horizon = if (ambient) .94f else .64f
        val sunY = if (ambient) .84f else .46f
        drawCircle(Color(0xFFEE9157), sunRadius, Offset(size.width * (if (ambient) .90f else .78f), size.height * sunY + (1f - motion.position.value) * sunRadius * 1.4f))
        val farHorizon = Path().apply {
            moveTo(0f, size.height)
            cubicTo(size.width * .35f, size.height * .98f, size.width * .64f, size.height * (horizon - .16f), size.width, size.height * (horizon - .09f))
            lineTo(size.width, size.height)
            close()
        }
        drawPath(farHorizon, distant)
        drawOval(ground, topLeft = Offset(-size.width * .65f, size.height * horizon), size = Size(size.width * 2.3f, size.height * 1.45f))
    }
}

private class WelcomeMotion(
    val position: Animatable<Float, AnimationVector1D>,
    val opacity: Animatable<Float, AnimationVector1D>,
)

@Composable
private fun rememberWelcomeMotion(enabled: Boolean, delayMillis: Long): WelcomeMotion {
    val motion = remember(enabled) {
        WelcomeMotion(Animatable(if (enabled) 0f else 1f), Animatable(if (enabled) 0f else 1f))
    }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(enabled) {
        if (enabled) {
            delay(delayMillis)
            coroutineScope {
                launch { motion.position.animateTo(1f, spatial) }
                launch { motion.opacity.animateTo(1f, effects) }
            }
        }
    }
    return motion
}

private fun Modifier.entrance(motion: WelcomeMotion, distance: Dp): Modifier = graphicsLayer {
    alpha = motion.opacity.value.coerceIn(0f, 1f)
    translationY = (1f - motion.position.value) * distance.toPx()
}
