package ph.appbuilders.saklolo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.triage.Urgency

val ForestDeep = Color(0xFF022C22)
val ForestMid = Color(0xFF065F46)
val ForestMint = Color(0xFF10B981)
val Ink = Color(0xFF0B2E22)
val InkSoft = Color(0xFF3F5A50)
val CriticalRed = Color(0xFFC62828)
val HelpAmber = Color(0xFFF9A825)
val HelpInk = Color(0xFF111111)
val SafeGreen = Color(0xFF1B5E20)
val OrbDeep = Color(0xFF450A0A)
val RingPink = Color(0xFFFCA5A5)

private val Colors = lightColorScheme(
    primary = SafeGreen,
    onPrimary = Color.White,
    background = ForestDeep,
    onBackground = Color.White,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
)

private val Type = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = Ink),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
)

@Composable
fun SakloloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

fun Urgency.stripe(): Color = when (this) {
    Urgency.CRITICAL -> CriticalRed
    Urgency.NEEDS_HELP -> HelpAmber
    Urgency.SAFE -> SafeGreen
}
