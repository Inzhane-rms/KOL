package ph.appbuilders.saklolo.ui

import android.content.Context
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.ask.AskEngine
import ph.appbuilders.saklolo.ask.AskResult
import ph.appbuilders.saklolo.ask.AskTurn
import ph.appbuilders.saklolo.ask.SafetyBank
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.AccentDeep
import ph.appbuilders.saklolo.ui.theme.ChipWash
import ph.appbuilders.saklolo.ui.theme.GreenText
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.LightRed
import ph.appbuilders.saklolo.ui.theme.PillAmberBg
import ph.appbuilders.saklolo.ui.theme.PillAmberText

@Composable
fun AskScreen(
    turns: List<AskTurn>,
    onAsk: (String) -> Unit,
    onOpenRecorder: () -> Unit,
    seed: String? = null,
    onSeedConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val bank = remember { loadBank(context) }
    val suggestions = remember(bank) { AskEngine.suggestions(bank) }
    var draft by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    fun ask(text: String) {
        if (text.isBlank()) return
        draft = ""
        onAsk(text)
    }

    LaunchedEffect(seed) {
        val text = questionToAutoSend(seed) ?: return@LaunchedEffect
        ask(text)
        onSeedConsumed()
    }

    LaunchedEffect(turns.size) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        Text("Ask B-LINK", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("Offline answers from official guides", color = InkSoft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        Column(
            Modifier.weight(1f).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            turns.forEach { turn ->
                QuestionBubble(turn.question)
                when (val result = turn.result) {
                    is AskResult.Emergency -> EmergencyCard(result.answerTl, result.answerEn, onOpenRecorder)
                    is AskResult.Tip -> TipCard(result, bank)
                    is AskResult.Fallback -> FallbackCard(turn.question, result, bank)
                }
            }
            if (turns.isNotEmpty()) {
                Text("Ask next", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            SuggestionRow(suggestions) { ask(it) }
            EmergencyCard(bank.medicalTl, bank.medicalEn, onOpenRecorder)
        }
        AskInput(
            draft = draft,
            onDraft = { draft = it },
            onSend = { ask(draft) },
        )
    }
}

/** Topic-card text that should be sent as soon as Ask opens. Blank seeds are ignored. */
internal fun questionToAutoSend(seed: String?): String? =
    seed?.trim()?.takeIf { it.isNotEmpty() }

@Composable
private fun SuggestionRow(suggestions: List<ph.appbuilders.saklolo.ask.AskSuggestion>, onAsk: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            Text(
                text = suggestion.label,
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .softCard(CircleShape)
                    .clickable { onAsk(suggestion.question) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AskInput(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(60.dp)
            .softCard(RoundedCornerShape(30.dp))
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = onDraft,
            textStyle = TextStyle(color = Ink, fontSize = 14.sp),
            cursorBrush = SolidColor(Ink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (draft.isEmpty()) Text("Type a question…", color = InkSoft, fontSize = 14.sp)
                    inner()
                }
            },
        )
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Send question", tint = Color.White)
        }
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Accent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun TipCard(result: AskResult.Tip, bank: SafetyBank) {
    AnswerCard(
        asked = result.askedLabel,
        title = result.answerTl,
        details = result.answerEn,
        chips = bank.sourceChips(result.sourceCode),
        footnote = "${bank.disclaimerEn} ${bank.disclaimerTl}",
        fromModel = result.fromModel,
    )
}

@Composable
private fun FallbackCard(question: String, result: AskResult.Fallback, bank: SafetyBank) {
    AnswerCard(
        asked = question,
        title = result.answerTl,
        details = result.answerEn,
        chips = emptyList(),
        footnote = "${bank.disclaimerEn} ${bank.disclaimerTl}",
        fromModel = false,
    )
}

@Composable
private fun AnswerCard(
    asked: String,
    title: String,
    details: String,
    chips: List<String>,
    footnote: String,
    fromModel: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().softCard(RoundedCornerShape(28.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(GreenText),
                contentAlignment = Alignment.Center,
            ) {
                Text("B", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text("B-LINK · answer", color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        if (fromModel) {
            Text(
                "Pinakamalapit na tip",
                color = PillAmberText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PillAmberBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text("SAGOT SA: $asked", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(details, color = InkSoft, fontSize = 13.sp)
        if (chips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                chips.forEach { chip ->
                    Text(
                        chip,
                        color = Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(ChipWash)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Text(footnote, color = InkSoft, fontSize = 12.sp)
    }
}

@Composable
private fun EmergencyCard(title: String, details: String, onOpenRecorder: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LightRed)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = AccentDeep, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(details, color = Ink, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFF4B4B), Accent, Color(0xFFB5161C))))
                .clickable(onClick = onOpenRecorder),
            contentAlignment = Alignment.Center,
        ) {
            Text("SOS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun loadBank(context: Context): SafetyBank =
    context.assets.open("ask/ask_blink_qa.json").bufferedReader().use { reader ->
        AskEngine.parse(reader.readText())
    }
