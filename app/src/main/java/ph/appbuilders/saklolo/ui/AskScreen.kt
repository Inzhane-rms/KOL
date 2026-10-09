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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.appbuilders.saklolo.ask.AskEngine
import ph.appbuilders.saklolo.ask.AskResult
import ph.appbuilders.saklolo.ask.SafetyBank
import ph.appbuilders.saklolo.summary.GemmaSummarizer
import ph.appbuilders.saklolo.ui.theme.CriticalRed
import ph.appbuilders.saklolo.ui.theme.ForestMid
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.MintWash

private data class AskTurn(val id: Long, val question: String, val result: AskResult)

@Composable
fun AskScreen(onOpenRecorder: () -> Unit) {
    val context = LocalContext.current
    val bank = remember { loadBank(context) }
    val suggestions = remember(bank) { AskEngine.suggestions(bank) }
    var draft by remember { mutableStateOf("") }
    var turns by remember { mutableStateOf(listOf<AskTurn>()) }
    var nextId by remember { mutableLongStateOf(1L) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    fun ask(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val rules = AskEngine.answer(trimmed, bank)
        val id = nextId
        nextId += 1
        turns = turns + AskTurn(id, trimmed, rules)
        draft = ""
        if (rules !is AskResult.Fallback || !GemmaSummarizer.mightRun(context)) return
        val catalog = bank.pairs.joinToString("\n") { pair ->
            "${pair.id}. ${pair.questionTl} / ${pair.questionEn}"
        }
        val valid = bank.pairs.map { it.id }.toSet()
        scope.launch {
            val chosen = withContext(Dispatchers.IO) {
                GemmaSummarizer.chooseAskPair(context, trimmed, catalog, valid)
            } ?: return@launch
            val pair = bank.pairs.firstOrNull { it.id == chosen } ?: return@launch
            val tip = AskEngine.tipFor(pair)
            turns = turns.map { turn ->
                if (turn.id == id && turn.result is AskResult.Fallback) turn.copy(result = tip) else turn
            }
        }
    }

    LaunchedEffect(turns.size) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 96.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            turns.forEach { turn ->
                QuestionBubble(turn.question)
                when (val result = turn.result) {
                    is AskResult.Emergency -> EmergencyCard(result, onOpenRecorder)
                    is AskResult.Tip -> TipCard(result, bank)
                    is AskResult.Fallback -> FallbackCard(turn.question, result, bank)
                }
            }
        }
        Text(
            "TRY ASKING",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                Text(
                    text = suggestion.label,
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MintWash)
                        .clickable { ask(suggestion.question) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White)
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Ink),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { ask(draft) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text("Magtanong… / Ask a question", color = InkSoft, fontSize = 16.sp)
                        }
                        inner()
                    }
                },
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ForestMid)
                    .clickable { ask(draft) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ask", tint = Color.White)
            }
        }
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun TipCard(result: AskResult.Tip, bank: SafetyBank) {
    AnswerCard(
        label = "ANSWER TO: ${result.askedLabel}",
        title = result.answerTl,
        details = result.answerEn,
        footnote = "${bank.sourceLabel(result.sourceCode)} · ${bank.disclaimerEn} ${bank.disclaimerTl}",
    )
}

@Composable
private fun FallbackCard(question: String, result: AskResult.Fallback, bank: SafetyBank) {
    AnswerCard(
        label = "ANSWER TO: $question",
        title = result.answerTl,
        details = result.answerEn,
        footnote = "${bank.disclaimerEn} ${bank.disclaimerTl}",
    )
}

@Composable
private fun AnswerCard(label: String, title: String, details: String, footnote: String) {
    Column(
        Modifier.flatCard(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(details, color = InkSoft, fontSize = 14.sp)
        Text(footnote, color = InkSoft, fontSize = 12.sp)
    }
}

@Composable
private fun EmergencyCard(result: AskResult.Emergency, onOpenRecorder: () -> Unit) {
    InfoCard(
        background = CriticalRed,
        label = "EMERGENCY?",
        title = result.answerTl,
        details = result.answerEn,
        ink = Color.White,
        arrowBackground = Color.White,
        arrowTint = Ink,
        arrowDescription = "Open SOS recorder",
        onArrow = onOpenRecorder,
    )
}

private fun loadBank(context: Context): SafetyBank =
    context.assets.open("ask/ask_blink_qa.json").bufferedReader().use { reader ->
        AskEngine.parse(reader.readText())
    }
