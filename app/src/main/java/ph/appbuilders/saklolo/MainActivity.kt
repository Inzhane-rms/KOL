package ph.appbuilders.saklolo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ph.appbuilders.saklolo.ui.SakloloApp
import ph.appbuilders.saklolo.ui.theme.SakloloTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SakloloViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            SakloloTheme {
                SakloloApp(viewModel)
            }
        }
    }
}
