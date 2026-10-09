package ph.appbuilders.saklolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ph.appbuilders.saklolo.ui.SakloloApp
import ph.appbuilders.saklolo.ui.theme.SakloloTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SakloloViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SakloloTheme {
                SakloloApp(viewModel)
            }
        }
    }
}
