package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import ph.appbuilders.saklolo.triage.Urgency
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.cardFill
import ph.appbuilders.saklolo.ui.theme.onCard

private enum class FeedFilter { ALL, CRITICAL, HELP }

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
    var filterName by rememberSaveable { mutableStateOf(FeedFilter.ALL.name) }
    val filter = runCatching { FeedFilter.valueOf(filterName) }.getOrDefault(FeedFilter.ALL)
    val shown = when (filter) {
        FeedFilter.ALL -> alerts
        FeedFilter.CRITICAL -> alerts.filter { it.urgency == Urgency.CRITICAL }
        FeedFilter.HELP -> alerts.filter { it.urgency == Urgency.NEEDS_HELP }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("All · ${alerts.size}", filter == FeedFilter.ALL) { filterName = FeedFilter.ALL.name }
            FilterChip("Critical", filter == FeedFilter.CRITICAL) { filterName = FeedFilter.CRITICAL.name }
            FilterChip("Needs help", filter == FeedFilter.HELP) { filterName = FeedFilter.HELP.name }
            FilterChip("Scan QR", selected = false, onClick = onScan)
        }
        notice?.let { message ->
            Text(
                text = message,
                color = Ink,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .flatCard(Color.White)
                    .clickable(onClick = onDismissNotice)
                    .padding(16.dp),
            )
        }
        if (shown.isEmpty()) {
            Text(
                text = if (alerts.isEmpty()) {
                    "No alerts on this phone yet. Record an SOS, or wait for a nearby phone."
                } else {
                    "No alerts in this filter."
                },
                color = Ink,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().flatCard(Color.White).padding(16.dp),
            )
        }
        shown.forEach { alert ->
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
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    BoxChip(
        label = label,
        background = if (selected) Ink else Color.White,
        foreground = if (selected) Color.White else Ink,
        onClick = onClick,
    )
}

@Composable
private fun BoxChip(label: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    val ink = alert.urgency.onCard()
    val darkArrow = alert.urgency == Urgency.NEEDS_HELP
    val clipNote = if (canPlay) " · voice clip" else ""
    InfoCard(
        background = alert.urgency.cardFill(),
        label = "${alert.urgency.label} · ${formatWhen(alert.createdAtMillis)}",
        title = alert.summary,
        details = "${formatHops(alert.hops)} · ${formatGps(alert.lat, alert.lon)}$clipNote · ${sourceLabel(alert.summarySource)}",
        ink = ink,
        arrowBackground = if (darkArrow) Ink else Color.White,
        arrowTint = if (darkArrow) Color.White else Ink,
        arrowDescription = if (open) "Hide transcript" else "Show transcript",
        onArrow = { open = !open },
        minHeight = 150.dp,
        extra = if (!open) {
            null
        } else {
            {
                Text(alert.transcript, color = ink, fontSize = 16.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (canPlay) PlayButton(onClick = onPlay)
                    TextButton(onClick = onShowQr, modifier = Modifier.height(48.dp)) {
                        Text("QR", color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.height(48.dp)) {
                        Text("Delete", color = ink, fontSize = 16.sp)
                    }
                }
            }
        },
    )
}
