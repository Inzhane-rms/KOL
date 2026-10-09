package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.SosUiState
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.GreenText
import ph.appbuilders.saklolo.ui.theme.Hairline
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.StatusGreen
import kotlin.math.sin

@Composable
fun SosScreen(
    state: SosUiState,
    peers: List<NearbyPeer>,
    location: Pair<Double, Double>?,
    lastAlert: Alert?,
    lastAlertLocal: Boolean,
    clipReady: (Alert) -> Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onHoldCancel: () -> Unit,
    onTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    onPlay: (Alert) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlerts: () -> Unit,
    micBlocked: Boolean = false,
    micGranted: Boolean = true,
    locationWarning: String? = null,
    onOpenAppSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPills(state.gemmaLoading, peers.size, location)
        locationWarning?.let { detail ->
            Spacer(Modifier.height(12.dp))
            LocationWarningCard(detail, onOpenAppSettings)
        }
        Spacer(Modifier.height(12.dp))
        if (state.recording) {
            RecordingHeading()
            Spacer(Modifier.height(12.dp))
            LevelBars(state.micLevel)
        } else {
            Text(
                "Emergency help\nneeded?",
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
            )
            Text(
                "Speak in Tagalog or Bisaya",
                color = InkSoft,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            HoldSteps()
        }
        state.error?.let { error ->
            Text(error, color = InkSoft, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        SosOrb(
            recording = state.recording,
            elapsedSec = state.elapsedSec,
            enabled = state.modelReady,
            onHoldStart = onHoldStart,
            onHoldEnd = onHoldEnd,
            onHoldCancel = onHoldCancel,
            dimmed = !micGranted,
        )
        Spacer(Modifier.weight(1f))
        if (state.recording) {
            Text(
                holdLabel(true, state.elapsedSec),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            HoldHintCard()
        } else {
            Text(
                "Hold to record",
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onOpenSettings() })
                },
            )
        }
        if (micBlocked) {
            Spacer(Modifier.height(12.dp))
            MicBlockedCard(onOpenAppSettings)
        }
        if (state.actionable && state.urgency != null && !state.recording) {
            Spacer(Modifier.height(12.dp))
            DraftCard(state, onTranscript, onSend, onDiscard)
        } else if (!state.recording && lastAlert != null) {
            Spacer(Modifier.height(12.dp))
            LastAlertCard(
                alert = lastAlert,
                localOrigin = lastAlertLocal,
                canPlay = clipReady(lastAlert),
                onOpen = { if (clipReady(lastAlert)) onPlay(lastAlert) else onOpenAlerts() },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RecordingHeading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
        Text(
            "  Recording…",
            color = Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Text(
        "Speak now",
        color = Ink,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        "Say where you are and what you need",
        color = InkSoft,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun HoldSteps() {
    val labels = listOf("1" to "Hold", "2" to "Speak", "3" to "Release")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, (number, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(label, color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (index < labels.lastIndex) {
                Box(
                    Modifier
                        .padding(bottom = 16.dp)
                        .width(28.dp)
                        .height(2.dp)
                        .background(Color(0xFFC9CDD6)),
                )
            }
        }
    }
}

@Composable
private fun LevelBars(level: Float) {
    val loud = level.coerceIn(0f, 1f)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(20) { index ->
            val shape = 0.35f + 0.65f * sin(Math.PI * (index + 1) / 21.0).toFloat()
            val window = if (index < 14) 1f else 0.28f
            val height = 8f + 30f * shape * loud.coerceAtLeast(0.2f) * window
            Box(
                Modifier
                    .width(5.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(if (index < 14) Accent else Color(0xFFC9CDD6)),
            )
        }
    }
}

@Composable
private fun HoldHintCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .softCard(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("↑", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Release to send", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(1.5.dp).height(40.dp).background(Hairline))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("←", color = InkSoft, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Slide away to cancel", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPills(gemmaLoading: Boolean, peers: Int, location: Pair<Double, Double>?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .softCard(CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(StatusGreen))
            Text(
                statusPill(gemmaLoading, peers),
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .softCard(CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
            Text(
                locationPill(location?.first, location?.second),
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun MicBlockedCard(onOpenAppSettings: () -> Unit) {
    PermissionCard(
        icon = Icons.Filled.Mic,
        title = "Kailangan ang mikropono",
        detail = "Allow mic access to record an SOS.",
        onOpenAppSettings = onOpenAppSettings,
    )
}

@Composable
private fun LocationWarningCard(detail: String, onOpenAppSettings: () -> Unit) {
    PermissionCard(
        icon = Icons.Filled.LocationOn,
        title = "Kailangan ang lokasyon",
        detail = detail,
        onOpenAppSettings = onOpenAppSettings,
    )
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    detail: String,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(LightRed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = InkSoft, fontSize = 13.sp)
            }
        }
        Box(
            Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Ink)
                .clickable(onClick = onOpenAppSettings)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Buksan ang Settings", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LastAlertCard(alert: Alert, localOrigin: Boolean, canPlay: Boolean, onOpen: () -> Unit) {
    val progress = alertProgress(localOrigin, alert.deliveredCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .softCard(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = 16.dp).size(40.dp).clip(CircleShape).background(Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("((•))", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(alert.summary, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            StepRow(progress)
        }
        RoundArrow(
            background = LightRed,
            tint = Accent,
            description = if (canPlay) "Play voice clip" else "Open alerts",
            onClick = onOpen,
            diameter = 48.dp,
        )
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun StepRow(progress: AlertProgress) {
    val steps = listOf(
        "Recorded" to progress.recorded,
        "Sent" to progress.sent,
        "Delivered" to progress.delivered,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        steps.forEach { (label, done) ->
            val color = if (done) GreenText else InkSoft
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (done) GreenText else InkSoft.copy(alpha = 0.35f)),
            )
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun DraftCard(
    state: SosUiState,
    onTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().softCard(RoundedCornerShape(28.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("REVIEW · ${state.urgency!!.label}", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(state.summary, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(sourceLabel(state.summarySource), color = if (state.summarySource == "AI") GreenText else InkSoft, fontSize = 13.sp)
        BasicTextField(
            value = state.transcript,
            onValueChange = onTranscript,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF4F5F8), RoundedCornerShape(16.dp)).padding(12.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Accent)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Text("Send alert", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onDiscard),
            contentAlignment = Alignment.Center,
        ) {
            Text("Discard", color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
