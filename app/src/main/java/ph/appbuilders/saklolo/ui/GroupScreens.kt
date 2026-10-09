package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ph.appbuilders.saklolo.VoiceUiState
import ph.appbuilders.saklolo.group.ConcertGroup
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.Sighting
import ph.appbuilders.saklolo.triage.Urgency
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.Amber
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.Page

@Composable
fun GroupHomeScreen(
    groups: List<ConcertGroup>,
    active: ConcertGroup?,
    sightings: List<Sighting>,
    displayName: String,
    notice: String?,
    onDisplayName: (String) -> Unit,
    onCreate: (String) -> Unit,
    onSelect: (String) -> Unit,
    onScan: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSos: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    var groupName by rememberSaveable { mutableStateOf("") }
    var nameDraft by rememberSaveable(displayName) { mutableStateOf(displayName) }
    val seen = sightings.filter { it.groupId == active?.id }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Page)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OfflineAiPillRow()
        Text("Home", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        notice?.let {
            Text(
                it,
                color = Ink,
                modifier = Modifier.fillMaxWidth().softCard().clickable(onClick = onDismissNotice).padding(16.dp),
            )
        }
        OutlinedTextField(
            value = nameDraft,
            onValueChange = {
                nameDraft = it
                onDisplayName(it)
            },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (groups.isEmpty()) {
            Text("Join a group to talk without signal.", color = InkSoft, fontSize = 14.sp)
        } else {
            groups.forEach { group ->
                val selected = group.id == active?.id
                Text(
                    group.name,
                    color = if (selected) Accent else Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softCard()
                        .clickable { onSelect(group.id) }
                        .padding(16.dp),
                )
            }
        }
        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text("New group name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onCreate(groupName)
                groupName = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = groupName.isNotBlank(),
        ) {
            Text("Create group QR")
        }
        TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("Scan a group QR", color = Ink, fontWeight = FontWeight.Bold)
        }
        if (active != null) {
            Text("Last seen", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (seen.isEmpty()) {
                Text("No one in ${active.name} has been heard yet.", color = InkSoft, fontSize = 14.sp)
            } else {
                seen.forEach { person ->
                    Text(
                        "${person.name} · ${formatWhen(person.heardAtMillis)} · ${formatGps(person.lat, person.lon)}",
                        color = Ink,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().softCard().padding(16.dp),
                    )
                }
            }
            Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Text("Open chat")
            }
        }
        Button(
            onClick = onOpenSos,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) {
            Text("SOS")
        }
    }
}

@Composable
fun GroupChatScreen(
    group: ConcertGroup?,
    notes: List<GroupNote>,
    voice: VoiceUiState,
    notice: String?,
    onSend: (String) -> Unit,
    onSendToMedics: (String) -> Unit,
    onOpenHome: () -> Unit,
    onOpenSos: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val shown = notes.filter { it.groupId == group?.id }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Page)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 148.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OfflineAiPillRow()
            Text(group?.name ?: "Chat", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            notice?.let {
                Text(
                    it,
                    color = Ink,
                    modifier = Modifier.fillMaxWidth().softCard().clickable(onClick = onDismissNotice).padding(16.dp),
                )
            }
            if (voice.status.isNotBlank()) {
                Text(
                    if (voice.recording) "Recording ${voice.elapsedSec}s" else voice.status,
                    color = InkSoft,
                    fontSize = 14.sp,
                )
            }
            if (shown.isEmpty()) {
                Text(
                    "Hold Record to send a voice note, or type below.",
                    color = Ink,
                    modifier = Modifier.fillMaxWidth().softCard().padding(16.dp),
                )
            }
            shown.forEach { note ->
                Column(
                    Modifier.fillMaxWidth().softCard().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(note.sender, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(note.body, color = Ink, fontSize = 16.sp)
                    note.urgency?.let { urgency ->
                        Text(
                            urgency.label,
                            color = if (urgency == Urgency.CRITICAL) Accent else Amber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        TextButton(onClick = { onSendToMedics(note.id) }) {
                            Text("Send to medics", color = Accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenHome) { Text("Home", color = Ink) }
            TextButton(onClick = onOpenSos) { Text("SOS", color = Accent) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message the group") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(draft)
                    draft = ""
                }),
            )
            TextButton(onClick = {
                onSend(draft)
                draft = ""
            }) {
                Text("Send", color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GroupQrDialog(name: String, payload: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Image(
                bitmap = qrBitmap(payload).asImageBitmap(),
                contentDescription = "Group QR code",
                modifier = Modifier.padding(vertical = 12.dp).size(240.dp),
            )
            TextButton(onClick = onDismiss) { Text("Close", color = Ink) }
        }
    }
}
