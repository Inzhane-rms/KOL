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

val Page = Color(0xFFEDEFF4)
val Ink = Color(0xFF1F2433)
/** v14 muted body text. */
val InkSoft = Color(0xFF6B7180)
val NavMuted = Color(0xFF9AA0AE)
val Accent = Color(0xFFE3242B)
/** Darkened red for text on the light-red card. #E3242B on #FDE8E8 is under 4.5. */
val AccentDeep = Color(0xFFB5161C)
val LightRed = Color(0xFFFDE8E8)
val StatusGreen = Color(0xFF059669)
/** Darkened from #059669 so green words pass 4.5:1 on white. The status dot stays StatusGreen. */
val GreenText = Color(0xFF047857)
val Amber = Color(0xFFF59E0B)
val SafeTint = Color(0xFF16A34A)
val Hairline = Color(0xFFE3E6EC)
val PillAmberBg = Color(0xFFFEF3C7)
val PillAmberText = Color(0xFF92400E)
val NearSecondary = Color(0xFFA6ABB8)
/** Status pill only. The rest of the light v14 theme stays as it is. */
val OfflineCyan = Color(0xFF22D3EE)
val ShadowInk = Color(0xFF5A6075)
val ChipWash = Color(0xFFF1F2F6)
val PlayerWash = Color(0xFFF4F5F8)

val CriticalRed = Accent
val ForestMid = Color(0xFF065F46)
val ForestMint = StatusGreen
val MintWash = Color(0xFFF4F5F8)
val SafeGreen = Color(0xFF1B5E20)
val SafeCard = Color(0xFF15803D)
val HelpAmber = Amber
val HelpInk = Ink
val RingPink = LightRed

private val Colors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Page,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
)

private val Type = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = Ink),
    titleLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun SakloloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

fun Urgency.tint(): Color = when (this) {
    Urgency.CRITICAL -> Accent
    Urgency.NEEDS_HELP -> Amber
    Urgency.SAFE -> SafeTint
}
