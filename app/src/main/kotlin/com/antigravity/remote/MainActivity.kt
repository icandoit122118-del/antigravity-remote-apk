package com.antigravity.remote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.antigravity.remote.ui.AntigravityRemoteScreen
import com.antigravity.remote.ui.theme.AntigravityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        AppNotificationHelper.ensureChannel(this)
        intent?.let { handleNotificationIntent(it) }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            AntigravityTheme {
                AntigravityRemoteScreen()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isRunningInstrumentationTest()) {
            window?.decorView?.postDelayed({
                if (!isFinishing && !isDestroyed &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                }
            }, 800L)
        }
    }

    private fun isRunningInstrumentationTest(): Boolean {
        return try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent) {
        if (intent.getBooleanExtra("trigger_test_notification", false)) {
            val title = intent.getStringExtra("title") ?: "Antigravity Remote 알림 테스트"
            val body = intent.getStringExtra("body") ?: "PC 안티그래비티 세션과 실시간 모바일 알림 연동이 정상 작동합니다."
            AppNotificationHelper.showNotification(this, title, body, 7701)
        }
    }
}
