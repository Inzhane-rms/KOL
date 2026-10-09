package ph.appbuilders.saklolo.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import ph.appbuilders.saklolo.stt.PcmRecorder
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.NavMuted
import ph.appbuilders.saklolo.ui.theme.ShadowInk

private val CardShape = RoundedCornerShape(28.dp)
private val ShadowSpot = ShadowInk.copy(alpha = 0.10f)
private val ButtonShadow = ShadowInk.copy(alpha = 0.35f)

fun Modifier.softCard(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)): Modifier =
    this
        .shadow(6.dp, shape, ambientColor = ShadowSpot, spotColor = ShadowSpot)
        .clip(shape)
        .background(Color.White)

fun Modifier.flatCard(color: Color = Color.White, shape: androidx.compose.ui.graphics.Shape = CardShape): Modifier =
    this
        .fillMaxWidth()
        .softCard(shape)
        .background(color)

@Composable
fun RoundArrow(
    background: Color,
    tint: Color,
    description: String?,
    onClick: (() -> Unit)? = null,
    diameter: Dp = 36.dp,
) {
    val base = Modifier
        .size(diameter)
        .clip(CircleShape)
        .background(background)
    Box(
        modifier = if (onClick != null && description != null) base.clickable(onClick = onClick) else base,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = if (onClick != null) description else null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun InfoCard(
    background: Color,
    label: String,
    title: String,
    details: String,
    ink: Color,
    detailsColor: Color = ink,
    arrowBackground: Color,
    arrowTint: Color,
    arrowDescription: String?,
    onArrow: (() -> Unit)?,
    minHeight: Dp = 0.dp,
    extra: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .flatCard(background)
            .heightIn(min = minHeight)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(title, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (details.isNotEmpty()) {
                    Text(details, color = detailsColor, fontSize = 14.sp)
                }
            }
            RoundArrow(arrowBackground, arrowTint, arrowDescription, onArrow)
        }
        extra?.invoke()
    }
}

@Composable
fun SosOrb(
    recording: Boolean,
    elapsedSec: Int,
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onHoldCancel: () -> Unit,
    dimmed: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val recordingNow by rememberUpdatedState(recording)
    val enabledNow by rememberUpdatedState(enabled)
    val startNow by rememberUpdatedState(onHoldStart)
    val endNow by rememberUpdatedState(onHoldEnd)
    val cancelNow by rememberUpdatedState(onHoldCancel)
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "press",
    )
    val easeOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val transition = rememberInfiniteTransition(label = "pulse")
    val ring1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = easeOut), RepeatMode.Restart),
        label = "ring1",
    )
    val ring2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, delayMillis = 700, easing = easeOut), RepeatMode.Restart),
        label = "ring2",
    )
    val fraction = if (recording) elapsedSec / PcmRecorder.MAX_SECONDS.toFloat() else 0f
    val pulse = if (recording) 0.10f to 0.16f else 0.05f to 0.09f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(296.dp)) {
        PulseDisc(ring1, 296.dp, pulse.first)
        PulseDisc(ring2, 256.dp, pulse.second)
        Box(
            Modifier
                .size(216.dp)
                .shadow(18.dp, CircleShape, ambientColor = ButtonShadow, spotColor = ButtonShadow)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFC9CDD6)))),
        )
        Canvas(Modifier.size(216.dp)) {
            if (fraction <= 0f) return@Canvas
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = Color(0xFFE3E6EC),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val angle = Math.toRadians((-90.0 + 360.0 * fraction.coerceIn(0f, 1f)))
            val radius = size.minDimension / 2f - inset
            val center = Offset(
                size.width / 2f + (cos(angle) * radius).toFloat(),
                size.height / 2f + (sin(angle) * radius).toFloat(),
            )
            drawCircle(Accent, 8.dp.toPx(), center)
            drawCircle(Color.White, 5.dp.toPx(), center)
        }
        Box(
            modifier = Modifier
                .size(184.dp)
                .scale(pressScale)
                .alpha(if (dimmed) 0.5f else 1f)
                .shadow(8.dp, CircleShape, ambientColor = ButtonShadow, spotColor = ButtonShadow)
                .clip(CircleShape)
                .background(
                    if (recording) {
                        Brush.radialGradient(listOf(Color(0xFFF23A3A), Color(0xFFCF1D24), Color(0xFFA11218)))
                    } else {
                        Brush.radialGradient(listOf(Color(0xFFFF4B4B), Accent, Color(0xFFB5161C)))
                    },
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!(enabledNow || recordingNow)) return@awaitEachGesture
                        down.consume()
                        pressed = true
                        if (!recordingNow) startNow()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        var outcome = SosHoldEnd.Release
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                event.changes.forEach { it.consume() }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                val decision = if (change == null) {
                                    sosHoldEnd(pointerPressed = true, outOfBounds = false, cancelled = true)
                                } else {
                                    val outside = change.position.x < 0f ||
                                        change.position.y < 0f ||
                                        change.position.x > width ||
                                        change.position.y > height
                                    sosHoldEnd(change.pressed, outside, cancelled = false)
                                }
                                if (decision != SosHoldEnd.Continue) {
                                    outcome = decision
                                    break
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            pressed = false
                            cancelNow()
                            throw cancelled
                        }
                        pressed = false
                        if (outcome == SosHoldEnd.Cancel) cancelNow() else endNow()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (recording) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White),
                )
            } else {
                Canvas(Modifier.size(64.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Color.White, 5.dp.toPx(), c)
                    drawArc(
                        Color.White,
                        startAngle = -60f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(c.x - 14.dp.toPx(), c.y - 14.dp.toPx()),
                        size = Size(28.dp.toPx(), 28.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        Color.White,
                        startAngle = -50f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(c.x - 24.dp.toPx(), c.y - 24.dp.toPx()),
                        size = Size(48.dp.toPx(), 48.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseDisc(progress: Float, diameter: Dp, baseAlpha: Float) {
    Box(
        Modifier
            .size(diameter)
            .scale(1f + 0.15f * progress)
            .clip(CircleShape)
            .background(Accent.copy(alpha = baseAlpha * (1f - progress))),
    )
}

@Composable
fun BottomSwitcher(
    route: String,
    alertBadge: Int,
    onRecord: () -> Unit,
    onAsk: () -> Unit,
    onFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .height(116.dp),
    ) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(6.dp, RoundedCornerShape(32.dp), ambientColor = ShadowSpot, spotColor = ShadowSpot)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavSide("Alerts", Icons.Filled.Notifications, route == "feed", alertBadge, Modifier.weight(1f), onFeed)
            Box(Modifier.size(width = 88.dp, height = 64.dp))
            NavSide("Ask", Icons.Filled.ChatBubble, route == "ask", 0, Modifier.weight(1f), onAsk)
        }
        Column(
            Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White))
                if (route == "record") {
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.35f)),
                    )
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .shadow(6.dp, CircleShape, ambientColor = Accent.copy(alpha = 0.35f), spotColor = Accent.copy(alpha = 0.35f))
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFF4B4B), Accent, Color(0xFFB5161C))))
                        .clickable(onClick = onRecord),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Record", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        Text(
            "Record",
            color = if (route == "record") Accent else NavMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun NavSide(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ink = if (selected) Accent else NavMuted
    Column(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(width = 56.dp, height = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LightRed),
                )
            }
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
            if (badge > 0) {
                val count = if (badge > 9) "9+" else badge.toString()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-6).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(count, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            label,
            color = ink,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun PlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play voice clip", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
