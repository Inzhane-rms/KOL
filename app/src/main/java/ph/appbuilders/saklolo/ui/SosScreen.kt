package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.SosUiState
import ph.appbuilders.saklolo.stt.SpeechLanguage
import ph.appbuilders.saklolo.ui.theme.color

@Composable
fun SosScreen(
    state: SosUiState,
    contentPadding: PaddingValues,
    onLanguage: (SpeechLanguage) -> Unit,
    onRecordToggle: () -> Unit,
    onTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Saklolo",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Offline disaster SOS. The recording never leaves this phone until you send the alert.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeechLanguage.entries.forEach { language ->
                FilterChip(
                    selected = state.language == language,
                    onClick = { onLanguage(language) },
                    label = { Text(language.label) },
                )
            }
        }
        if (state.language == SpeechLanguage.BISAYA) {
            Text(
                text = "Whisper has no Cebuano language code, so Bisaya uses auto-detect. The urgency words are still matched locally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        val recordEnabled = state.modelReady || state.recording
        Box(
            modifier = Modifier
                .size(188.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !recordEnabled -> MaterialTheme.colorScheme.surfaceVariant
                        state.recording -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                .clickable(enabled = recordEnabled, onClick = onRecordToggle),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (state.recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (state.recording) "Stop recording" else "Record SOS",
                    modifier = Modifier.size(42.dp),
                )
                Text(
                    text = if (state.recording) "STOP" else "RECORD",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                )
                if (state.recording) {
                    Text("${state.elapsedSec}s", fontWeight = FontWeight.Medium)
                }
            }
        }
        Text(
            text = state.modelStatus,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = state.status,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.sentSummary?.let { summary ->
            Text(
                text = "Sent: $summary",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = state.transcript,
            onValueChange = onTranscript,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.recording,
            minLines = 3,
            label = { Text("Transcript, or type an SOS") },
            placeholder = { Text("Tatlong tao ang naipit sa Purok 4, kailangan ng tubig") },
        )
        if (state.urgency != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.urgency.label,
                color = state.urgency.color(),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            Text(
                text = state.summary,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                    Text("Discard")
                }
                Button(onClick = onSend, enabled = state.actionable, modifier = Modifier.weight(1f)) {
                    Text("Send alert")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
