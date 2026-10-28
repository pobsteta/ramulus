package io.github.pobsteta.ramulus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.pobsteta.ramulus.notation.EcranNotation
import io.github.pobsteta.ramulus.ui.theme.RamulusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RamulusTheme {
                EcranNotation()
            }
        }
    }
}
