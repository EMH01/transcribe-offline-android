package com.emh01.transcribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emh01.transcribe.ui.TranscribeApp
import com.emh01.transcribe.ui.TranscribeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TranscribeTheme {
                TranscribeApp()
            }
        }
    }
}
