package ph.appbuilders.saklolo.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.CallUi
import ph.appbuilders.saklolo.CaptionLine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ph.appbuilders.saklolo.audio.Waveform
import ph.appbuilders.saklolo.contact.CaptionDisplay
import ph.appbuilders.saklolo.contact.ContactRow
import ph.appbuilders.saklolo.contact.Conversation
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.NameChoice
import ph.appbuilders.saklolo.contact.ChipTapGuard
import ph.appbuilders.saklolo.contact.ReplyChip
import ph.appbuilders.saklolo.contact.Urgent
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.Amber
import ph.appbuilders.saklolo.ui.theme.CardWhite
import ph.appbuilders.saklolo.ui.theme.Cyan
import ph.appbuilders.saklolo.ui.theme.CyanLight
import ph.appbuilders.saklolo.ui.theme.CyanText
import ph.appbuilders.saklolo.ui.theme.Hairline
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.PillAmberBg
import ph.appbuilders.saklolo.ui.theme.PillAmberText
import ph.appbuilders.saklolo.ui.theme.Poppins
import ph.appbuilders.saklolo.ui.theme.StatusGreen
import ph.appbuilders.saklolo.ui.theme.Violet
import ph.appbuilders.saklolo.ui.theme.VioletDeep
import ph.appbuilders.saklolo.ui.theme.VioletLight

private val GreyIcon = Color(0xFFB3ADC6)
private val CallBrush = Brush.linearGradient(listOf(StatusGreen, Violet))
private val VioletBrush = Brush.linearGradient(listOf(Color(0xFF9479FF), Violet))

private val avatarColors = listOf(
    Color(0xFFF472B6),
    Color(0xFF60A5FA),
    Color(0xFF34D399),
    Color(0xFFFB923C),
    Color(0xFFF59E0B),
    Color(0xFFA78BFA),
)

fun bluetoothOn(context: Context): Boolean = try {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    manager?.adapter?.isEnabled == true
} catch (_: SecurityException) {
    false
}

private fun initial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

private fun avatarColor(name: String): Color = avatarColors[name.hashCode().mod(avatarColors.size).let { if (it < 0) it + avatarColors.size else it }]

private fun ago(deltaMillis: Long): String {
    val minutes = deltaMillis / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} hr ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}

private fun rangeLine(row: ContactRow, now: Long): String {
    if (row.inRange) {
        val fresh = row.lastHeardMillis == 0L || now - row.lastHeardMillis < 120_000
        return if (fresh) "In range · heard just now" else "In range · heard ${ago(now - row.lastHeardMillis)}"
    }
    if (row.lastHeardMillis == 0L) return "Not heard yet"
    return "Last heard ${ago(now - row.lastHeardMillis)}"
}

