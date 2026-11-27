package ar.matiasblaiotta.cuenotebooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.matiasblaiotta.cuenotebooks.ui.App
import ar.matiasblaiotta.cuenotebooks.ui.QTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QTheme { App(applicationContext) } }
    }
}
