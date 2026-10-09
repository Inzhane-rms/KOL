package ph.appbuilders.saklolo.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.SosUiState
import ph.appbuilders.saklolo.ask.AskEngine
import ph.appbuilders.saklolo.ask.AskSuggestion
import ph.appbuilders.saklolo.ask.SafetyBank
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.Amber
import ph.appbuilders.saklolo.ui.theme.GreenText
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.StatusGreen

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
    onTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    onPlay: (Alert) -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAll: () -> Unit,
    onTopic: (String) -> Unit,
    onOpenAlerts: () -> Unit,
    micBlocked: Boolean = false,
    micGranted: Boolean = true,
    locationWarning: String? = null,
    onOpenAppSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val topics = androidx.compose.runtime.remember { AskEngine.recorderTopics(loadRecorderBank(context)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusPills(state.gemmaLoading, peers.size, location, onOpenSettings)
        locationWarning?.let { detail ->
            LocationWarningCard(detail, onOpenAppSettings)
        }
        Text(
            "Emergency help needed?",
            color = Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            "Hold the button and speak in Tagalog",
            color = InkSoft,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        recordStatusLine(state)?.let { line ->
            Text(line, color = InkSoft, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SosOrb(
                recording = state.recording,
                elapsedSec = state.elapsedSec,
                enabled = state.modelReady,
                onHoldStart = onHoldStart,
                onHoldEnd = onHoldEnd,
                dimmed = !micGranted,
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                holdLabel(state.recording, state.elapsedSec),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(Ink)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (micBlocked) {
            MicBlockedCard(onOpenAppSettings)
        }
        if (state.actionable && state.urgency != null) {
            DraftCard(state, onTranscript, onSend, onDiscard)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Not sure what to do?", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "See all",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            topics.forEachIndexed { index, topic ->
                val (icon, tint) = topicStyle(index)
                TopicCard(topic, icon, tint) { onTopic(topic.question) }
            }
        }
        if (!state.actionable && lastAlert != null) {
            LastAlertCard(
                alert = lastAlert,
                localOrigin = lastAlertLocal,
                canPlay = clipReady(lastAlert),
                onOpen = { if (clipReady(lastAlert)) onPlay(lastAlert) else onOpenAlerts() },
            )
        }
    }
}

private fun topicStyle(index: Int): Pair<ImageVector, Color> = when (index) {
    0 -> Icons.Filled.WaterDrop to StatusGreen
    1 -> Icons.Filled.Add to Accent
    else -> Icons.Filled.DirectionsRun to Amber
}

@Composable
private fun StatusPills(gemmaLoading: Boolean, peers: Int, location: Pair<Double, Double>?, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .softCard(CircleShape)
                .semantics { contentDescription = "Offline status. Long-press for demo settings" }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenSettings() }) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(StatusGreen))
            Text(
                statusPill(gemmaLoading, peers),
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .height(40.dp)
                .softCard(CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
            Text(
                locationPill(location?.first, location?.second),
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
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
        Text(
            "Buksan ang Settings",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Ink)
                .clickable(onClick = onOpenAppSettings)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TopicCard(topic: AskSuggestion, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .height(92.dp)
            .softCard()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(topic.label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun LastAlertCard(alert: Alert, localOrigin: Boolean, canPlay: Boolean, onOpen: () -> Unit) {
    val progress = alertProgress(localOrigin, alert.deliveredCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .softCard()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Text("!", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(alert.summary, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            StepRow(progress)
        }
        RoundArrow(
            background = LightRed,
            tint = Accent,
            description = if (canPlay) "Play voice clip" else "Open alerts",
            onClick = onOpen,
        )
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
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
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
    Column(Modifier.flatCard(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("REVIEW · ${state.urgency!!.label}", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(state.summary, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(sourceLabel(state.summarySource), color = if (state.summarySource == "AI") GreenText else InkSoft, fontSize = 13.sp)
        BasicTextField(
            value = state.transcript,
            onValueChange = onTranscript,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier.fillMaxWidth().background(Page, RoundedCornerShape(16.dp)).padding(12.dp),
        )
        Text(state.status, color = InkSoft, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSend, modifier = Modifier.height(48.dp)) {
                Text("Send alert", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDiscard, modifier = Modifier.height(48.dp)) {
                Text("Discard", color = InkSoft, fontSize = 16.sp)
            }
        }
    }
}

private fun recordStatusLine(state: SosUiState): String? {
    state.error?.let { return it }
    val idle = "Hold the button and speak. Tagalog, Bisaya, or English."
    if (state.status != idle && !state.recording) return state.status
    if (!state.modelReady) return state.modelStatus
    return null
}

private fun loadRecorderBank(context: Context): SafetyBank =
    context.assets.open("ask/ask_blink_qa.json").bufferedReader().use { reader ->
        AskEngine.parse(reader.readText())
    }
