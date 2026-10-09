package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ph.appbuilders.saklolo.SosUiState
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.ui.theme.ForestMid
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.MintWash
import ph.appbuilders.saklolo.ui.theme.Page

@Composable
fun SosScreen(
    state: SosUiState,
    peers: List<NearbyPeer>,
    lastAlert: Alert?,
    clipReady: (Alert) -> Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    onPlay: (Alert) -> Unit,
) {
    var tipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6_000)
            tipIndex = (tipIndex + 1) % preparednessTips.size
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroCard(state, peers.size, onHoldStart, onHoldEnd)
        if (state.actionable && state.urgency != null) {
            DraftCard(state, onTranscript, onSend, onDiscard)
        } else if (lastAlert != null) {
            LastAlertCard(lastAlert, clipReady(lastAlert), onPlay)
        }
        TipsCard(tipIndex) { tipIndex = (tipIndex + 1) % preparednessTips.size }
        NearbyCard(peers)
    }
}

@Composable
private fun HeroCard(
    state: SosUiState,
    peerCount: Int,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val action = if (state.recording) "RELEASE TO SEND" else "HOLD TO RECORD"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(ForestMid),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SosOrb(
            recording = state.recording,
            elapsedSec = state.elapsedSec,
            enabled = state.modelReady,
            onHoldStart = onHoldStart,
            onHoldEnd = onHoldEnd,
        )
        Text(
            text = "$action · ${phonesNearby(peerCount)}",
            color = MintWash,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun NearbyCard(peers: List<NearbyPeer>) {
    val title = when {
        peers.isEmpty() -> "No phones connected yet"
        else -> "Nearby: ${peers.first().name} · connected"
    }
    val details = when {
        peers.isEmpty() -> "Waiting for a B-LINK phone"
        peers.size == 1 -> formatWhen(peers.first().connectedAtMillis)
        else -> peers.drop(1).joinToString(" · ") { "${it.name} · connected" }
    }
    InfoCard(
        background = Color.White,
        label = "NEARBY",
        title = title,
        details = details,
        ink = Ink,
        detailsColor = InkSoft,
        arrowBackground = ForestMid,
        arrowTint = Color.White,
        arrowDescription = null,
        onArrow = null,
    )
}

@Composable
private fun DraftCard(
    state: SosUiState,
    onTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        Modifier.flatCard(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "REVIEW · ${state.urgency!!.label}",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(state.summary, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(sourceLabel(state.summarySource), color = InkSoft, fontSize = 14.sp)
        BasicTextField(
            value = state.transcript,
            onValueChange = onTranscript,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier
                .fillMaxWidth()
                .background(Page, RoundedCornerShape(16.dp))
                .padding(12.dp),
        )
        Text(state.status, color = InkSoft, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSend, modifier = Modifier.height(48.dp)) {
                Text("Send alert", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDiscard, modifier = Modifier.height(48.dp)) {
                Text("Discard", color = InkSoft, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun LastAlertCard(alert: Alert, canPlay: Boolean, onPlay: (Alert) -> Unit) {
    InfoCard(
        background = Color.White,
        label = "LAST ALERT · ${alert.urgency.label}",
        title = alert.summary,
        details = "${formatDelivered(alert.deliveredCount)} · ${formatHops(alert.hops)} · ${sourceLabel(alert.summarySource)}",
        ink = Ink,
        detailsColor = InkSoft,
        arrowBackground = ForestMid,
        arrowTint = Color.White,
        arrowDescription = if (canPlay) "Play voice clip" else null,
        onArrow = if (canPlay) ({ onPlay(alert) }) else null,
    )
}

@Composable
private fun TipsCard(index: Int, onNext: () -> Unit) {
    val tip = preparednessTips[index]
    InfoCard(
        background = MintWash,
        label = "TIP ${index + 1} OF ${preparednessTips.size}",
        title = tip.tagalog,
        details = tip.english,
        ink = Ink,
        detailsColor = InkSoft,
        arrowBackground = Color.White,
        arrowTint = Ink,
        arrowDescription = "Next tip",
        onArrow = onNext,
    )
}

