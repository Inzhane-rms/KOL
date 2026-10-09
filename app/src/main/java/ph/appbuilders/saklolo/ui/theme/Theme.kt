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

val Page = Color(0xFFF1F5F3)
val ForestMid = Color(0xFF065F46)
val ForestMint = Color(0xFF10B981)
val MintWash = Color(0xFFD1FAE5)
val Ink = Color(0xFF0B2E22)
val InkSoft = Color(0xFF4B5F57)
val CriticalRed = Color(0xFFC62828)
val HelpAmber = Color(0xFFF9A825)
val HelpInk = Color(0xFF111111)
val SafeGreen = Color(0xFF1B5E20)
val SafeCard = Color(0xFF2E7D32)
val RingPink = Color(0xFFFCA5A5)

private val Colors = lightColorScheme(
    primary = ForestMid,
    onPrimary = Color.White,
    background = Page,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
)

private val Type = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = Ink),
    titleLarge = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun SakloloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

fun Urgency.cardFill(): Color = when (this) {
    Urgency.CRITICAL -> CriticalRed
    Urgency.NEEDS_HELP -> HelpAmber
    Urgency.SAFE -> SafeCard
}

fun Urgency.onCard(): Color = when (this) {
    Urgency.NEEDS_HELP -> HelpInk
    else -> Color.White
}