@Composable
fun V17Scaffold(
    route: String,
    unread: Int,
    showTabs: Boolean,
    notice: String?,
    onDismissNotice: () -> Unit,
    onContacts: () -> Unit,
    onMessages: () -> Unit,
    onCall: () -> Unit,
    onAdd: () -> Unit,
    overlay: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Page)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = if (showTabs) 108.dp else 12.dp),
        ) {
            if (notice != null) {
                Text(
                    notice,
                    color = VioletDeep,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).clickable(onClick = onDismissNotice),
                )
            }
            content()
        }
        if (overlay != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.28f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (showTabs) 96.dp else 16.dp, start = 16.dp, end = 16.dp),
            ) { overlay() }
        }
        if (showTabs) {
            V17TabBar(
                route = route,
                unread = unread,
                onContacts = onContacts,
                onMessages = onMessages,
                onCall = onCall,
                onAdd = onAdd,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun V17TabBar(
    route: String,
    unread: Int,
    onContacts: () -> Unit,
    onMessages: () -> Unit,
    onCall: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(34.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                TabItem("Contacts", Icons.Filled.Person, route == MainNav.CONTACTS, 0, onContacts, Modifier.weight(1f))
                TabItem("Messages", Icons.Outlined.ChatBubbleOutline, route == MainNav.MESSAGES, unread, onMessages, Modifier.weight(1f))
            }
            Spacer(Modifier.width(68.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TabItem("Add", Icons.Outlined.QrCode, route == MainNav.ADD, 0, onAdd, Modifier)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(68.dp)
                .clip(CircleShape)
                .background(VioletBrush)
                .clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val tint = if (selected) Violet else InkSoft
    Column(
        modifier.height(62.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) VioletLight else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            }
            if (badge > 0 && !selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Violet),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge.coerceAtMost(9).toString(), color = Color.White, fontSize = 9.sp, fontFamily = Poppins, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(label, color = tint, fontFamily = Poppins, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
    }
}

@Composable
fun OfflinePill(right: String? = null, onRight: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(36.dp).clip(CircleShape).background(CardWhite).border(1.dp, Hairline, CircleShape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Cyan))
            Text("Offline · on-device AI", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        if (right != null) {
            val setup = right.startsWith("Setup")
            val tap = if (onRight != null) Modifier.clickable(onClick = onRight) else Modifier
            Row(
                tap
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (setup) PillAmberBg else CardWhite)
                    .border(1.dp, if (setup) Amber else Hairline, CircleShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (setup) Amber else Cyan))
                Text(
                    right,
                    color = if (setup) PillAmberText else Ink,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(CardWhite).border(1.dp, Hairline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
    }
}

@Composable
private fun Avatar(name: String, size: Int, inRange: Boolean) {
    val letter = initial(name)
    Box(contentAlignment = Alignment.Center) {
        if (inRange) {
            Box(Modifier.size((size + 8).dp).clip(CircleShape).border(2.5.dp, Cyan, CircleShape))
        }
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(if (inRange) avatarColor(name) else avatarColor(name).copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (size / 2.4f).sp)
        }
    }
}

@Composable
private fun CallDot(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (on) VioletBrush else Brush.linearGradient(listOf(Hairline, Hairline)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Call, contentDescription = "Call", tint = if (on) Color.White else GreyIcon, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun V17Contacts(
    rows: List<ContactRow>,
    filter: String,
    onFilter: (String) -> Unit,
    now: Long,
    onOpen: (ContactRow) -> Unit,
    onCall: (ContactRow) -> Unit,
    onScan: () -> Unit,
    status: String,
    onStatus: () -> Unit,
    onAbout: () -> Unit,
) {
    val inRange = rows.count { it.inRange }
    val favorites = rows.count { it.favorite }
    val shown = when (filter) {
        "range" -> rows.filter { it.inRange }
        "favorites" -> rows.filter { it.favorite }
        else -> rows
    }
    val near = shown.filter { it.inRange }
    val far = shown.filter { !it.inRange }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            OfflinePill(right = status, onRight = onStatus)
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Contacts", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.weight(1f))
                Text(
                    "About",
                    color = VioletDeep,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onAbout),
                )
            }
            Text("Tap a name to open · call anyone in range", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        }
        item {
            SectionTitle("In range now", "$inRange heard just now", CyanText)
        }
        item {
            ContactCard(near, now, onOpen, onCall, empty = "Nobody in range yet")
        }
        item {
            Spacer(Modifier.height(14.dp))
            SectionTitle("Out of range", "${far.size} · call when back", InkSoft)
        }
        item { ContactCard(far, now, onOpen, onCall, empty = "No one waiting") }
        item {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip("All ${rows.size}", filter == "all") { onFilter("all") }
                FilterChip("In range $inRange", filter == "range") { onFilter("range") }
                FilterChip("Favorites $favorites", filter == "favorites") { onFilter("favorites") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(24.dp)).background(VioletLight).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(CardWhite), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.QrCode, contentDescription = null, tint = Violet)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Add contact", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Scan their QR face to face", color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
                }
                Box(
                    Modifier.height(56.dp).width(116.dp).clip(RoundedCornerShape(28.dp)).background(VioletBrush).clickable(onClick = onScan),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Scan", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String, right: String, rightColor: Color) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(right, color = rightColor, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ContactCard(
    rows: List<ContactRow>,
    now: Long,
    onOpen: (ContactRow) -> Unit,
    onCall: (ContactRow) -> Unit,
    empty: String,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(24.dp)),
    ) {
        if (rows.isEmpty()) {
            Text(empty, color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(1.dp).background(Hairline))
            Row(
                Modifier.fillMaxWidth().height(64.dp).clickable { onOpen(row) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(row.name, 36, row.inRange)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.name, color = if (row.inRange) Ink else InkSoft, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (row.favorite) {
                            Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = Amber, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                        }
                    }
                    Text(rangeLine(row, now), color = if (row.inRange) CyanText else GreyIcon, fontFamily = Poppins, fontSize = 12.sp, maxLines = 1)
                }
                CallDot(row.inRange) { onCall(row) }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(CircleShape).background(if (on) Violet else CardWhite).border(1.dp, if (on) Violet else Hairline, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Color.White else Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun V17Thread(
    row: ContactRow,
    messages: List<DirectMessage>,
    mine: (DirectMessage) -> Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    recording: Boolean,
    onPing: () -> Unit,
    onCall: () -> Unit,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onPlay: (String?) -> Unit,
    now: Long,
    chips: List<ReplyChip>,
    onChip: (ReplyChip) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Spacer(Modifier.weight(1f))
            OfflinePill()
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Avatar(row.name, 72, row.inRange)
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = if (row.favorite) Amber else GreyIcon,
                    modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).clickable(onClick = onFavorite),
                )
            }
            Text(row.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
            Text(rangeLine(row, now), color = CyanText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(CyanLight).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite).padding(12.dp)) {
            item {
                Text("Recent", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            items(messages, key = { it.id }) { message ->
                val own = mine(message)
                if (message.kind == "voice" || message.kind == "call_clip") {
                    VoiceBubble(message, own, onPlay)
                } else {
                    val label = if (message.kind == "ping") "Ping" else CaptionDisplay.text(message.body)
                    val urgent = Urgent.flagged(message.kind)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
                        Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
                            if (urgent) UrgentBadge()
                            Text(
                                label,
                                color = if (own && !urgent) Color.White else Ink,
                                fontFamily = Poppins,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .padding(top = if (urgent) 4.dp else 0.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(if (urgent) LightRed else if (own) Violet else Page)
                                    .border(if (urgent) 1.dp else 0.dp, if (urgent) Accent else Color.Transparent, RoundedCornerShape(19.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
        ReplyChips(chips, onChip)
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(28.dp)).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontFamily = Poppins, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text(if (recording) "Recording…" else "Message ${row.name.substringBefore(' ')}…", color = InkSoft, fontFamily = Poppins, fontSize = 14.sp)
                    inner()
                },
            )
            Box(Modifier.size(40.dp).clip(CircleShape).background(VioletLight).clickable(onClick = onMic), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice note", tint = Violet, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(Violet).clickable(onClick = onSend), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Message", Violet, CardWhite, Modifier.weight(1f), onMic)
            ActionPill("Ping", CyanText, CyanLight, Modifier.weight(1f), onPing)
        }
        Box(
            Modifier.padding(top = 8.dp, bottom = 8.dp).fillMaxWidth().height(68.dp).clip(RoundedCornerShape(34.dp)).background(CallBrush).clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Text("Call ${row.name.substringBefore(' ')}", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun VoiceBubble(message: DirectMessage, mine: Boolean, onPlay: (String?) -> Unit) {
    Column(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Page).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Violet).clickable { onPlay(message.audioPath) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(if (mine) "You" else message.senderName, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        WaveBars(message.audioPath)
        Text("“${CaptionDisplay.text(message.body)}”", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text("On-device AI", color = CyanText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(CyanLight).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun DisclosureDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(ModelDisclosure.TITLE, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            ModelDisclosure.LINES.forEach { line ->
                Text(line, color = Ink, fontFamily = Poppins, fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "Close",
                    color = VioletDeep,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun WaveBars(path: String?) {
    if (path.isNullOrBlank()) return
    val bars by produceState(emptyList<Float>(), path) {
        value = withContext(Dispatchers.IO) { Waveform.bars(File(path), 24) }
    }
    if (bars.isEmpty()) return
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bars.forEach { height ->
            Box(
                Modifier
                    .weight(1f)
                    .height((6 + 22 * height).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Violet),
            )
        }
    }
}

@Composable
private fun ReplyChips(chips: List<ReplyChip>, onTap: (ReplyChip) -> Unit) {
    if (chips.isEmpty()) return
    val taps = remember { ChipTapGuard() }
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(VioletLight).clickable {
                    if (taps.allow(System.currentTimeMillis())) onTap(chip)
                }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(chip.label, color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun UrgentBadge() {
    Text(
        Urgent.BADGE,
        color = Color.White,
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        modifier = Modifier.clip(CircleShape).background(Accent).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun ActionPill(label: String, tint: Color, fill: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(fill).border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun V17InCall(
    call: CallUi,
    inRange: Boolean,
    onUrgent: () -> Unit,
    onDismiss: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    chips: List<ReplyChip>,
    onChip: (ReplyChip) -> Unit,
    onSendText: (String) -> Unit,
) {
    var gateDraft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding()) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(call.peerName, 40, inRange)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(call.peerName, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (call.phase.name == "ACTIVE") "1:1 call · ${if (inRange) "in range" else "out of range"}" else "Calling…",
                    color = InkSoft,
                    fontFamily = Poppins,
                    fontSize = 12.sp,
                )
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (call.speakerOn) "Connected · Speaker" else "Connected · Earpiece",
                color = Color(0xFF15803D),
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier.clip(CircleShape).background(Color(0xFFE8F8EE)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text("Offline · on-device AI", color = CyanText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(CyanLight).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        if (call.emergency != null) {
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LightRed).border(1.5.dp, Accent, RoundedCornerShape(24.dp)).padding(16.dp),
            ) {
                Text("Possible emergency:", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("“${CaptionDisplay.text(call.emergency)}”", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("AI · on-device", color = CyanText, fontFamily = Poppins, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1.4f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Accent).clickable(onClick = onUrgent), contentAlignment = Alignment.Center) {
                        Text("Send urgent", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).border(1.dp, Color(0xFFF9CFCF), RoundedCornerShape(28.dp)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Text("Dismiss", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        Column(
            Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(CardWhite).padding(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Captions", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Last 3 turns", color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
            }
            call.captions.forEach { line -> CaptionRow(line) }
            if (call.captions.isEmpty()) {
                Text("Hold to talk. Captions stay on this phone.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Text("Clips play automatically · up to 0:10 each", color = InkSoft, fontFamily = Poppins, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        ReplyChips(chips) { chip ->
            if (chip.fill) gateDraft = chip.sendText else onChip(chip)
        }
        if (gateDraft.isNotEmpty()) {
            Row(
                Modifier.padding(bottom = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(24.dp)).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = gateDraft,
                    onValueChange = { gateDraft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Ink, fontFamily = Poppins, fontSize = 14.sp),
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Violet).clickable {
                        val text = gateDraft.trim()
                        if (text.isNotEmpty()) onSendText(text)
                        gateDraft = ""
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (call.phase.name == "INCOMING") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 18.dp)) {
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(CallBrush).clickable(onClick = onAccept), contentAlignment = Alignment.Center) {
                    Text("Accept", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Accent).clickable(onClick = onDecline), contentAlignment = Alignment.Center) {
                    Text("Decline", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Row(Modifier.padding(bottom = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(if (call.speakerOn) VioletLight else CardWhite).border(1.5.dp, Hairline, CircleShape).clickable(onClick = onSpeaker), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speaker", tint = Ink)
                    }
                    Text(if (call.speakerOn) "Speaker on" else "Speaker", color = InkSoft, fontFamily = Poppins, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(if (call.phase.name == "ACTIVE" && !call.playing) VioletBrush else Brush.linearGradient(listOf(GreyIcon, GreyIcon)))
                            .pointerHold(
                                enabled = call.phase.name == "ACTIVE" && !call.playing,
                                onStart = onHoldStart,
                                onEnd = onHoldEnd,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Mic, contentDescription = "Hold to talk", tint = Color.White, modifier = Modifier.size(32.dp))
                            Text(if (call.holding) "0:${call.elapsedSec.coerceAtMost(10).toString().padStart(2, '0')}" else "Hold to talk", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Text("0:10 max", color = InkSoft, fontFamily = Poppins, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Accent).clickable(onClick = onEnd), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End", tint = Color.White)
                    }
                    Text("End", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CaptionRow(line: CaptionLine) {
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (line.transcribing) Color.Transparent else Page).border(if (line.transcribing) 1.dp else 0.dp, Violet, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(line.speaker, color = if (line.mine) InkSoft else Accent, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(
            if (line.transcribing) "Transcribing…" else CaptionDisplay.text(line.text),
            color = Ink,
            fontFamily = Poppins,
            fontSize = 14.sp,
        )
    }
}

private fun Modifier.pointerHold(enabled: Boolean, onStart: () -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown()
            onStart()
            waitForUpOrCancellation()
            onEnd()
        }
    }

@Composable
fun NamePrompt(initial: String, onContinue: (String) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxSize().background(Page).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("B-LINK", color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            NameChoice.PROMPT,
            color = Ink,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        androidx.compose.foundation.text.BasicTextField(
            value = draft,
            onValueChange = { draft = it.take(40) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontFamily = Poppins, fontSize = 18.sp),
            modifier = Modifier.padding(top = 28.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) Text(NameChoice.PROMPT, color = InkSoft, fontFamily = Poppins, fontSize = 16.sp)
                    inner()
                }
            },
        )
        Box(
            Modifier.padding(top = 16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Violet).clickable { onContinue(draft) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun V17Add(
    name: String,
    code: String,
    qr: androidx.compose.ui.graphics.ImageBitmap?,
    recent: List<ContactRow>,
    now: Long,
    bluetooth: Boolean,
    onName: (String) -> Unit,
    onScan: () -> Unit,
    onShare: () -> Unit,
    onCall: (ContactRow) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            OfflinePill()
            Text("Add contact", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            Text("Show your code, or scan theirs. No signal needed.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CardWhite).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (qr != null) {
                    androidx.compose.foundation.Image(qr, contentDescription = "Your contact QR", modifier = Modifier.size(200.dp))
                }
                var editingName by remember { mutableStateOf(false) }
                if (!editingName) {
                    Text(
                        name,
                        color = Ink,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clickable { editingName = true },
                    )
                } else {
                    androidx.compose.foundation.text.BasicTextField(
                        value = name,
                        onValueChange = onName,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(56.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                                if (name.isEmpty()) {
                                    Text(NameChoice.PROMPT, color = InkSoft, fontFamily = Poppins, fontSize = 16.sp)
                                }
                                inner()
                            }
                        },
                    )
                }
                Text(code, color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).clip(CircleShape).background(VioletLight).padding(horizontal = 12.dp, vertical = 4.dp))
            }
            Text("Recently added", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite)) {
                if (recent.isEmpty()) {
                    Text("Scan a code to save someone.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
                recent.take(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(row.name, 36, row.inRange)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(row.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Added ${ago((now - row.addedAtMillis).coerceAtLeast(0))} · ${if (row.inRange) "In range" else rangeLine(row, now)}",
                                color = InkSoft,
                                fontFamily = Poppins,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        CallDot(row.inRange) { onCall(row) }
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bluetooth) {
                    Text("Bluetooth on", color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(VioletLight).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Text("Face to face works best", color = CyanText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(CyanLight).padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Row(Modifier.padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(VioletBrush).clickable(onClick = onScan), contentAlignment = Alignment.Center) {
                    Text("Scan their QR", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).border(1.5.dp, Violet, RoundedCornerShape(28.dp)).clickable(onClick = onShare), contentAlignment = Alignment.Center) {
                    Text("Share my code", color = VioletDeep, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun V17Messages(
    threads: List<Conversation>,
    query: String,
    onQuery: (String) -> Unit,
    now: Long,
    onOpen: (Conversation) -> Unit,
    onUrgent: (Conversation) -> Unit,
) {
    val needle = query.trim().lowercase()
    val filtered = if (needle.isEmpty()) threads else threads.filter {
        it.name.lowercase().contains(needle) || it.snippet.lowercase().contains(needle) || it.criticalBody.lowercase().contains(needle)
    }
    val critical = filtered.firstOrNull { it.critical }
    val rest = filtered.filter { it.peerId != critical?.peerId }
    val unread = threads.sumOf { it.unread }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            OfflinePill(right = if (unread > 0) "$unread unread" else null)
            Text("Messages", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(top = 16.dp))
            Text("1:1 chats · voice notes transcribed on this phone", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (critical != null) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite).border(1.5.dp, Accent, RoundedCornerShape(24.dp)).padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(critical.name, 36, true)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(critical.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("CRITICAL", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp).clip(CircleShape).background(Accent).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Text(ago((now - critical.atMillis).coerceAtLeast(0)), color = InkSoft, fontFamily = Poppins, fontSize = 11.sp)
                    }
                    Text("“${CaptionDisplay.text(critical.criticalBody)}”", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                    Text("On-device AI", color = CyanText, fontFamily = Poppins, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1.4f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Accent).clickable { onUrgent(critical) }, contentAlignment = Alignment.Center) {
                            Text("Send urgent", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).border(1.dp, Color(0xFFF9CFCF), RoundedCornerShape(28.dp)).clickable { onOpen(critical) }, contentAlignment = Alignment.Center) {
                            Text("Open", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite)) {
                if (rest.isEmpty() && critical == null) {
                    Text("No chats yet. Add someone, then message them.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
                rest.forEach { thread ->
                    Row(Modifier.fillMaxWidth().height(70.dp).clickable { onOpen(thread) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(thread.name, 36, true)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(thread.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (thread.urgent) {
                                    Spacer(Modifier.width(6.dp))
                                    UrgentBadge()
                                }
                            }
                            Text(thread.snippet, color = if (thread.unread > 0) Ink else InkSoft, fontFamily = Poppins, fontWeight = if (thread.unread > 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(ago((now - thread.atMillis).coerceAtLeast(0)), color = if (thread.unread > 0) Violet else InkSoft, fontFamily = Poppins, fontSize = 11.sp)
                            if (thread.unread > 0) {
                                Box(Modifier.padding(top = 4.dp).size(20.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) {
                                    Text(thread.unread.coerceAtMost(9).toString(), color = Color.White, fontSize = 10.sp, fontFamily = Poppins, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(28.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = InkSoft)
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.padding(start = 8.dp).fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontFamily = Poppins, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search messages or transcripts", color = InkSoft, fontFamily = Poppins, fontSize = 14.sp)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun V17QuickCall(rows: List<ContactRow>, onCall: (ContactRow) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardWhite).padding(16.dp)) {
        Text("Call someone in range", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (rows.isEmpty()) {
            Text("Nobody in range yet.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(row.name, 32, true)
                Text(row.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                CallDot(true) { onCall(row) }
            }
        }
        Text("Close", color = Violet, fontFamily = Poppins, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onClose))
    }
}
