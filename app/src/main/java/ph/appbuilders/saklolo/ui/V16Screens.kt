package ph.appbuilders.saklolo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import java.io.File
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import ph.appbuilders.saklolo.SosUiState
import ph.appbuilders.saklolo.VoiceUiState
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.group.ConcertGroup
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupQr
import ph.appbuilders.saklolo.group.Sighting
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.stt.PcmRecorder
import ph.appbuilders.saklolo.triage.Urgency
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.Amber
import ph.appbuilders.saklolo.ui.theme.CardWhite
import ph.appbuilders.saklolo.ui.theme.Cyan
import ph.appbuilders.saklolo.ui.theme.CyanLight
import ph.appbuilders.saklolo.ui.theme.CyanText
import ph.appbuilders.saklolo.ui.theme.GreenText
import ph.appbuilders.saklolo.ui.theme.Hairline
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.Peach
import ph.appbuilders.saklolo.ui.theme.PeachLight
import ph.appbuilders.saklolo.ui.theme.PeachText
import ph.appbuilders.saklolo.ui.theme.PillAmberBg
import ph.appbuilders.saklolo.ui.theme.PillAmberText
import ph.appbuilders.saklolo.ui.theme.Poppins
import ph.appbuilders.saklolo.ui.theme.StatusGreen
import ph.appbuilders.saklolo.ui.theme.Violet
import ph.appbuilders.saklolo.ui.theme.VioletDeep
import ph.appbuilders.saklolo.ui.theme.VioletGradStart
import ph.appbuilders.saklolo.ui.theme.VioletLight

private val VioletBrush = Brush.linearGradient(listOf(VioletGradStart, VioletDeep))
private val CardShape = RoundedCornerShape(24.dp)
private val AvatarColors = listOf(Color(0xFFF472B6), Color(0xFF60A5FA), Amber, StatusGreen, Violet)

internal fun greeting(name: String, hour: Int): String {
    val hello = when (hour) {
        in 5..11 -> "Magandang umaga"
        in 12..17 -> "Magandang hapon"
        else -> "Magandang gabi"
    }
    return "$hello, $name"
}

internal fun displayCode(groupId: String): String {
    val compact = groupId.replace("-", "").uppercase()
    return compact.take(4) + " · " + compact.drop(4).take(4)
}

internal fun lostCompanion(alert: Alert): Boolean {
    val text = (alert.transcript + " " + alert.summary).lowercase()
    return "nawawala" in text || "missing" in text || "lost" in text
}

