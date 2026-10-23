package com.atharchive.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atharchive.ui.navigation.AtharDestination
import com.atharchive.ui.theme.AtharTheme
import kotlinx.coroutines.launch
import kotlin.math.max

/*
 * The selection indicator is one soft object, not a sliding rectangle.
 *
 * Its two edges are two INDEPENDENT springs. The edge facing the destination
 * gets the stiff spring and leaves first; the far edge gets a softer one and
 * arrives late. The gap between them IS the deformation — nothing is scaled,
 * so the rounded ends stay circular instead of smearing into ellipses, and the
 * asymmetry reads as being pulled rather than stretched.
 *
 *   stretch → pull → follow → compress → settle
 *
 * Constants come from a grid search over the HTML prototype, not from taste:
 * stiffness sets the clock, damping ratio sets the overshoot, and the trailing
 * spring's softness sets how much it stretches. The three turned out to be
 * near-independent, so they can be retuned one at a time.
 *
 *   neighbour hop   ~183ms   stretch ×1.50   overshoot ~1px
 *   full-width jump ~213ms   stretch ×2.99   overshoot ~5px
 *
 * Compose honours the system "remove animations" setting through
 * MotionDurationScale, so both springs snap when a11y animations are off.
 */
private const val STIFFNESS_LEAD = 2325f
private const val STIFFNESS_TRAIL = 1162f   // = LEAD × 0.50; the lag IS the stretch
private const val DAMPING_LEAD = 0.75f      // <1, but high: settles, never wobbles
private const val DAMPING_TRAIL = 0.90f
private const val MAX_STRETCH = 3.4f        // a long jump travels; it must not smear
private const val SQUASH = 0.30f            // height given up per unit of width gained
private const val MIN_SQUASH = 0.80f
private const val JELLY = 0.14f             // how far a re-tap squashes the pill
private const val EDGE_EPSILON = 0.5f       // half a pixel — invisible, and ends the tail
private val PillWidth = 46.dp
private val PillHeight = 32.dp

