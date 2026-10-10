package ph.appbuilders.saklolo.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ph.appbuilders.saklolo.R
import ph.appbuilders.saklolo.ui.theme.CardWhite
import ph.appbuilders.saklolo.ui.theme.Hairline
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.Poppins
import ph.appbuilders.saklolo.ui.theme.Violet
import ph.appbuilders.saklolo.ui.theme.VioletDeep

/** Asset names for the legal pages. The files live in assets/legal. */
object LegalDocs {
    const val SAFETY = "safety"
    const val TERMS = "terms"
    const val PRIVACY = "privacy"
    const val LICENSES = "licenses"

    fun read(context: Context, name: String): String =
        context.assets.open("legal/$name.txt").bufferedReader().use { it.readText() }
}

@Composable
fun LegalGate(onOpen: (String) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val safety by produceState("") {
        value = withContext(Dispatchers.IO) { LegalDocs.read(context, LegalDocs.SAFETY) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(R.string.legal_badge),
            color = VioletDeep,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            stringResource(R.string.legal_safety),
            color = Ink,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(
            Modifier
                .padding(top = 16.dp)
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(safety, color = Ink, fontFamily = Poppins, fontSize = 14.sp)
        }
        AgreeLine(onOpen)
        Box(
            Modifier
                .padding(top = 12.dp, bottom = 24.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Violet)
                .clickable(onClick = onContinue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.legal_continue),
                color = CardWhite,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun AgreeLine(onOpen: (String) -> Unit) {
    Text(
        stringResource(R.string.legal_agree),
        color = InkSoft,
        fontFamily = Poppins,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LinkText(stringResource(R.string.legal_terms)) { onOpen(LegalDocs.TERMS) }
        LinkText(stringResource(R.string.legal_privacy)) { onOpen(LegalDocs.PRIVACY) }
    }
}

@Composable
private fun LinkText(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = VioletDeep,
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
fun LegalHub(onOpen: (String) -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(R.string.legal_badge),
            color = VioletDeep,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            ModelDisclosure.TITLE,
            color = Ink,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModelDisclosure.LINES.forEach { line ->
                Text(line, color = Ink, fontFamily = Poppins, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        HubRow(stringResource(R.string.legal_safety)) { onOpen(LegalDocs.SAFETY) }
        HubRow(stringResource(R.string.legal_terms)) { onOpen(LegalDocs.TERMS) }
        HubRow(stringResource(R.string.legal_privacy)) { onOpen(LegalDocs.PRIVACY) }
        HubRow(stringResource(R.string.legal_licenses)) { onOpen(LegalDocs.LICENSES) }
        HubRow(stringResource(R.string.legal_settings), onClick = onSettings)
        Box(
            Modifier
                .padding(top = 16.dp, bottom = 28.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(28.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.legal_close),
                color = VioletDeep,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun HubRow(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun LegalPage(name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val title = when (name) {
        LegalDocs.TERMS -> stringResource(R.string.legal_terms)
        LegalDocs.PRIVACY -> stringResource(R.string.legal_privacy)
        LegalDocs.LICENSES -> stringResource(R.string.legal_licenses)
        else -> stringResource(R.string.legal_safety)
    }
    val body by produceState("", name) {
        value = withContext(Dispatchers.IO) { LegalDocs.read(context, name) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            title,
            color = Ink,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 28.dp),
        )
        Column(
            Modifier
                .padding(top = 16.dp)
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(body, color = Ink, fontFamily = Poppins, fontSize = 14.sp)
        }
        Box(
            Modifier
                .padding(top = 12.dp, bottom = 24.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Violet)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.legal_close),
                color = CardWhite,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}
