package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.triage.Urgency
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.GreenText
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.NearSecondary
import ph.appbuilders.saklolo.ui.theme.PlayerWash
import ph.appbuilders.saklolo.ui.theme.tint

private enum class FeedFilter { ALL, CRITICAL, HELP, SAFE }

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
    onMarkResponding: (String) -> Unit,
) {
    var filterName by rememberSaveable { mutableStateOf(FeedFilter.ALL.name) }
    val filter = runCatching { FeedFilter.valueOf(filterName) }.getOrDefault(FeedFilter.ALL)
    val shown = when (filter) {
        FeedFilter.ALL -> alerts
        FeedFilter.CRITICAL -> alerts.filter { it.urgency == Urgency.CRITICAL }
        FeedFilter.HELP -> alerts.filter { it.urgency == Urgency.NEEDS_HELP }
        FeedFilter.SAFE -> alerts.filter { it.urgency == Urgency.SAFE }
    }
    val featured = shown.firstOrNull { it.urgency == Urgency.CRITICAL && !it.responding }
    val rest = shown.filter { it.id != featured?.id }
    val nearest = alerts.firstOrNull { it.urgency == Urgency.CRITICAL && !it.responding }
    val criticalCount = alerts.count { it.urgency == Urgency.CRITICAL }
    val helpCount = alerts.count { it.urgency == Urgency.NEEDS_HELP }
    val safeCount = alerts.count { it.urgency == Urgency.SAFE }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OfflineAiPillRow()
        Column(Modifier.fillMaxWidth()) {
            Text("Medic", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "${alerts.size} near you · sorted by urgency",
                color = InkSoft,
                fontSize = 14.sp,
            )
        }
        nearest?.let { alert ->
            NearestCritical(alert)
        }
        notice?.let { message ->
            Text(
                text = message,
                color = Ink,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().softCard().clickable(onClick = onDismissNotice).padding(16.dp),
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
                modifier = Modifier.fillMaxWidth().softCard().padding(16.dp),
            )
        }
        featured?.let { alert ->
            FeaturedAlert(
                alert = alert,
                canPlay = clipReady(alert),
                onPlay = { onPlay(alert) },
                onDelete = { onDelete(alert.id) },
                onShowQr = { onShowQr(alert) },
                onRespond = { onMarkResponding(alert.id) },
            )
        }
        rest.forEach { alert ->
            key(alert.id) {
                SwipeAlert(
                    alert = alert,
                    canPlay = clipReady(alert),
                    onPlay = { onPlay(alert) },
                    onDelete = { onDelete(alert.id) },
                    onShowQr = { onShowQr(alert) },
                    onRespond = { onMarkResponding(alert.id) },
                )
            }
        }
        if (alerts.any { !it.responding }) {
            Text(
                "Swipe left on an alert to mark it responding",
                color = InkSoft,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("All ${alerts.size}", filter == FeedFilter.ALL) { filterName = FeedFilter.ALL.name }
            FilterChip("Critical $criticalCount", filter == FeedFilter.CRITICAL) { filterName = FeedFilter.CRITICAL.name }
            FilterChip("Help $helpCount", filter == FeedFilter.HELP) { filterName = FeedFilter.HELP.name }
            FilterChip("Safe $safeCount", filter == FeedFilter.SAFE) { filterName = FeedFilter.SAFE.name }
            FilterChip("Scan", false, onScan)
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .then(if (selected) Modifier else Modifier.softCard(RoundedCornerShape(24.dp)))
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Accent else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeaturedAlert(
    alert: Alert,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    onRespond: () -> Unit,
) {
    var open by rememberSaveable(alert.id) { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard(RoundedCornerShape(28.dp))
            .border(1.5.dp, Accent, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AlertHeader(alert, open, { open = !open })
        if (canPlay) ClipPlayer(alert, onPlay)
        SourceChip(alert.summarySource)
        if (!alert.responding) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Accent)
                    .clickable(onClick = onRespond),
                contentAlignment = Alignment.Center,
            ) {
                Text("Mark responding", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (open) TranscriptBlock(alert, canPlay, onPlay, onShowQr, onDelete)
    }
}

@Composable
private fun SwipeAlert(
    alert: Alert,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    onRespond: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !alert.responding) onRespond()
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !alert.responding,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GreenText)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Responding ✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
    ) {
        CompactAlert(alert, canPlay, onPlay, onDelete, onShowQr)
    }
}

@Composable
private fun CompactAlert(
    alert: Alert,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
) {
    var open by rememberSaveable(alert.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .softCard()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlertHeader(alert, open, { open = !open })
        if (open) TranscriptBlock(alert, canPlay, onPlay, onShowQr, onDelete)
    }
}

@Composable
private fun AlertHeader(alert: Alert, open: Boolean, onArrow: () -> Unit) {
    val tint = alert.urgency.tint()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", color = tint, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(alert.summary, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(alertMeta(alert), color = InkSoft, fontSize = 11.sp, maxLines = 2)
        }
        RoundArrow(
            background = LightRed,
            tint = Accent,
            description = if (open) "Hide transcript" else "Show transcript",
            onClick = onArrow,
        )
    }
}

@Composable
private fun TranscriptBlock(
    alert: Alert,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShowQr: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(alert.transcript, color = Ink, fontSize = 16.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (canPlay) PlayButton(onClick = onPlay)
        TextButton(onClick = onShowQr, modifier = Modifier.height(48.dp)) {
            Text("QR", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        TextButton(onClick = onDelete, modifier = Modifier.height(48.dp)) {
            Text("Delete", color = Ink, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ClipPlayer(alert: Alert, onPlay: () -> Unit) {
    val path = alert.audioPath ?: return
    val bars by produceState(emptyList<Float>(), path) {
        value = withContext(Dispatchers.IO) { WavPcm.peakBars(File(path)) }
    }
    val duration by produceState("0:00", path) {
        value = withContext(Dispatchers.IO) { WavPcm.durationLabel(File(path)) }
    }
    if (bars.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PlayerWash)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayButton(onClick = onPlay)
        Row(
            Modifier.weight(1f).padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            bars.forEach { height ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((6 + 22 * height).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Accent),
                )
            }
        }
        Text(duration, color = InkSoft, fontSize = 11.sp)
    }
}

@Composable
private fun SourceChip(source: String) {
    val ai = source == "AI"
    Text(
        sourceLabel(source),
        color = if (ai) GreenText else Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PlayerWash)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun NearestCritical(alert: Alert) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Ink)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("NEAREST CRITICAL", color = NearSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            "${formatGps(alert.lat, alert.lon)} · ${formatHops(alert.hops)}",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun alertMeta(alert: Alert): String {
    val urgency = when (alert.urgency) {
        Urgency.CRITICAL -> "Critical"
        Urgency.NEEDS_HELP -> "Needs help"
        Urgency.SAFE -> "Safe"
    }
    val responding = if (alert.responding) " · Responding" else ""
    return "$urgency · ${formatWhen(alert.createdAtMillis)} · ${formatGps(alert.lat, alert.lon)} · ${formatHops(alert.hops)}$responding"
}
