package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ph.appbuilders.saklolo.CallUi
import ph.appbuilders.saklolo.R
import ph.appbuilders.saklolo.audio.Waveform
import ph.appbuilders.saklolo.contact.CaptionDisplay
import ph.appbuilders.saklolo.contact.OriginalCaption
import ph.appbuilders.saklolo.contact.ChipTapGuard
import ph.appbuilders.saklolo.contact.ContactRow
import ph.appbuilders.saklolo.contact.Conversation
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.ReplyChip
import ph.appbuilders.saklolo.contact.Urgent
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.Amber
import ph.appbuilders.saklolo.ui.theme.AmberLight
import ph.appbuilders.saklolo.ui.theme.CardWhite
import ph.appbuilders.saklolo.ui.theme.Cyan
import ph.appbuilders.saklolo.ui.theme.CyanLight
import ph.appbuilders.saklolo.ui.theme.CyanText
import ph.appbuilders.saklolo.ui.theme.Hairline
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.NavIdle
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.Peach
import ph.appbuilders.saklolo.ui.theme.PeachLight
import ph.appbuilders.saklolo.ui.theme.PeachText
import ph.appbuilders.saklolo.ui.theme.PillAmberText
import ph.appbuilders.saklolo.ui.theme.Poppins
import ph.appbuilders.saklolo.ui.theme.StatusGreen
import ph.appbuilders.saklolo.contact.CallClock
import ph.appbuilders.saklolo.ui.theme.Violet
import ph.appbuilders.saklolo.ui.theme.VioletLight

private val Panel = RoundedCornerShape(28.dp)
private val Card = RoundedCornerShape(24.dp)

@Composable
fun rememberKolListState(): LazyListState = rememberSaveable(saver = LazyListState.Saver) {
    LazyListState()
}

@Composable
fun KolBar(
    route: String,
    unread: Int,
    onHome: () -> Unit,
    onContacts: () -> Unit,
    onMessages: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = MainNav.barRoute(route)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Ink)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NavSlot("Home", Icons.Filled.Home, active == MainNav.HOME, false, Modifier.weight(1f), onHome)
        NavSlot("Contacts", Icons.Filled.Person, active == MainNav.CONTACTS, false, Modifier.weight(1f), onContacts)
        NavSlot("Messages", Icons.Outlined.ChatBubbleOutline, active == MainNav.MESSAGES, unread > 0, Modifier.weight(1f), onMessages)
        NavSlot("Add", Icons.Filled.Add, active == MainNav.ADD, false, Modifier.weight(1f), onAdd)
    }
}

@Composable
private fun NavSlot(label: String, icon: ImageVector, active: Boolean, dot: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (active) {
        Row(
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(24.dp))
            Text(label, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        }
    } else {
        Box(
            modifier.heightIn(min = 56.dp).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(NavIdle), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
                if (dot) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(Amber))
                }
            }
        }
    }
}

