package io.captioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.captioner.ui.main.MainScreen
import io.captioner.ui.main.MainScreenViewModel
import io.captioner.ui.theme.CaptionerTheme

class MainActivity : ComponentActivity() {
    // Giữ lại để trigger load Whisper model vào WhisperContextHolder
    private val whisperViewModel: MainScreenViewModel by viewModels { MainScreenViewModel.factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        whisperViewModel
        setContent {
            CaptionerTheme {
                MainScreen()
            }
        }
    }
}