package com.antigravity.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.antigravity.remote.ui.AntigravityRemoteScreen
import com.antigravity.remote.ui.theme.AntigravityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#11161B")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#11161B"))
        )
        setContent {
            AntigravityTheme {
                AntigravityRemoteScreen()
            }
        }
    }
}
