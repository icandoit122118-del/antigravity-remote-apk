package com.antigravity.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AppNotificationHelper {
    const val CHANNEL_ID = "antigravity_remote_alerts"
    const val CHANNEL_NAME = "Antigravity 실시간 알림"
    const val ACTION_NOTIFY = "com.antigravity.remote.ACTION_NOTIFY"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "PC 안티그래비티 세션 응답 및 원격 제어 알림"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createBrandLargeIconBitmap(context: Context): Bitmap? {
        return try {
            val sizePx = 128
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val clipPath = Path().apply {
                addOval(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)

            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
                ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(canvas)
            } else {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1418.toInt() }
                canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), bgPaint)
            }
            canvas.restore()

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = 0x6668C5B2
            }
            canvas.drawOval(RectF(1.5f, 1.5f, sizePx - 1.5f, sizePx - 1.5f), ringPaint)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = 7701
    ): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, pendingFlags)

        val largeIcon = createBrandLargeIconBitmap(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_antigravity)
            .setColor(0xFF68C5B2.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("Antigravity Remote")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
                    .setSummaryText("PC 실시간 연동")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        return true
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val title = intent.getStringExtra("title")?.takeIf { it.isNotBlank() }
            ?: "Antigravity Remote 알림"
        val body = intent.getStringExtra("body")?.takeIf { it.isNotBlank() }
            ?: "PC 안티그래비티 세션에서 새 알림이 도착했습니다."
        val id = intent.getIntExtra("id", 7701)
        AppNotificationHelper.showNotification(context, title, body, id)
    }
}
