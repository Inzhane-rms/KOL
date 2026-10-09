package ph.appbuilders.saklolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
import ph.appbuilders.saklolo.ui.theme.SafeGreen

@Composable
fun SettingsDialog(
    initial: DemoConfig,
    onDismiss: () -> Unit,
    onSave: (name: String, restrict: Boolean, allowlist: String, language: SpeechLanguage) -> Unit,
) {
    var name by remember { mutableStateOf(initial.deviceName) }
    var restrict by remember { mutableStateOf(initial.restrictPeers) }
    var allowlist by remember { mutableStateOf(initial.allowlist) }
    var language by remember { mutableStateOf(initial.language) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                "Example: Camon 40, Spark 30. Leave the switch off to relay with every B-LINK phone.",
                color = InkSoft,
                fontSize = 14.sp,
            )
            Text(initial.gemmaStatus, color = InkSoft, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) {
                    Text("Cancel", color = InkSoft, fontSize = 16.sp)
                }
                TextButton(
                    onClick = { onSave(name, restrict, allowlist, language) },
                    modifier = Modifier.height(56.dp),
                ) {
                    Text("Save", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            .background(Color(0xFFF3F7F5), RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}
