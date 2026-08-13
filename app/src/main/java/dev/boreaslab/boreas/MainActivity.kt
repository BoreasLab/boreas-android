package dev.boreaslab.boreas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.ui.BoreasApp
import dev.boreaslab.boreas.ui.BoreasViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BoreasViewModel by viewModels { BoreasViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoreasTheme {
                BoreasApp(viewModel = viewModel)
            }
        }
    }
}