@Composable
fun AtharBottomBar(
    selectedDestination: AtharDestination?,
    onDestinationClick: (AtharDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale >= 1.6f
    val destinations = AtharDestination.entries

    // Measured in window space and subtracted at draw time. Doing it that way
    // means the bar's and the icons' onGloballyPositioned callbacks can land in
    // any order without leaving a stale offset behind.
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    val iconCentreX = remember { mutableStateListOf(*Array(destinations.size) { 0f }) }
    var iconCentreY by remember { mutableFloatStateOf(0f) }

    val halfPill = with(LocalDensity.current) { PillWidth.toPx() / 2f }
    val leftEdge = remember { Animatable(0f, EDGE_EPSILON) }
    val rightEdge = remember { Animatable(0f, EDGE_EPSILON) }
    var placed by remember { mutableStateOf(false) }
    var movingRight by remember { mutableStateOf(true) }
    val jelly = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val selectedIndex = destinations.indexOf(selectedDestination)
    val targetCentre = iconCentreX.getOrElse(selectedIndex) { 0f }
        .let { if (it == 0f || barBounds == Rect.Zero) Float.NaN else it - barBounds.left }

    LaunchedEffect(targetCentre) {
        if (targetCentre.isNaN()) return@LaunchedEffect
        val toLeft = targetCentre - halfPill
        val toRight = targetCentre + halfPill
        if (!placed) {
            leftEdge.snapTo(toLeft)
            rightEdge.snapTo(toRight)
            placed = true
            return@LaunchedEffect
        }
        movingRight = targetCentre > (leftEdge.value + rightEdge.value) / 2f
        // Cancelling the previous animateTo preserves each edge's velocity, so a
        // fast second tap carries its momentum instead of restarting from rest.
        launch {
            leftEdge.animateTo(
                toLeft,
                if (movingRight) {
                    spring(DAMPING_TRAIL, STIFFNESS_TRAIL, EDGE_EPSILON)
                } else {
                    spring(DAMPING_LEAD, STIFFNESS_LEAD, EDGE_EPSILON)
                },
            )
        }
        launch {
            rightEdge.animateTo(
                toRight,
                if (movingRight) {
                    spring(DAMPING_LEAD, STIFFNESS_LEAD, EDGE_EPSILON)
                } else {
                    spring(DAMPING_TRAIL, STIFFNESS_TRAIL, EDGE_EPSILON)
                },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AtharTheme.colors.canvas)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(
            thickness = 0.7.dp,
            color = AtharTheme.colors.divider.copy(alpha = 0.42f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { barBounds = it.boundsInWindow() },
        ) {
            val pillColor = AtharTheme.colors.accentSurface
            // Drawn first so it sits behind the icons. Reading the Animatables
            // inside the draw lambda repaints per frame without recomposing.
            Canvas(Modifier.matchParentSize()) {
                if (!placed || selectedDestination == null) return@Canvas
                val restW = PillWidth.toPx()
                val restH = PillHeight.toPx()
                var width = rightEdge.value - leftEdge.value
                var x = leftEdge.value
                if (width > restW * MAX_STRETCH) {
                    width = restW * MAX_STRETCH
                    if (movingRight) x = rightEdge.value - width
                }
                // Volume roughly conserved: what it gains in length it gives up
                // in height, which is what makes it read as soap and not rubber.
                val stretch = width / restW
                var height = restH * max(MIN_SQUASH, 1f - SQUASH * (stretch - 1f))
                val j = jelly.value
                x -= width * JELLY * j / 2f
                width *= 1f + JELLY * j
                height *= 1f - JELLY * j
                val top = (iconCentreY - barBounds.top) - height / 2f
                drawRoundRect(
                    color = pillColor,
                    topLeft = Offset(x, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height * 0.42f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = destination == selectedDestination
                    // The selected destination is the only burgundy in the bar.
                    val tint by animateColorAsState(
                        targetValue = if (selected) {
                            AtharTheme.colors.accent
                        } else {
                            AtharTheme.colors.secondaryText
                        },
                        animationSpec = tween(180),
                        label = "bottom_bar_tint",
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bottom_${destination.route}")
                            .defaultMinSize(minHeight = 56.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                // No ripple: the indicator is the feedback now, and a
                                // 56dp ripple slab fights the soft pill underneath it.
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (selected) scope.launch { jelly.jelly() }
                                    onDestinationClick(destination)
                                },
                            )
                            .semantics {
                                this.selected = selected
                                contentDescription = destination.label
                            }
                            .padding(horizontal = 2.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .size(21.dp)
                                .onGloballyPositioned {
                                    val bounds = it.boundsInWindow()
                                    iconCentreX[index] = bounds.center.x
                                    iconCentreY = bounds.center.y
                                },
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = destination.label,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = if (largeText) 2 else 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The reader bar's soap: one soft pill for the whole bar. It grows in under the first control that opens,
 * travels (the bottom bar's two-spring stretch) when another control takes over, and shrinks
 * away when the last one closes, so every change moves.
 */
@Composable
internal fun SoapTravelIndicator(selected: Int, centres: List<Offset>, color: Color, modifier: Modifier) {
    val half = with(LocalDensity.current) { PillWidth.toPx() / 2 }
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    var centreY by remember { mutableFloatStateOf(0f) }
    val target = centres.getOrNull(selected)?.takeIf { it != Offset.Zero }
    LaunchedEffect(target) {
        if (target == null) {
            grow.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 900f))
            return@LaunchedEffect
        }
        centreY = target.y
        if (grow.value < 0.2f) {
            left.snapTo(target.x - half)
            right.snapTo(target.x + half)
            grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
            return@LaunchedEffect
        }
        val movingRight = target.x > (left.value + right.value) / 2
        launch { grow.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f)) }
        launch { left.animateTo(target.x - half, if (movingRight) spring(DAMPING_TRAIL, STIFFNESS_TRAIL) else spring(DAMPING_LEAD, STIFFNESS_LEAD)) }
        launch { right.animateTo(target.x + half, if (movingRight) spring(DAMPING_LEAD, STIFFNESS_LEAD) else spring(DAMPING_TRAIL, STIFFNESS_TRAIL)) }
    }
    Canvas(modifier) {
        val g = grow.value
        if (g <= 0.01f) return@Canvas
        val rest = PillWidth.toPx()
        val travel = (right.value - left.value).coerceIn(1f, rest * MAX_STRETCH)
        val width = travel * g
        val height = PillHeight.toPx() * max(MIN_SQUASH, 1 - SQUASH * (travel / rest - 1)) * (0.8f + 0.2f * g)
        drawRoundRect(
            color.copy(alpha = color.alpha * g.coerceIn(0f, 1f)),
            Offset((left.value + right.value - width) / 2, centreY - height / 2),
            Size(width, height),
            CornerRadius(height * 0.42f),
        )
    }
}

/** Where each choice of a soap row sits, measured in the row's own box. */
class SoapTargets {
    internal var box: androidx.compose.ui.layout.LayoutCoordinates? = null
    internal val rects = androidx.compose.runtime.mutableStateMapOf<Int, Rect>()
    internal var pulse by androidx.compose.runtime.mutableIntStateOf(0)
    internal val jelly = Animatable(0f)

    /** The current choice was tapped again: the pill wobbles in place instead of travelling. */
    fun reselect() { pulse++ }
}

/** Squashes wide and flat, then springs back past rest and settles: soap poked, not moved. */
internal suspend fun Animatable<Float, androidx.compose.animation.core.AnimationVector1D>.jelly() {
    animateTo(1f, tween(70))
    animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
}

@Composable
fun rememberSoapTargets() = remember { SoapTargets() }

/** Marks choice [index] as a place the pill can land; the pill takes this element's bounds. */
fun Modifier.soapTarget(targets: SoapTargets, index: Int): Modifier = onGloballyPositioned { coords ->
    val box = targets.box?.takeIf { it.isAttached } ?: return@onGloballyPositioned
    val rect = box.localBoundingBoxOf(coords, clipBounds = false)
    if (targets.rects[index] != rect) targets.rects[index] = rect
}

/**
 * The bottom bar's soap for any row of choices (tabs, segments): one pill behind the row whose
 * two edges are separate springs, so it stretches toward the new choice, thins on the way and
 * lands with a squeeze. Put it first in a Box around the row, sized with Modifier.matchParentSize().
 */
@Composable
fun SoapLayer(targets: SoapTargets, selected: Int, color: Color, modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = 8.dp) {
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val bottom = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = targets.rects[selected]
    LaunchedEffect(targets.pulse) { if (targets.pulse > 0) targets.jelly.jelly() }
    LaunchedEffect(target) {
        if (target == null) {
            placed = false
            return@LaunchedEffect
        }
        if (!placed) {
            left.snapTo(target.left); right.snapTo(target.right); top.snapTo(target.top); bottom.snapTo(target.bottom)
            placed = true
            return@LaunchedEffect
        }
        val movingRight = target.center.x > (left.value + right.value) / 2
        val lead = spring(DAMPING_LEAD, STIFFNESS_LEAD, EDGE_EPSILON)
        val trail = spring(DAMPING_TRAIL, STIFFNESS_TRAIL, EDGE_EPSILON)
        launch { left.animateTo(target.left, if (movingRight) trail else lead) }
        launch { right.animateTo(target.right, if (movingRight) lead else trail) }
        launch { top.animateTo(target.top, lead) }
        launch { bottom.animateTo(target.bottom, lead) }
    }
    Canvas(modifier.onPlaced { targets.box = it }) {
        if (!placed || target == null) return@Canvas
        val j = targets.jelly.value
        val rawWidth = right.value - left.value
        val stretch = (rawWidth / target.width.coerceAtLeast(1f) - 1f).coerceIn(0f, MAX_STRETCH)
        val width = rawWidth * (1 + JELLY * j)
        val height = (bottom.value - top.value) * max(MIN_SQUASH, 1 - SQUASH * stretch) * (1 - JELLY * j)
        val centreY = (top.value + bottom.value) / 2
        val x = left.value - rawWidth * JELLY * j / 2
        drawRoundRect(color, Offset(x, centreY - height / 2), Size(width, height), CornerRadius(corner.toPx().coerceAtMost(height / 2)))
    }
}
