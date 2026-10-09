package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ph.appbuilders.saklolo.RelayUiState
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.ui.theme.color

@Composable
fun ResponderScreen(
    alerts: List<Alert>,
    relay: RelayUiState,
    contentPadding: PaddingValues,
    onRelay: (Boolean) -> Unit,
    onScan: () -> Unit,
    onDelete: (String) -> Unit,
    qrFor: (Alert) -> String,
    onDismissNotice: () -> Unit,
) {
    var qrAlert by remember { mutableStateOf<Alert?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Responder feed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Critical alerts stay on top. Phones rebroadcast what they have not seen yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (relay.enabled) "Relay on" else "Relay off", fontWeight = FontWeight.Bold)
                    Text(
                        relay.message + if (relay.peers > 0) " · ${relay.peers} connected" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = relay.enabled, onCheckedChange = onRelay)
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.padding(top = 8.dp)) {
                Text("Scan alert QR")
            }
            relay.notice?.let { notice ->
                TextButton(onClick = onDismissNotice) { Text(notice) }
            }
        }
        if (alerts.isEmpty()) {
            Text(
                "No alerts yet. Record one on the SOS tab, or receive one from a nearby phone.",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(alerts, key = { it.id }) { alert ->
                AlertCard(alert, onDelete = { onDelete(alert.id) }, onQr = { qrAlert = alert })
            }
        }
    }
    qrAlert?.let { alert ->
        val bitmap = remember(alert.id, alert.summary) { qrBitmap(qrFor(alert)) }
        AlertDialog(
            onDismissRequest = { qrAlert = null },
            confirmButton = { TextButton(onClick = { qrAlert = null }) { Text("Close") } },
            title = { Text(alert.summary) },
            text = {
                Column {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR code for this alert",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                    Text(
                        "The other phone can scan this with Saklolo if nearby radio does not connect.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun AlertCard(alert: Alert, onDelete: () -> Unit, onQr: () -> Unit) {
    var expanded by remember(alert.id) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Spacer(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(alert.urgency.color()),
            )
            Column(Modifier.padding(14.dp).weight(1f)) {
                Text(alert.urgency.label, color = alert.urgency.color(), fontWeight = FontWeight.Black)
                Text(alert.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatWhen(alert.createdAtMillis)} · ${formatGps(alert.lat, alert.lon)} · ${hopLabel(alert.hops)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (expanded && alert.transcript.isNotBlank()) {
                    Text(alert.transcript, modifier = Modifier.padding(top = 8.dp))
                }
                Row {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide transcript" else "Transcript")
                    }
                    TextButton(onClick = onQr) { Text("QR") }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete alert")
                    }
                }
            }
        }
    }
}

private fun hopLabel(hops: Int): String = when (hops) {
    0 -> "recorded here or direct"
    1 -> "1 hop"
    else -> "$hops hops"
}
