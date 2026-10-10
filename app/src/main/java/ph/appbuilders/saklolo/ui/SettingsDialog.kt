package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import ph.appbuilders.saklolo.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ph.appbuilders.saklolo.DemoConfig
import ph.appbuilders.saklolo.stt.SpeechLanguage
import ph.appbuilders.saklolo.ui.theme.Accent
import ph.appbuilders.saklolo.ui.theme.CardWhite
import ph.appbuilders.saklolo.ui.theme.Hairline
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.Poppins
import ph.appbuilders.saklolo.ui.theme.SafeGreen

@Composable
fun SettingsDialog(
    initial: DemoConfig,
    deleting: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, restrict: Boolean, allowlist: String, language: SpeechLanguage) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.deviceName) }
    var restrict by remember { mutableStateOf(initial.restrictPeers) }
    var allowlist by remember { mutableStateOf(initial.allowlist) }
    var language by remember { mutableStateOf(initial.language) }
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Demo settings", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            FieldLabel("Phone name")
            DemoField(name) { name = it }
            Text("Language for the next recording", color = InkSoft, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeechLanguage.entries.forEach { option ->
                    val selected = option == language
                    Text(
                        text = option.label,
                        color = if (selected) Color.White else Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) SafeGreen else Color.White)
                            .border(1.dp, SafeGreen, RoundedCornerShape(20.dp))
                            .clickable { language = option }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Only these phone names", color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = restrict,
                    onCheckedChange = { restrict = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = SafeGreen),
                )
            }
            FieldLabel("Allowed names, comma separated")
            DemoField(allowlist) { allowlist = it }
            Text(
                "Example: Camon 40, Spark 30. Leave the switch off to relay with every KOL phone.",
                color = InkSoft,
                fontSize = 14.sp,
            )
            Text(initial.gemmaStatus, color = InkSoft, fontSize = 14.sp)
            TextButton(onClick = { confirmDelete = true }, enabled = !deleting, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.legal_delete), color = Accent, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Cancel", color = InkSoft, fontSize = 16.sp)
                }
                TextButton(
                    onClick = { onSave(name, restrict, allowlist, language) },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text("Save", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(28.dp))
                    .background(Page)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardWhite)
                        .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.legal_delete),
                        color = Ink,
                        fontFamily = Poppins,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.legal_delete_body),
                        color = Ink,
                        fontFamily = Poppins,
                        fontSize = 14.sp,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(CardWhite)
                            .border(1.dp, Hairline, RoundedCornerShape(28.dp))
                            .clickable { confirmDelete = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.legal_cancel), color = Ink, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Accent)
                            .alpha(if (deleting) 0.4f else 1f)
                            .clickable(enabled = !deleting) {
                                confirmDelete = false
                                onDeleteAll()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.legal_delete_yes), color = CardWhite, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = InkSoft, fontSize = 14.sp)
}

@Composable
private fun DemoField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = Ink, fontSize = 18.sp),
        cursorBrush = SolidColor(Ink),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(Page, RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}
