package ph.appbuilders.saklolo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ph.appbuilders.saklolo.R
import ph.appbuilders.saklolo.triage.Urgency

val Page = Color(0xFFF7F5FF)
val CardWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF1E1B2E)
val InkSoft = Color(0xFF6B6585)
val NavMuted = InkSoft
val Hairline = Color(0xFFECE8F7)

val Violet = Color(0xFF7C5CFA)
val VioletLight = Color(0xFFEEE9FF)
val VioletDeep = Color(0xFF5B3FD6)
val VioletGradStart = Color(0xFF9479FF)

val Cyan = Color(0xFF5CC8E0)
val CyanLight = Color(0xFFE3F6FA)
val CyanText = Color(0xFF2A9DB8)
val OfflineCyan = Cyan

val Peach = Color(0xFFFFB48A)
val PeachLight = Color(0xFFFFF0E6)
val PeachText = Color(0xFFE07A45)

/** Emergencies only. */
val Accent = Color(0xFFEF4444)
val AccentDeep = Color(0xFFB91C1C)
val LightRed = Color(0xFFFDECEC)

val StatusGreen = Color(0xFF22C55E)
val GreenText = Color(0xFF15803D)
val Amber = Color(0xFFF59E0B)
val SafeTint = StatusGreen
val PillAmberBg = Color(0xFFFEF3DC)
val PillAmberText = Color(0xFFB45309)
val NearSecondary = InkSoft
val ShadowInk = Violet
val ChipWash = VioletLight
val PlayerWash = Page

val CriticalRed = Accent
val ForestMid = Color(0xFF065F46)
val ForestMint = StatusGreen
val MintWash = Color(0xFFF4F5F8)
val SafeGreen = Color(0xFF1B5E20)
val SafeCard = Color(0xFF15803D)
val HelpAmber = Amber
val HelpInk = Ink
val RingPink = LightRed

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_semibold, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold),
)

private val Colors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    background = Page,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
)

private fun style(size: Int, weight: FontWeight, line: Int) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    color = Ink,
)

private val Type = Typography(
    bodyLarge = style(16, FontWeight.Normal, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    titleLarge = style(26, FontWeight.SemiBold, 32),
    titleMedium = style(18, FontWeight.SemiBold, 24),
    labelLarge = style(13, FontWeight.SemiBold, 16),
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