@Composable
fun V16Frame(
    peers: Int,
    route: String,
    unread: Int,
    recording: Boolean,
    onHome: () -> Unit,
    onChat: () -> Unit,
    onRecord: () -> Unit,
    onFind: () -> Unit,
    onSos: () -> Unit,
    notice: String? = null,
    onDismissNotice: () -> Unit = {},
    sheet: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Page)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 128.dp),
        ) {
            NearbyPill(peers)
            notice?.let {
                Text(
                    it,
                    color = VioletDeep,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).clickable(onClick = onDismissNotice),
                )
            }
            content()
        }
        if (sheet != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.42f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
            )
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)) {
                sheet()
            }
        }
        V16TabBar(
            route = route,
            unread = unread,
            recording = recording,
            onHome = onHome,
            onChat = onChat,
            onRecord = onRecord,
            onFind = onFind,
            onSos = onSos,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun NearbyPill(peers: Int) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .height(36.dp)
            .clip(CircleShape)
            .background(CardWhite)
            .border(1.dp, Hairline, CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Cyan))
        Text(
            "Offline · $peers nearby",
            color = Ink,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
fun V16TabBar(
    route: String,
    unread: Int,
    recording: Boolean,
    onHome: () -> Unit,
    onChat: () -> Unit,
    onRecord: () -> Unit,
    onFind: () -> Unit,
    onSos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .height(100.dp),
    ) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(68.dp)
                .shadow(8.dp, RoundedCornerShape(34.dp), ambientColor = Violet.copy(alpha = 0.10f), spotColor = Violet.copy(alpha = 0.10f))
                .clip(RoundedCornerShape(34.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(34.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabSlot("Home", Icons.Filled.Home, route == "home", false, 0, Modifier.weight(1f), onHome)
            TabSlot("Barkada", Icons.Outlined.ChatBubbleOutline, route == "chat", false, unread, Modifier.weight(1f), onChat)
            Spacer(Modifier.width(72.dp))
            TabSlot("Find", Icons.Filled.LocationOn, route == "find", false, 0, Modifier.weight(1f), onFind)
            TabSlot("SOS", Icons.Filled.Notifications, route == "feed", true, 0, Modifier.weight(1f), onSos)
        }
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Page))
                Box(
                    Modifier
                        .size(68.dp)
                        .shadow(8.dp, CircleShape, ambientColor = Violet.copy(alpha = 0.10f), spotColor = Violet.copy(alpha = 0.10f))
                        .clip(CircleShape)
                        .background(VioletBrush)
                        .clickable(onClick = onRecord),
                    contentAlignment = Alignment.Center,
                ) {
                    if (recording) {
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
                    } else {
                        Icon(Icons.Filled.Mic, contentDescription = "Record", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Text(
                if (recording) "Stop" else "Record",
                color = if (recording) Violet else InkSoft,
                fontFamily = Poppins,
                fontWeight = if (recording) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TabSlot(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    sos: Boolean,
    badge: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tint = when {
        sos -> Accent
        selected -> Violet
        else -> InkSoft
    }
    Column(
        modifier.height(68.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(width = 56.dp, height = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sos) LightRed else VioletLight),
                )
            }
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-6).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(PeachText),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 9) "9+" else badge.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Text(label, color = tint, fontFamily = Poppins, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 11.sp)
    }
}

@Composable
fun V16Home(
    displayName: String,
    group: ConcertGroup?,
    notes: List<GroupNote>,
    sightings: List<Sighting>,
    onOpenChat: () -> Unit,
    onOpenJoin: () -> Unit,
    onOpenFind: () -> Unit,
    onPing: () -> Unit,
    onOpenSos: () -> Unit,
    onPlay: (String?) -> Unit,
    onSettings: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val latest = notes.filter { it.kind != "ping" }.maxByOrNull { it.createdAtMillis }
    val heard = sightings.filter { it.groupId == group?.id }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(greeting(displayName, hour), color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, modifier = Modifier.weight(1f))
            Text("Settings", color = Violet, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onSettings).padding(top = 8.dp))
        }
        Text("Tap the mic to talk with your barkada.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp)
        HeroCard(group, heard.size, latest, onOpenChat)
        FindSummary(heard, onOpenFind)
        LatestNote(latest, onPlay)
        Text("Quick actions", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile("Join QR", Icons.Filled.QrCode, Violet, VioletLight, onOpenJoin, Modifier.weight(1f))
            QuickTile("Find", Icons.Filled.LocationOn, PeachText, PeachLight, onOpenFind, Modifier.weight(1f))
            QuickTile("Send ping", Icons.Filled.Notifications, CyanText, CyanLight, onPing, Modifier.weight(1f))
            QuickTile("SOS", Icons.Filled.Notifications, Accent, LightRed, onOpenSos, Modifier.weight(1f), sos = true)
        }
    }
}

