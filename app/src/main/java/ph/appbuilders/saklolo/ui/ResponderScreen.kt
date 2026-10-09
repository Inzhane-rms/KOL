package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.SafeGreen
import ph.appbuilders.saklolo.ui.theme.stripe

@Composable
fun ResponderScreen(
    alerts: List<Alert>,
    notice: String?,
    clipReady: (Alert) -> Boolean,
    onScan: () -> Unit,
    onDelete: (String) -> Unit,
    onShowQr: (Alert) -> Unit,
    onPlay: (Alert) -> Unit,
    onDismissNotice: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text(formatCounts(alerts), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Sorted by urgency, then newest", color = InkSoft, fontSize = 14.sp)
            TextButton(onClick = onScan, modifier = Modifier.height(56.dp)) {
                Text("Scan QR", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        notice?.let { message ->
            Text(
                text = message,
                color = Ink,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(20.dp)
                    .padding(16.dp)
                    .clickable(onClick = onDismissNotice),
            )
        }
        if (alerts.isEmpty()) {
            Text(
                text = "No alerts on this phone yet. Record an SOS, or wait for a nearby phone.",
                color = Ink,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
            )
        }
        alerts.forEach { alert ->
            FeedCard(
                alert = alert,
                canPlay = clipReady(alert),
                onPlay = { onPlay(alert) },
                onDelete = { onDelete(alert.id) },
                onShowQr = { onShowQr(alert) },
            )
        }
    }
}

@Composable
private fun FeedCard(
    alert: Alert,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
) {
    var open by rememberSaveable(alert.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .glass(),
    ) {
        Box(
            Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(alert.urgency.stripe()),
        )
        Column(
            Modifier
                .weight(1f)
                .clickable { open = !open }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UrgencyChip(alert.urgency)
                SourcePill(alert.summarySource)
                if (canPlay) {
                    Box(Modifier.weight(1f))
                    PlayButton(onClick = onPlay)
                }
            }
            Text(alert.summary, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${formatHops(alert.hops)} · ${formatWhen(alert.createdAtMillis)} · ${formatGps(alert.lat, alert.lon)}",
                color = InkSoft,
                fontSize = 14.sp,
            )
            if (open) {
                Text(alert.transcript, color = Ink, fontSize = 18.sp)
                Row {
                    TextButton(onClick = onShowQr, modifier = Modifier.height(56.dp)) {
                        Text("QR", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.height(56.dp)) {
                        Text("Delete", color = InkSoft, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(SafeGreen)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play voice clip", tint = Color.White)
    }
}
