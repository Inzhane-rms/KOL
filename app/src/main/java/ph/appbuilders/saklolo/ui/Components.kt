package ph.appbuilders.saklolo.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.ui.theme.CriticalRed
import ph.appbuilders.saklolo.ui.theme.ForestMint
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.RingPink

private val CardShape = RoundedCornerShape(28.dp)

fun Modifier.flatCard(color: Color): Modifier =
    this
        .fillMaxWidth()
        .clip(CardShape)
        .background(color)

@Composable
fun RoundArrow(
    background: Color,
    tint: Color,
    description: String?,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier
        .size(44.dp)
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
                Text(title, color = ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
    diameter: Dp = 148.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val recordingNow by rememberUpdatedState(recording)
    val enabledNow by rememberUpdatedState(enabled)
    val startNow by rememberUpdatedState(onHoldStart)
    val endNow by rememberUpdatedState(onHoldEnd)
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
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter * 1.35f)) {
        PulseRing(ring1, diameter)
        PulseRing(ring2, diameter)
        Box(
            modifier = Modifier
                .size(diameter)
                .scale(pressScale)
                .clip(CircleShape)
                .background(CriticalRed)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (enabledNow || recordingNow) {
                                pressed = true
                                if (!recordingNow) startNow()
                                tryAwaitRelease()
                                pressed = false
                                endNow()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(width = diameter * 0.55f, height = diameter * 0.22f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )
            Text(
                text = if (recording) "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60) else "SOS",
                color = Color.White,
                fontSize = if (recording) 22.sp else 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PulseRing(progress: Float, diameter: Dp) {
    val scale = 1f + (0.35f * progress)
    val alpha = 0.6f * (1f - progress)
    Box(
        Modifier
            .size(diameter)
            .scale(scale)
            .border(3.dp, RingPink.copy(alpha = alpha), CircleShape),
    )
}

@Composable
fun BottomSwitcher(
    route: String,
    onRecord: () -> Unit,
    onAsk: () -> Unit,
    onFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .width(300.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Ink)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SwitchTab("Record", route == "record", Modifier.weight(1f), onRecord)
        SwitchTab("Feed", route == "feed", Modifier.weight(1f), onFeed)
        SwitchTab("Ask", route == "ask", Modifier.weight(1f), onAsk)
    }
}

@Composable
private fun SwitchTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) ForestMint else Color.Transparent)
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

@Composable
fun PlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play voice clip", tint = Ink)
    }
}
