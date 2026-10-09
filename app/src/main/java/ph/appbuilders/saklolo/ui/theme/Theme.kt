package ph.appbuilders.saklolo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ph.appbuilders.saklolo.triage.Urgency

private val Ink = Color(0xFF12161B)
private val Panel = Color(0xFF1C232B)
private val PanelHigh = Color(0xFF2A343F)
private val Paper = Color(0xFFF4F7FA)
private val Muted = Color(0xFFC5D0DA)
private val Rescue = Color(0xFFE23D2B)

private val SakloloColors = darkColorScheme(
    primary = Rescue,
    onPrimary = Color.White,
    secondary = Color(0xFFFFB020),
    onSecondary = Color(0xFF1A1204),
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Muted,
    error = Color(0xFFFF6B5C),
    onError = Color.White,
)

@Composable
fun SakloloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SakloloColors, content = content)
}

fun Urgency.color(): Color = when (this) {
    Urgency.CRITICAL -> Color(0xFFFF4D3A)
    Urgency.NEEDS_HELP -> Color(0xFFFFB020)
    Urgency.SAFE -> Color(0xFF3DDC97)
}
