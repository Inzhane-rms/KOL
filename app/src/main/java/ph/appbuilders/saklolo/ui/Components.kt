package ph.appbuilders.saklolo.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.triage.Urgency
import ph.appbuilders.saklolo.ui.theme.HelpAmber
import ph.appbuilders.saklolo.ui.theme.HelpInk
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.CriticalRed
import ph.appbuilders.saklolo.ui.theme.OrbDeep
import ph.appbuilders.saklolo.ui.theme.RingPink
import ph.appbuilders.saklolo.ui.theme.SafeGreen

fun Modifier.glass(corner: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
        .clip(shape)
        .background(Color.White.copy(alpha = 0.94f))
        .border(1.dp, Color.White.copy(alpha = 0.5f), shape)
}

@Composable
fun UrgencyChip(urgency: Urgency) {
    val background: Color
    val foreground: Color
    val border: Color
    when (urgency) {
        Urgency.CRITICAL -> {
            background = CriticalRed
            foreground = Color.White
            border = Color.Transparent
        }
        Urgency.NEEDS_HELP -> {
            background = HelpAmber
            foreground = HelpInk
            border = Color.Transparent
        }
        Urgency.SAFE -> {
            background = Color.White
            foreground = SafeGreen
            border = SafeGreen
        }
    }
    Box(
        modifier = Modifier
            .height(32.dp)
            .border(if (urgency == Urgency.SAFE) 2.dp else 0.dp, border, CircleShape)
            .background(background, CircleShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(urgency.label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SourcePill(source: String) {
    Text(
        text = sourceLabel(source),
        color = Ink,
        fontSize = 14.sp,
        modifier = Modifier
            .border(1.dp, Ink.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SosOrb(
    recording: Boolean,
    elapsedSec: Int,
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "press",
    )
    val pulseMs = if (recording) 800 else 1600
    val easeOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val transition = rememberInfiniteTransition(label = "pulse")
    val ring1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(pulseMs, easing = easeOut), RepeatMode.Restart),
        label = "ring1",
    )
    val ring2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(pulseMs, delayMillis = pulseMs / 2, easing = easeOut),
            RepeatMode.Restart,
        ),
        label = "ring2",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        PulseRing(ring1)
        PulseRing(ring2)
        BoxWithConstraints(
            modifier = Modifier
                .size(220.dp)
                .scale(pressScale)
                .shadow(24.dp, CircleShape, ambientColor = OrbDeep, spotColor = OrbDeep)
                .clip(CircleShape)
                .pointerInput(enabled, recording) {
                    if (!enabled && !recording) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            if (!recording) onHoldStart()
                            tryAwaitRelease()
                            pressed = false
                            onHoldEnd()
                        },
                    )
                },
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color(0xFFFF8A80),
                                0.45f to Color(0xFFE53935),
                                1f to Color(0xFF7F1D1D),
                            ),
                            center = Offset(widthPx * 0.38f, heightPx * 0.32f),
                            radius = widthPx * 0.85f,
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 18.dp)
                    .size(width = 120.dp, height = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (recording) "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60) else "SOS",
                    color = Color.White,
                    fontSize = if (recording) 32.sp else 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PulseRing(progress: Float) {
    val scale = 1f + (0.35f * progress)
    val alpha = 0.6f * (1f - progress)
    Box(
        Modifier
            .size(220.dp)
            .scale(scale)
            .border(3.dp, RingPink.copy(alpha = alpha), CircleShape),
    )
}

@Composable
fun BottomSwitcher(recordSelected: Boolean, onRecord: () -> Unit, onFeed: () -> Unit) {
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SwitchTab("Record", recordSelected, Modifier.weight(1f), onRecord)
        SwitchTab("Responder feed", !recordSelected, Modifier.weight(1f), onFeed)
    }
}

@Composable
private fun SwitchTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Ink else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
