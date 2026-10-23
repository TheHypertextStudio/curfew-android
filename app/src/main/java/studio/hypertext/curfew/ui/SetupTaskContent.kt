package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.*
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SetupTaskContent(posture: Posture? = null, introduction: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val adaptive = currentWindowAdaptiveInfoV2()
    val density = LocalDensity.current
    val actualPosture = posture ?: adaptive.windowPosture
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().onGloballyPositioned { origin = it.positionInWindow() }) {
        val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
        val paired = maxWidth / density.fontScale.coerceAtLeast(1f) >= 640.dp ||
            actualPosture.hingeList.any { it.isVertical && (it.isSeparating || it.isOccluding) }
        @Composable fun Purpose(modifier: Modifier) {
            Box(modifier.testTag("setup-purpose"), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = gutter, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) { introduction() }
            }
        }
        @Composable fun Work(modifier: Modifier) {
            Box(modifier.testTag("setup-work"), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = gutter, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
        val horizontalHinge = actualPosture.hingeList.firstOrNull { !it.isVertical && (it.isSeparating || it.isOccluding) }
        if (horizontalHinge != null) {
            val height = with(density) { maxHeight.toPx() }
            val above = (horizontalHinge.bounds.top - origin.y).coerceIn(0f, height)
            val below = (horizontalHinge.bounds.bottom - origin.y).coerceIn(above, height)
            val topHeight = with(density) { above.toDp() }
            val bottomHeight = with(density) { (height - below).toDp() }
            Column(Modifier.fillMaxSize()) {
                Purpose(Modifier.fillMaxWidth().height(topHeight).verticalScroll(rememberScrollState()).heightIn(min = topHeight))
                Spacer(Modifier.height(with(density) { (below - above).toDp() }))
                Work(Modifier.fillMaxWidth().height(bottomHeight).verticalScroll(rememberScrollState()).heightIn(min = bottomHeight))
            }
        } else if (paired) {
            SupportingPaneScaffold(
                directive = calculatePaneScaffoldDirective(WindowAdaptiveInfo(adaptive.windowSizeClass, actualPosture), HingePolicy.AlwaysAvoid).copy(
                    maxHorizontalPartitions = 2, defaultPanePreferredWidth = maxWidth / 2,
                ),
                value = ThreePaneScaffoldValue(PaneAdaptedValue.Expanded, PaneAdaptedValue.Expanded, PaneAdaptedValue.Hidden),
                mainPane = { AnimatedPane { Purpose(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) } },
                supportingPane = { AnimatedPane { Work(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) } },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val viewport = maxHeight
            Layout(
                content = { Purpose(Modifier.fillMaxWidth()); Work(Modifier.fillMaxWidth()) },
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) { children, constraints ->
                val work = children[1].measure(constraints.copy(minHeight = 0))
                val purpose = children[0].measure(constraints.copy(minHeight = 0))
                val contentHeight = purpose.height + work.height
                val height = maxOf(viewport.roundToPx(), contentHeight)
                val top = (height - contentHeight) / 2
                layout(constraints.maxWidth, height) {
                    purpose.placeRelative(0, top)
                    work.placeRelative(0, top + purpose.height)
                }
            }
        }
    }
}

@Composable
internal fun SetupIntroduction(icon: Int, title: String, body: String?) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(painterResource(icon), null, modifier = Modifier.padding(16.dp).size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