@Composable
fun KolHome(
    name: String,
    rows: List<ContactRow>,
    messages: List<DirectMessage>,
    myId: String,
    setupMissing: Int,
    now: Long,
    onMenu: () -> Unit,
    onBell: () -> Unit,
    onAvatar: () -> Unit,
    bellDot: Boolean,
    onCallAny: () -> Unit,
    onMessage: () -> Unit,
    onVoice: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val inRange = remember(rows) { rows.filter { it.inRange } }
    val waiting = remember(messages, rows, myId) { KolNav.waitingPeers(messages, rows, myId) }
    val lastHeard = remember(rows) { rows.maxOfOrNull { it.lastHeardMillis } ?: 0L }
    val activity = remember(messages, rows, myId) { KolNav.recentActivity(messages, rows, myId) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        KolTop(name, onMenu, onBell, onAvatar, bellDot)
        Text("Hello $name!", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(
            KolNav.friendsLine(inRange.size),
            color = InkSoft,
            fontFamily = Poppins,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (setupMissing > 0) {
            SetupPill(onBell, Modifier.padding(top = 8.dp))
        }
        KolStack(
            back = PeachLight,
            backLabel = KolNav.homeBackLabel(setupMissing, waiting, lastHeard, now),
            frontHeight = 176.dp,
            pill = "Call",
            pillIcon = Icons.Filled.Call,
            pillWidth = 140.dp,
            onPill = onCallAny,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("In range now · ${inRange.size}", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
            AvatarStack(inRange, Modifier.padding(top = 16.dp))
            Text(
                if (inRange.isEmpty()) "Nobody in range" else "In range",
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = Poppins,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp, end = 148.dp),
            )
        }
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 112.dp).clip(Panel).background(CardWhite).border(1.dp, Hairline, Panel),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Quick("Call", VioletLight, Violet, Icons.Filled.Call, Modifier.weight(1f), onCallAny)
            Quick("Message", CyanLight, CyanText, Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f), onMessage)
            Quick("Voice note", PeachLight, PeachText, Icons.Filled.Mic, Modifier.weight(1f)) {
                inRange.firstOrNull()?.let { onVoice(it.deviceId) }
            }
            Quick("Add", VioletLight, Violet, Icons.Filled.Add, Modifier.weight(1f), onAdd)
        }
        Column(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(Panel).background(CardWhite).border(1.dp, Hairline, Panel),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent activity", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Box(Modifier.height(56.dp).widthIn(min = 56.dp).clickable(onClick = onMessage), contentAlignment = Alignment.Center) {
                    Text("View all", color = Violet, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            if (activity.isEmpty()) {
                Text("Nothing yet. Add a friend to start.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            activity.forEach { row ->
                key("${row.tone}:${row.peerId}:${row.at}") {
                    ActivityLine(row, now) { onOpen(row.peerId) }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun KolContacts(
    rows: List<ContactRow>,
    filter: String,
    onFilter: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    now: Long,
    selfName: String,
    onMenu: () -> Unit,
    onBell: () -> Unit,
    onAvatar: () -> Unit,
    bellDot: Boolean,
    onOpen: (ContactRow) -> Unit,
    onCall: (ContactRow) -> Unit,
    onFavorite: (ContactRow) -> Unit,
) {
    val inRange = remember(rows) { rows.filter { it.inRange } }
    val saved = remember(rows) { rows.count { it.saved } }
    val unsaved = remember(rows) { rows.filter { it.inRange && !it.saved } }
    val shown = remember(rows, filter, query) {
        val base = when (filter) {
            "range" -> rows.filter { it.inRange }
            "favorites" -> rows.filter { it.favorite }
            else -> rows
        }
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) base else base.filter { it.name.lowercase().contains(needle) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        KolTop(selfName, onMenu, onBell, onAvatar, bellDot)
        SearchField(query, "Search friends", onQuery, Modifier.padding(top = 8.dp))
        Text("Contacts", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, modifier = Modifier.padding(top = 12.dp))
        Text("$saved saved · ${inRange.size} in range · tap to call", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        KolStack(
            back = CyanLight,
            backLabel = if (unsaved.isEmpty()) "Nobody new nearby" else unsaved.joinToString { it.name }.let { "$it nearby, not saved yet" },
            frontHeight = 176.dp,
            pill = "Call",
            pillIcon = Icons.Filled.Call,
            pillWidth = 140.dp,
            onPill = { inRange.firstOrNull()?.let(onCall) },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("In range now", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
            val shownRange = inRange.take(2)
            shownRange.forEachIndexed { index, row ->
                val end = if (index == shownRange.lastIndex) 148.dp else 0.dp
                Text(row.name, color = Color.White, fontFamily = Poppins, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, end = end))
            }
            if (inRange.isEmpty()) {
                Text("Nobody in range", color = Color.White.copy(alpha = 0.85f), fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, end = 148.dp))
            }
        }
        Row(
            Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("All", filter == "all") { onFilter("all") }
            FilterChip("In range", filter == "range") { onFilter("range") }
            FilterChip("Favorites", filter == "favorites") { onFilter("favorites") }
        }
        Column(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(Panel).background(CardWhite).border(1.dp, Hairline, Panel),
        ) {
            if (shown.isEmpty()) {
                Text("No contacts here yet.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            shown.forEach { row ->
                key(row.deviceId) {
                    ContactLine(row, now, onOpen, onCall, onFavorite)
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun KolMessages(
    threads: List<Conversation>,
    query: String,
    onQuery: (String) -> Unit,
    now: Long,
    selfName: String,
    onMenu: () -> Unit,
    onBell: () -> Unit,
    onAvatar: () -> Unit,
    bellDot: Boolean,
    onOpen: (Conversation) -> Unit,
    onUrgent: (Conversation) -> Unit,
) {
    val shown = remember(threads, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) threads else threads.filter {
            it.name.lowercase().contains(needle) || it.snippet.lowercase().contains(needle)
        }
    }
    val critical = shown.firstOrNull { it.critical }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        KolTop(selfName, onMenu, onBell, onAvatar, bellDot)
        Text("Messages", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, modifier = Modifier.padding(top = 12.dp))
        Text("1:1 chats · transcripts stay on this phone", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        SearchField(query, "Search messages", onQuery, Modifier.padding(top = 12.dp))
        if (critical != null) {
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 144.dp).clip(Card).background(LightRed).border(1.dp, Accent, Card).padding(16.dp),
            ) {
                Text("Possible emergency", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("“${CaptionDisplay.text(critical.criticalBody)}”", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(KolNav.SEND_URGENT, Accent, Color.White, Modifier.weight(1.4f)) { onUrgent(critical) }
                    PillButton("Open", CardWhite, Ink, Modifier.weight(1f)) { onOpen(critical) }
                }
            }
        }
        Column(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(Panel).background(CardWhite).border(1.dp, Hairline, Panel),
        ) {
            if (shown.isEmpty()) {
                Text("No chats yet. Add someone, then message them.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            shown.filter { it.peerId != critical?.peerId }.forEach { thread ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable { onOpen(thread) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonFace(thread.name, 40, inRange = true)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(thread.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                            if (thread.urgent) {
                                Spacer(Modifier.width(6.dp))
                                UrgentMark()
                            }
                        }
                        Text(thread.snippet, color = InkSoft, fontFamily = Poppins, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatWhen(thread.atMillis, now), color = InkSoft, fontFamily = Poppins, fontSize = 11.sp)
                        if (thread.unread > 0) {
                            Box(Modifier.padding(top = 4.dp).size(20.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) {
                                Text(thread.unread.coerceAtMost(9).toString(), color = Color.White, fontFamily = Poppins, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun KolChat(
    row: ContactRow,
    messages: List<DirectMessage>,
    mine: (DirectMessage) -> Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    recording: Boolean,
    onCall: () -> Unit,
    onBack: () -> Unit,
    onPlay: (String?) -> Unit,
    now: Long,
    chips: List<ReplyChip>,
    onChip: (ReplyChip) -> Unit,
) {
    val list = rememberKolListState()
    val clock = remember { SimpleDateFormat("h:mm a", Locale.US) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", CardWhite, Ink, onBack)
            PersonFace(row.name, 40, row.inRange, Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(row.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (row.inRange) "In range · offline" else "Out of range · offline", color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
            }
            RoundButton(Icons.Filled.Call, "Call", Violet, Color.White, onCall)
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (messages.isEmpty()) "Today" else dayChip(messages.first().createdAtMillis, now),
                        color = InkSoft,
                        fontFamily = Poppins,
                        fontSize = 11.sp,
                        modifier = Modifier.clip(CircleShape).background(CardWhite).border(1.dp, Hairline, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                val own = mine(message)
                val voice = message.kind == "voice" || message.kind == "call_clip"
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = if (own) Alignment.End else Alignment.Start,
                ) {
                    if (Urgent.flagged(message.kind)) UrgentMark()
                    if (voice) {
                        VoiceBubble(message, own, onPlay)
                    } else {
                        Text(
                            if (message.kind == "ping") "Ping" else CaptionDisplay.text(message.body),
                            color = if (own) Color.White else Ink,
                            fontFamily = Poppins,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (own) Violet else CardWhite)
                                .border(if (own) 0.dp else 1.dp, if (own) Color.Transparent else Hairline, RoundedCornerShape(22.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    if (own) {
                        val handedOff = message.sentAtMillis > 0L
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (handedOff) {
                                Icon(Icons.Filled.Check, contentDescription = KolNav.SENT, tint = StatusGreen, modifier = Modifier.size(12.dp))
                                Text(
                                    KolNav.bubbleStatus(message.sentAtMillis, clock.format(Date(message.sentAtMillis))),
                                    color = InkSoft,
                                    fontFamily = Poppins,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            } else {
                                Icon(Icons.Filled.Schedule, contentDescription = KolNav.WAITING, tint = Amber, modifier = Modifier.size(16.dp))
                                Text(KolNav.bubbleStatus(0L, ""), color = PillAmberText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (!row.inRange) {
            Row(
                Modifier.padding(bottom = 8.dp).clip(CircleShape).background(AmberLight).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                Text("Waiting · sends when in range", color = PillAmberText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        KolChips(chips, onChip)
        Row(
            Modifier.padding(bottom = 12.dp).fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(32.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(32.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                textStyle = TextStyle(color = Ink, fontFamily = Poppins, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(if (recording) "Recording…" else "Message", color = InkSoft, fontFamily = Poppins, fontSize = 14.sp)
                    }
                    inner()
                },
            )
            RoundButton(Icons.Filled.Mic, "Voice note", Peach, Color.White, onMic)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(56.dp).clip(CircleShape).background(Violet).clickable(onClick = onSend), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun KolCall(
    call: CallUi,
    inRange: Boolean,
    onBack: () -> Unit,
    onSpeaker: () -> Unit,
    onUrgent: () -> Unit,
    onDismiss: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onMute: (Boolean) -> Unit,
    onEnd: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    chips: List<ReplyChip>,
    onChip: (ReplyChip) -> Unit,
    onSendText: (String) -> Unit,
) {
    val muted = call.muted
    var gate by rememberSaveable(call.peerId) { mutableStateOf("") }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var started by remember(call.callId) { mutableLongStateOf(0L) }
    LaunchedEffect(call.phase, call.callId) {
        val active = call.phase.name == "ACTIVE"
        started = CallClock.anchor(active, call.callId, if (active) started else 0L, System.currentTimeMillis())
        if (!active || call.callId == 0L) return@LaunchedEffect
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = CallClock.label(if (started == 0L) 0L else tick - started)
    val headline = when (call.phase.name) {
        "ACTIVE" -> "On call · $elapsed"
        "INCOMING" -> "Incoming · offline"
        else -> "Calling… · offline"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", CardWhite, Ink, onBack)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(call.peerName, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                Text(headline, color = InkSoft, fontFamily = Poppins, fontSize = 12.sp)
            }
            RoundButton(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", if (call.speakerOn) Violet else CardWhite, if (call.speakerOn) Color.White else Ink, onSpeaker)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).border(2.dp, Cyan.copy(alpha = 0.35f), CircleShape))
                Box(Modifier.size(108.dp).border(2.dp, Cyan, CircleShape))
                PersonFace(call.peerName, 96, inRange)
            }
            Text(
                if (inRange) "In range" else "Not in range",
                color = if (inRange) CyanText else InkSoft,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp).clip(CircleShape).background(if (inRange) CyanLight else CardWhite).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Column(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(Panel).background(CardWhite).border(1.dp, Hairline, Panel).padding(16.dp),
                horizontalAlignment = Alignment.Start,
            ) {
            Text("Live captions · on-device", color = CyanText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(CyanLight).padding(horizontal = 10.dp, vertical = 6.dp))
            if (call.captions.isEmpty()) {
                Text("${KolNav.HOLD_TO_TALK}. Captions stay on this phone.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            }
            call.captions.forEach { line ->
                key(line.id) {
                    val latest = line == call.captions.last()
                    val original = OriginalCaption.line(line.text, line.raw)
                    var open by rememberSaveable(line.id) { mutableStateOf(false) }
                    Text(line.speaker, color = InkSoft, fontFamily = Poppins, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        if (line.transcribing) "Transcribing…" else CaptionDisplay.text(line.text),
                        color = Ink,
                        fontFamily = Poppins,
                        fontWeight = if (latest) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (latest) 16.sp else 14.sp,
                        modifier = Modifier.clickable(enabled = original != null) { open = !open },
                    )
                    if (open && original != null) {
                        Text(original, color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            }
        if (call.emergency != null) {
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 152.dp).clip(Card).background(Accent).padding(16.dp),
            ) {
                Text("Possible emergency", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Heard ‘${CaptionDisplay.text(call.emergency)}’ · detected on-device", color = Color.White, fontFamily = Poppins, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(KolNav.SEND_URGENT, Color.White, Accent, Modifier.weight(1.5f), onUrgent)
                    PillButton("Not now", Color.Transparent, Color.White, Modifier.weight(1f), onDismiss)
                }
            }
        }
        KolChips(chips) { chip ->
            if (chip.fill) gate = chip.sendText else onChip(chip)
        }
        if (gate.isNotEmpty()) {
            Row(
                Modifier.padding(bottom = 8.dp).fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(28.dp)).padding(horizontal = 6.dp).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(value = gate, onValueChange = { gate = it }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Ink, fontFamily = Poppins, fontSize = 14.sp))
                Box(Modifier.size(56.dp).clip(CircleShape).background(Violet).clickable {
                    val text = gate.trim()
                    if (text.isNotEmpty()) onSendText(text)
                    gate = ""
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        }
        if (call.phase.name == "INCOMING") {
            Row(Modifier.padding(bottom = 16.dp).fillMaxWidth().heightIn(min = 64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Accept", Violet, Color.White, Modifier.weight(1f), onAccept)
                PillButton("Decline", Ink, Color.White, Modifier.weight(1f), onDecline)
            }
        } else {
            Row(
                Modifier.padding(bottom = 16.dp).fillMaxWidth().heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (muted) "Muted" else "Mute", if (muted) AmberLight else CardWhite, if (muted) Amber else Ink) { onMute(!muted) }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (!muted && call.phase.name == "ACTIVE" && !call.playing) Violet else NavIdle)
                        .pointerHold(enabled = !muted && call.phase.name == "ACTIVE" && !call.playing, onStart = onHoldStart, onEnd = onHoldEnd)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (call.holding) "0:${call.elapsedSec.coerceAtMost(10).toString().padStart(2, '0')}" else KolNav.HOLD_TO_TALK,
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                Row(
                    Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Ink).clickable(onClick = onEnd).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color(0xFFFF8A8A), modifier = Modifier.size(22.dp))
                    Text("End", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
fun KolAdd(
    name: String,
    onName: (String) -> Unit,
    code: String,
    qr: ImageBitmap?,
    nearby: List<ContactRow>,
    onMenu: () -> Unit,
    onBell: () -> Unit,
    onAvatar: () -> Unit,
    bellDot: Boolean,
    onScan: () -> Unit,
    onShare: () -> Unit,
    onAdd: (ContactRow) -> Unit,
) {
    val displayCode = remember(code) { KolNav.kolCode(code) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        KolTop(name, onMenu, onBell, onAvatar, bellDot)
        Text("Add a friend", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, modifier = Modifier.padding(top = 12.dp))
        Text("Share your code, or scan a friend's.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        KolStack(
            back = PeachLight,
            backLabel = "My code · $displayCode",
            frontHeight = 348.dp,
            pill = "Share my code",
            pillIcon = Icons.Filled.Share,
            pillWidth = 178.dp,
            onPill = onShare,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Box(
                Modifier.size(212.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) {
                    Image(qr, contentDescription = "My code", modifier = Modifier.size(188.dp), contentScale = ContentScale.Fit)
                }
                Image(
                    painterResource(R.drawable.ic_kol_tile),
                    contentDescription = "KOL",
                    modifier = Modifier.size(36.dp),
                )
            }
            Row(Modifier.padding(top = 12.dp, end = 186.dp), verticalAlignment = Alignment.CenterVertically) {
                PersonFace(name, 36, true)
                BasicTextField(
                    value = name,
                    onValueChange = onName,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    textStyle = TextStyle(color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                )
            }
        }
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(28.dp)).background(Ink).clickable(onClick = onScan).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Scan QR", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Add a friend from their code", color = Color.White.copy(alpha = 0.75f), fontFamily = Poppins, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(Panel).background(CardWhite).border(1.dp, Hairline, Panel).padding(16.dp),
        ) {
            Text("Nearby, not saved", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (nearby.isEmpty()) {
                Text("Friends in range who are not saved yet show up here.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            nearby.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonFace(row.name, 40, true)
                    Text(row.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1)
                    Box(
                        Modifier.heightIn(min = 56.dp).clip(CircleShape).background(VioletLight).clickable { onAdd(row) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ Add", color = Violet, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun KolQuickCall(rows: List<ContactRow>, onCall: (ContactRow) -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .clip(Panel)
            .background(CardWhite)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Call", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Box(Modifier.height(56.dp).widthIn(min = 56.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Text("Close", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        if (rows.isEmpty()) {
            Text("Nobody in range.", color = InkSoft, fontFamily = Poppins, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onCall(row) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonFace(row.name, 36, true)
                Text(row.name, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun KolTop(name: String, onMenu: () -> Unit, onBell: () -> Unit, onAvatar: () -> Unit, bellDot: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Filled.Menu, "Menu", CardWhite, Ink, onMenu)
        Spacer(Modifier.weight(1f))
        Box {
            RoundButton(Icons.Filled.Notifications, "Alerts", CardWhite, Ink, onBell)
            if (bellDot) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(CircleShape).background(Amber))
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(56.dp).clickable(onClick = onAvatar), contentAlignment = Alignment.Center) {
            PersonFace(name, 48, true)
        }
    }
}

@Composable
private fun KolStack(
    back: Color,
    backLabel: String,
    frontHeight: Dp,
    pill: String,
    pillIcon: ImageVector,
    pillWidth: Dp,
    onPill: () -> Unit,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().heightIn(min = 44.dp + frontHeight)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(Panel).background(back)) {
            Text(backLabel, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 8.dp))
        }
        Box(Modifier.padding(top = 44.dp).fillMaxWidth().heightIn(min = frontHeight).clip(Panel).background(Violet).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Column { front() }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp).widthIn(min = pillWidth).heightIn(min = 56.dp)) {
            Box(
                Modifier.offset(x = 8.dp, y = (-8).dp).width(pillWidth + 8.dp).heightIn(min = 72.dp).clip(RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp)).background(Page),
            )
            Row(
                Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)).background(Ink).clickable(onClick = onPill).padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(pillIcon, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                }
                Text(pill, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SetupPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).clip(CircleShape).background(AmberLight).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Setup needed", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun Quick(label: String, fill: Color, tint: Color, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.heightIn(min = 88.dp).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, color = Ink, fontFamily = Poppins, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ActivityLine(row: KolActivity, now: Long, onOpen: () -> Unit) {
    val fill = when (row.tone) {
        "voice" -> CyanLight
        "sent" -> VioletLight
        "waiting" -> AmberLight
        else -> PeachLight
    }
    val tint = when (row.tone) {
        "voice" -> Cyan
        "sent" -> Violet
        "waiting" -> Amber
        else -> Peach
    }
    val icon = when (row.tone) {
        "voice" -> Icons.Filled.Mic
        "sent" -> Icons.Filled.Check
        "waiting" -> Icons.Filled.Schedule
        else -> Icons.Filled.Person
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(row.title, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Text(row.detail, color = InkSoft, fontFamily = Poppins, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatWhen(row.at, now), color = InkSoft, fontFamily = Poppins, fontSize = 11.sp)
    }
}

@Composable
private fun AvatarStack(rows: List<ContactRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val shown = rows.take(4)
    val extra = rows.size - shown.size
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width((36 + (shown.size - 1) * 8).dp).height(36.dp)) {
            shown.forEachIndexed { index, row ->
                Box(Modifier.offset(x = (index * 8).dp)) {
                    PersonFace(row.name, 36, true)
                }
            }
        }
        if (extra > 0) {
            Text("+$extra", color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun ContactLine(
    row: ContactRow,
    now: Long,
    onOpen: (ContactRow) -> Unit,
    onCall: (ContactRow) -> Unit,
    onFavorite: (ContactRow) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable { onOpen(row) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonFace(row.name, 40, row.inRange)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    color = if (row.inRange) Ink else Ink.copy(alpha = 0.7f),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(Modifier.size(48.dp).clickable { onFavorite(row) }, contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorite",
                        tint = if (row.favorite) Amber else Hairline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                if (row.inRange) "In range" else formatWhen(row.lastHeardMillis, now),
                color = InkSoft,
                fontFamily = Poppins,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Box(Modifier.size(56.dp).clickable { onCall(row) }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (row.inRange) VioletLight else Page), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = if (row.inRange) Violet else InkSoft, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun VoiceBubble(message: DirectMessage, mine: Boolean, onPlay: (String?) -> Unit) {
    val bars by produceState(emptyList<Float>(), message.audioPath) {
        val path = message.audioPath
        value = if (path.isNullOrBlank()) emptyList() else withContext(Dispatchers.IO) { Waveform.bars(File(path), 18) }
    }
    Column(
        Modifier.width(240.dp).clip(RoundedCornerShape(22.dp)).background(if (mine) Violet else CardWhite).border(if (mine) 0.dp else 1.dp, Hairline, RoundedCornerShape(22.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clickable { onPlay(message.audioPath) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (mine) Color.White else Violet), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = if (mine) Violet else Color.White)
                }
            }
            Row(Modifier.padding(start = 8.dp).weight(1f).height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val shown = if (bars.isEmpty()) List(12) { 0.3f } else bars
                shown.forEach { height ->
                    Box(Modifier.weight(1f).height((8 + 18 * height).dp).clip(RoundedCornerShape(2.dp)).background(if (mine) Color.White else Violet))
                }
            }
        }
        Text("AI transcript", color = CyanText, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp).clip(CircleShape).background(CyanLight).padding(horizontal = 8.dp, vertical = 3.dp))
        val original = OriginalCaption.line(message.body, message.rawBody)
        var open by rememberSaveable(message.id) { mutableStateOf(false) }
        Text(
            "“${CaptionDisplay.text(message.body)}”",
            color = if (mine) Color.White else Ink,
            fontFamily = Poppins,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp).clickable(enabled = original != null) { open = !open },
        )
        if (open && original != null) {
            Text(original, color = if (mine) Color.White.copy(alpha = 0.8f) else InkSoft, fontFamily = Poppins, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun KolChips(chips: List<ReplyChip>, onTap: (ReplyChip) -> Unit) {
    if (chips.isEmpty()) return
    val taps = remember { ChipTapGuard() }
    Row(Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            Box(
                Modifier.heightIn(min = 48.dp).clip(CircleShape).background(VioletLight).clickable {
                    if (taps.allow(System.currentTimeMillis())) onTap(chip)
                }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(chip.label, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun UrgentMark() {
    Text(Urgent.BADGE, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp).clip(CircleShape).background(Accent).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun PersonFace(name: String, size: Int, inRange: Boolean, modifier: Modifier = Modifier) {
    val tint = avatarTint(name)
    Box(
        modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = if (inRange) 1f else 0.45f)).border(if (inRange) 2.dp else 0.dp, if (inRange) Cyan else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = (size / 2.6f).sp)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, fill: Color, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(fill).border(1.dp, Hairline, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(CardWhite).border(1.dp, Hairline, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = InkSoft, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            textStyle = TextStyle(color = Ink, fontFamily = Poppins, fontSize = 14.sp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = InkSoft, fontFamily = Poppins, fontSize = 14.sp)
                inner()
            },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).background(if (selected) Ink else CardWhite).border(1.dp, if (selected) Ink else Hairline, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else Ink,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PillButton(label: String, fill: Color, text: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(fill).border(if (fill == Color.Transparent) 1.dp else 0.dp, Color.White, RoundedCornerShape(28.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = text, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun avatarTint(name: String): Color {
    val colors = listOf(Violet, CyanText, Peach, Color(0xFF5B3FD6))
    val index = (name.lowercase().hashCode() and Int.MAX_VALUE) % colors.size
    return colors[index]
}

private fun dayChip(millis: Long, now: Long): String {
    val day = millis / 86_400_000L
    val today = now / 86_400_000L
    return when (today - day) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
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