@Composable
private fun HeroCard(group: ConcertGroup?, members: Int, latest: GroupNote?, onOpenChat: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = Violet.copy(alpha = 0.10f), spotColor = Violet.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(28.dp))
            .background(VioletBrush)
            .padding(16.dp),
    ) {
        Text(group?.name ?: "Barkada", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(
            if (group == null) "Join a group to start" else "$members heard on this phone",
            color = Color.White.copy(alpha = 0.9f),
            fontFamily = Poppins,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        latest?.let {
            Text(
                "${it.sender}: ${it.body}",
                color = Color(0xFFE4DDFF),
                fontFamily = Poppins,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .clickable(onClick = onOpenChat),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Open chat", color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FindSummary(heard: List<Sighting>, onOpen: () -> Unit) {
    val now = System.currentTimeMillis()
    val line = if (heard.isEmpty()) {
        "No one heard yet"
    } else {
        val oldestMin = heard.maxOf { (now - it.heardAtMillis).coerceAtLeast(0) } / 60_000
        val whenText = if (oldestMin < 1) "the last minute" else "$oldestMin min"
        "All ${heard.size} heard from in $whenText"
    }
    val missing = heard.count { it.lat == null || it.lon == null }
    val sub = when {
        heard.isEmpty() -> "Last heard shows up here"
        missing == 0 -> "A location came with the latest notes"
        else -> "$missing without GPS"
    }
    Row(
        Modifier.fillMaxWidth().v16Card().clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(CyanLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = CyanText)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(line, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(sub, color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LatestNote(note: GroupNote?, onPlay: (String?) -> Unit) {
    Column(Modifier.fillMaxWidth().v16Card().padding(16.dp)) {
        if (note == null) {
            Text("No voice note yet", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            return
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(note.sender, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Latest · ${formatWhen(note.createdAtMillis)}", color = InkSoft, fontFamily = Poppins, fontSize = 11.sp)
        }
        if (note.audioPath != null) {
            VoiceWave(note.audioPath, mine = false, onPlay = { onPlay(note.audioPath) }, modifier = Modifier.padding(top = 10.dp))
        }
        Text(note.body, color = Ink, fontFamily = Poppins, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun QuickTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    wash: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    sos: Boolean = false,
) {
    Column(
        modifier
            .height(96.dp)
            .then(if (sos) Modifier.clip(RoundedCornerShape(22.dp)).background(LightRed).border(1.dp, Color(0xFFF9CFCF), RoundedCornerShape(22.dp)) else Modifier.v16Card(22))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (sos) Color.White else wash), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, color = if (sos) Accent else Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
    }
}

@Composable
fun V16Chat(
    group: ConcertGroup?,
    notes: List<GroupNote>,
    displayName: String,
    memberCount: Int,
    onSend: (String) -> Unit,
    onPlay: (String?) -> Unit,
    onSendToMedics: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val shown = notes.filter { it.groupId == group?.id }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(group?.name ?: "Barkada", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        Text(
            "$memberCount heard · voice notes up to 0:${PcmRecorder.MAX_SECONDS}",
            color = InkSoft,
            fontFamily = Poppins,
            fontSize = 12.sp,
        )
        shown.forEach { note ->
            if (note.kind == "ping") {
                Text(
                    "${note.sender} pinged · ${formatWhen(note.createdAtMillis)}",
                    color = InkSoft,
                    fontFamily = Poppins,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                NoteBubble(note, mine = note.sender == displayName, onPlay, onSendToMedics)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Papunta na", "Saan kayo?", "Wait lang").forEach { chip ->
                Text(
                    chip,
                    color = Ink,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardWhite)
                        .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                        .clickable { onSend(chip) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .v16Card(28)
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message ${group?.name ?: "Barkada"}…", fontFamily = Poppins, color = InkSoft) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(draft)
                    draft = ""
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Violet)
                    .clickable {
                        onSend(draft)
                        draft = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
private fun NoteBubble(
    note: GroupNote,
    mine: Boolean,
    onPlay: (String?) -> Unit,
    onSendToMedics: (String) -> Unit,
) {
    val voice = note.kind == "voice" || note.audioPath != null
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(
            "${note.sender} · ${formatWhen(note.createdAtMillis)}",
            color = InkSoft,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
        Column(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(22.dp))
                .background(if (mine) Violet else CardWhite)
                .border(1.dp, if (mine) Color.Transparent else Hairline, RoundedCornerShape(22.dp))
                .padding(12.dp),
        ) {
            if (voice && note.audioPath != null) {
                VoiceWave(note.audioPath, mine, { onPlay(note.audioPath) })
            }
            Text(
                note.body,
                color = if (mine) Color.White else Ink,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (voice) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AiTag()
                }
            }
            note.urgency?.let { urgency ->
                Text(
                    urgency.label,
                    color = if (mine) Color.White else if (urgency == Urgency.CRITICAL) Accent else Amber,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Send to medics",
                    color = if (mine) Color.White else Accent,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable { onSendToMedics(note.id) },
                )
            }
        }
        Text(
            when {
                note.hops <= 0 -> "Sent"
                note.hops == 1 -> "Sent · 1 hop"
                else -> "Sent · ${note.hops} hops"
            },
            color = if (mine) Violet else InkSoft,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AiTag() {
    Row(
        Modifier.height(22.dp).clip(CircleShape).background(CyanLight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Cyan))
        Text("On-device AI", color = CyanText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun VoiceWave(path: String, mine: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val bars by produceState(emptyList<Float>(), path) {
        value = withContext(Dispatchers.IO) { WavPcm.peakBars(File(path), 24) }
    }
    val duration = remember(path) { WavPcm.durationLabel(File(path)) }
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (mine) Color.White else Violet).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play voice note", tint = if (mine) Violet else Color.White, modifier = Modifier.size(18.dp))
        }
        Row(Modifier.weight(1f).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val shown = if (bars.isEmpty()) List(12) { 0.2f } else bars
            shown.forEach { peak ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((8 + 22 * peak).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (mine) Color.White else Violet),
                )
            }
        }
        Text(duration, color = if (mine) Color.White else InkSoft, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun V16RecordSheet(groupName: String, voice: VoiceUiState, members: Int, onCancel: () -> Unit, onSend: () -> Unit) {
    val left = (PcmRecorder.MAX_SECONDS - voice.elapsedSec).coerceAtLeast(0)
    val fraction = (voice.elapsedSec / PcmRecorder.MAX_SECONDS.toFloat()).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(CardWhite)
            .padding(20.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Hairline))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Violet))
            Text(
                "Recording to $groupName",
                color = Ink,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text("$members heard", color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(VioletLight).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Text(
            if (voice.recording) "Tap the center button again to stop" else "Transcript appears after you stop",
            color = InkSoft,
            fontFamily = Poppins,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp)).background(Page).padding(16.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(24) { index ->
                    val shape = 0.35f + 0.65f * sin(index * 0.7).toFloat().let { abs(it) }
                    val height = 6f + 28f * voice.micLevel.coerceIn(0f, 1f) * shape
                    Box(Modifier.width(4.dp).height(height.dp).clip(RoundedCornerShape(2.dp)).background(if (index < (24 * fraction).toInt()) Violet else Color(0xFFD9D1FB)))
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format("%d:%02d", voice.elapsedSec / 60, voice.elapsedSec % 60), color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                Text(" / 0:${PcmRecorder.MAX_SECONDS}", color = InkSoft, fontFamily = Poppins, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("$left s left", color = InkSoft, fontFamily = Poppins, fontSize = 11.sp)
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Hairline)) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(Violet))
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(22.dp)).background(VioletLight).padding(16.dp)) {
            AiTag()
            Text(
                when {
                    voice.transcript.isNotBlank() -> voice.transcript
                    voice.status == "Transcribing…" -> "Transcribing…"
                    voice.status.isNotBlank() -> voice.status
                    else -> "Transcript starts when you stop"
                },
                color = Ink,
                fontFamily = Poppins,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(0.7f).height(56.dp).clip(RoundedCornerShape(28.dp)).border(1.5.dp, Hairline, RoundedCornerShape(28.dp)).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(VioletBrush).clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Text("Send now", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun V16Find(
    sightings: List<Sighting>,
    displayName: String,
    onPing: () -> Unit,
    onRefresh: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val justNow = sightings.count { now - it.heardAtMillis < 120_000 }
    val earlier = sightings.count { now - it.heardAtMillis >= 120_000 }
    val noGps = sightings.count { it.lat == null || it.lon == null }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Find my group", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        Text("Last heard via nearby phones · no map", color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().v16Card(28).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                listOf(130 to Page, 96 to VioletLight, 62 to Color(0xFFE2DAFF)).forEach { (radius, color) ->
                    Box(Modifier.size(radius.dp).clip(CircleShape).background(color))
                }
                Avatar(displayName, 18, self = true)
                sightings.take(6).forEachIndexed { index, person ->
                    val minutes = ((now - person.heardAtMillis).coerceAtLeast(0) / 60_000f).coerceAtMost(40f)
                    val radius = 28f + minutes * 1.4f
                    val angle = index * 1.15f
                    Box(
                        Modifier.offset(
                            x = (cos(angle) * radius).dp,
                            y = (sin(angle) * radius).dp,
                        ),
                    ) {
                        Avatar(person.name, 14, self = false, missingGps = person.lat == null)
                    }
                }
            }
            Column(Modifier.padding(start = 8.dp)) {
                LegendCount(justNow.toString(), "Just now", Violet)
                LegendCount(earlier.toString(), "Earlier", CyanText)
                LegendCount(noGps.toString(), "No GPS", Amber)
            }
        }
        Column(Modifier.fillMaxWidth().v16Card()) {
            if (sightings.isEmpty()) {
                Text("No one heard yet", color = Ink, fontFamily = Poppins, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }
            sightings.forEachIndexed { index, person ->
                if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 72.dp).height(1.dp).background(Hairline))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.name, 16, self = person.name == displayName, missingGps = person.lat == null)
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(person.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(formatWhen(person.heardAtMillis), color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
                    }
                    val shared = person.lat != null && person.lon != null
                    Text(
                        if (shared) "GPS shared" else "No GPS",
                        color = if (shared) GreenText else PillAmberText,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (shared) Color(0xFFE3F8EB) else PillAmberBg).padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1.4f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(VioletBrush).clickable(onClick = onPing),
                contentAlignment = Alignment.Center,
            ) {
                Text("Ping group", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Box(
                Modifier.weight(0.8f).height(56.dp).clip(RoundedCornerShape(28.dp)).border(1.5.dp, Hairline, RoundedCornerShape(28.dp)).clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                Text("Refresh", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LegendCount(value: String, label: String, color: Color) {
    Text(value, color = color, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    Text(label, color = InkSoft, fontFamily = Poppins, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Avatar(name: String, radius: Int, self: Boolean, missingGps: Boolean = false) {
    val color = if (self) Violet else AvatarColors[abs(name.hashCode()) % AvatarColors.size]
    Box(
        Modifier
            .size((radius * 2).dp)
            .clip(CircleShape)
            .background(color)
            .border(if (missingGps) 1.5.dp else 0.dp, Amber, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = (radius * 0.8f).sp)
    }
}

@Composable
fun V16Join(
    group: ConcertGroup?,
    memberCount: Int,
    displayName: String,
    onDisplayName: (String) -> Unit,
    onCreate: (String) -> Unit,
    onScan: () -> Unit,
) {
    val context = LocalContext.current
    var groupName by remember { mutableStateOf("") }
    var yourName by remember(displayName) { mutableStateOf(displayName) }
    val payload = group?.let { GroupQr.encode(it.id, it.name) }
    Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Join your Barkada", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Scan a friend’s phone. No signal needed.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (payload != null) {
            Image(
                bitmap = qrBitmap(payload).asImageBitmap(),
                contentDescription = "Group QR code",
                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).padding(12.dp),
            )
            Text(
                displayCode(group.id),
                color = Ink,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardWhite)
                    .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                    .clickable { copyText(context, payload) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
            Text(
                if (group.createdHere) "Created by you · ${formatWhen(group.joinedAtMillis)}" else "Joined · ${formatWhen(group.joinedAtMillis)}",
                color = InkSoft,
                fontFamily = Poppins,
                fontSize = 12.sp,
            )
            Text("$memberCount heard", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        OutlinedTextField(value = yourName, onValueChange = { yourName = it; onDisplayName(it) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("New group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(VioletBrush).clickable(onClick = onScan),
                contentAlignment = Alignment.Center,
            ) {
                Text("Scan to join", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).border(1.5.dp, Violet, RoundedCornerShape(28.dp)).clickable {
                    payload?.let { shareText(context, it) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text("Share code", color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(VioletLight).clickable(enabled = groupName.isNotBlank()) { onCreate(groupName) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Create", color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun V16Sos(
    sos: SosUiState,
    alerts: List<Alert>,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onHoldCancel: () -> Unit,
    onUndo: () -> Unit,
    onSendDraft: () -> Unit,
    onDiscard: () -> Unit,
    onPlay: (Alert) -> Unit,
    onRespond: (String) -> Unit,
    clipReady: (Alert) -> Boolean,
) {
    var medic by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("all") }
    val lost = alerts.count { lostCompanion(it) }
    val done = alerts.count { it.responding }
    val critical = alerts.count { it.urgency == Urgency.CRITICAL }
    val shown = when (filter) {
        "critical" -> alerts.filter { it.urgency == Urgency.CRITICAL }
        "lost" -> alerts.filter { lostCompanion(it) }
        "done" -> alerts.filter { it.responding }
        else -> alerts
    }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.align(Alignment.CenterHorizontally).height(48.dp).clip(RoundedCornerShape(24.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(24.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segment("Send SOS", !medic) { medic = false }
            Segment("Medic feed", medic) { medic = true }
        }
        if (!medic || sos.actionable || sos.canUndo || sos.recording) {
            Text("Send SOS", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (sos.canUndo) {
                Text("Cancelled", color = Ink, fontFamily = Poppins, fontSize = 14.sp)
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Ink).clickable(onClick = onUndo), contentAlignment = Alignment.Center) {
                    Text("Undo", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold)
                }
            }
            if (sos.transcript.isNotBlank()) {
                Text(sos.transcript, color = Ink, fontFamily = Poppins, fontSize = 14.sp, modifier = Modifier.v16Card().padding(16.dp))
            }
            sos.urgency?.let {
                Text(it.label, color = if (it == Urgency.CRITICAL) Accent else Amber, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            if (sos.summary.isNotBlank()) {
                Text(sos.summary, color = InkSoft, fontFamily = Poppins, fontSize = 13.sp)
            }
            if (sos.actionable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Accent).clickable(onClick = onSendDraft), contentAlignment = Alignment.Center) {
                        Text("Send alert", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).border(1.dp, Hairline, RoundedCornerShape(28.dp)).clickable(onClick = onDiscard), contentAlignment = Alignment.Center) {
                        Text("Discard", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            sos.error?.let { Text(it, color = Accent, fontFamily = Poppins, fontSize = 13.sp) }
        }
        if (medic) {
            Text("${alerts.size} SOS nearby", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Sorted by urgency", color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All ${alerts.size}", filter == "all") { filter = "all" }
                FilterChip("Critical $critical", filter == "critical") { filter = "critical" }
                FilterChip("Lost $lost", filter == "lost") { filter = "lost" }
                FilterChip("Done $done", filter == "done") { filter = "done" }
            }
            shown.forEach { alert ->
                MedicCard(alert, clipReady(alert), { onPlay(alert) }, { onRespond(alert.id) })
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LightRed).padding(8.dp)) {
            HoldSosButton(recording = sos.recording, elapsedSec = sos.elapsedSec, enabled = sos.modelReady, onHoldStart = onHoldStart, onHoldEnd = onHoldEnd, onHoldCancel = onHoldCancel)
            Text(
                "Sends to everyone nearby, not just your group",
                color = Accent,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            Text("Slide away to cancel", color = Accent, fontFamily = Poppins, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) VioletLight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) VioletDeep else InkSoft, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else Ink,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) Violet else CardWhite)
            .border(1.dp, if (on) Violet else Hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun MedicCard(alert: Alert, canPlay: Boolean, onPlay: () -> Unit, onRespond: () -> Unit) {
    val critical = alert.urgency == Urgency.CRITICAL
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CardWhite)
            .border(if (critical && !alert.responding) 1.5.dp else 1.dp, if (critical && !alert.responding) Accent else Hairline, RoundedCornerShape(26.dp))
            .padding(16.dp),
    ) {
        Text(alert.summary, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "${alert.urgency.label.lowercase().replaceFirstChar { it.uppercase() }} · ${formatWhen(alert.createdAtMillis)} · ${formatHops(alert.hops)}",
            color = InkSoft,
            fontFamily = Poppins,
            fontSize = 12.sp,
        )
        if (canPlay) {
            Text("Play voice", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onPlay))
        }
        Text("“${alert.transcript}”", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        if (alert.summarySource == "AI") {
            Box(Modifier.padding(top = 8.dp)) { AiTag() }
        }
        Text(formatGps(alert.lat, alert.lon), color = InkSoft, fontFamily = Poppins, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        if (!alert.responding) {
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Accent).clickable(onClick = onRespond),
                contentAlignment = Alignment.Center,
            ) {
                Text("Mark responding", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        } else {
            Text("Responding", color = GreenText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun HoldSosButton(
    recording: Boolean,
    elapsedSec: Int,
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onHoldCancel: () -> Unit,
) {
    val recordingNow by rememberUpdatedState(recording)
    val enabledNow by rememberUpdatedState(enabled)
    val elapsedNow by rememberUpdatedState(elapsedSec)
    val startNow by rememberUpdatedState(onHoldStart)
    val endNow by rememberUpdatedState(onHoldEnd)
    val cancelNow by rememberUpdatedState(onHoldCancel)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Accent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!(enabledNow || recordingNow)) return@awaitEachGesture
                    down.consume()
                    if (!recordingNow) startNow()
                    val slopPx = CANCEL_PAST_EDGE_DP.dp.toPx()
                    var outcome = SosHoldEnd.Release
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { it.consume() }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            val decision = if (change == null) {
                                sosHoldEnd(true, 0f, slopPx, elapsedNow * 1_000L, cancelled = true)
                            } else {
                                val past = distanceOutsideRect(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat())
                                sosHoldEnd(change.pressed, past, slopPx, elapsedNow * 1_000L, cancelled = false)
                            }
                            if (decision != SosHoldEnd.Continue) {
                                outcome = decision
                                break
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        cancelNow()
                        throw cancelled
                    }
                    if (outcome == SosHoldEnd.Cancel) cancelNow() else endNow()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (recording) "Recording ${elapsedSec}s" else "Hold to send SOS",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

private fun Modifier.v16Card(radius: Int = 24): Modifier = this
    .shadow(8.dp, RoundedCornerShape(radius.dp), ambientColor = Violet.copy(alpha = 0.10f), spotColor = Violet.copy(alpha = 0.10f))
    .clip(RoundedCornerShape(radius.dp))
    .background(CardWhite)
    .border(1.dp, Hairline, RoundedCornerShape(radius.dp))

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("B-LINK group", text))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share code"))
}
