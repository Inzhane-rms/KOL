package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ph.appbuilders.saklolo.SosUiState
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NearbyCard(peers)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SosOrb(
                recording = state.recording,
                elapsedSec = state.elapsedSec,
                enabled = state.modelReady,
                onHoldStart = onHoldStart,
                onHoldEnd = onHoldEnd,
            )
            Text(
                text = "Speak in Tagalog or Bisaya · works offline",
                color = InkSoft,
                fontSize = 14.sp,
                modifier = Modifier
                    .glass(20.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.recording) "Release to send it through" else "HOLD TO RECORD",
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .glass(20.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.modelStatus,
                color = InkSoft,
                fontSize = 14.sp,
                modifier = Modifier
                    .glass(20.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (state.actionable && state.urgency != null) {
            DraftCard(state, onTranscript, onSend, onDiscard)
        } else if (lastAlert != null) {
            LastAlertCard(lastAlert, clipReady(lastAlert), onPlay)
        }
        state.error?.let { message ->
            Text(
                text = message,
                color = Ink,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(20.dp)
                    .padding(16.dp),
            )
        }
        TipsCard()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NearbyCard(peers: List<NearbyPeer>) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("NEARBY PHONES", color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (peers.isEmpty()) {
            Text("No phones connected yet", color = Ink, fontSize = 18.sp)
        } else {
            peers.forEach { peer ->
                Text(
                    text = "${peer.name} · connected · ${formatWhen(peer.connectedAtMillis)}",
                    color = Ink,
                    fontSize = 18.sp,
                )
            }
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
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("REVIEW", color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UrgencyChip(state.urgency!!)
            SourcePill(state.summarySource)
        }
        Text(state.summary, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = state.transcript,
            onValueChange = onTranscript,
            textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 18.sp),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3F7F5), RoundedCornerShape(12.dp))
                .padding(12.dp),
        )
        Text(state.status, color = InkSoft, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSend, modifier = Modifier.height(56.dp)) {
                Text("Send alert", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDiscard, modifier = Modifier.height(56.dp)) {
                Text("Discard", color = InkSoft, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun LastAlertCard(alert: Alert, canPlay: Boolean, onPlay: (Alert) -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LAST ALERT", color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UrgencyChip(alert.urgency)
            SourcePill(alert.summarySource)
            if (canPlay) {
                Spacer(Modifier.weight(1f))
                PlayButton(onClick = { onPlay(alert) })
            }
        }
        Text(alert.summary, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "${formatDelivered(alert.deliveredCount)} · ${formatGps(alert.lat, alert.lon)}",
            color = InkSoft,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun TipsCard() {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6_000)
            index = (index + 1) % preparednessTips.size
        }
    }
    val tip = preparednessTips[index]
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "TIP ${index + 1} OF ${preparednessTips.size}",
            color = InkSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(tip.tagalog, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(tip.english, color = InkSoft, fontSize = 16.sp)
    }
}
