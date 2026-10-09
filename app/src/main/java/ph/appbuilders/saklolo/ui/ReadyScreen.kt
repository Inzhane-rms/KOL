package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.relay.ReadyToConnect
import ph.appbuilders.saklolo.relay.SetupKey
import ph.appbuilders.saklolo.relay.SetupRow
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.CardWhite
import ph.appbuilders.saklolo.ui.theme.Hairline
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.Poppins
import ph.appbuilders.saklolo.ui.theme.StatusGreen
import ph.appbuilders.saklolo.ui.theme.Violet

@Composable
fun ReadyToConnectScreen(
    rows: List<SetupRow>,
    onFix: (SetupKey) -> Unit,
    onContinue: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            ReadyToConnect.TITLE,
            color = Ink,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Phones next to you stay out of range until the required rows are green.",
            color = InkSoft,
            fontFamily = Poppins,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(24.dp)),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(Hairline))
                }
                SetupLine(row, onFix)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (onContinue != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Violet)
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center,
            ) {
                Text("Continue", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupLine(row: SetupRow, onFix: (SetupKey) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (row.ok) StatusGreen else Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (row.ok) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (row.ok) "On" else "Off",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(row.title, color = Ink, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                if (row.required) "Required" else "Recommended",
                color = InkSoft,
                fontFamily = Poppins,
                fontSize = 12.sp,
            )
        }
        if (!row.ok) {
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Violet)
                    .clickable { onFix(row.key) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Fix", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
