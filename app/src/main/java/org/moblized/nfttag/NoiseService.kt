package org.moblized.nfttag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Foreground service that owns the [NoiseEngine] so playback survives the screen
 * turning off. Controlled with ACTION_START / ACTION_STOP intents.
 */
class NoiseService : Service() {

    private val engine = NoiseEngine()
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> {
                val color = runCatching {
                    NoiseColor.valueOf(intent?.getStringExtra(EXTRA_COLOR) ?: "")
                }.getOrDefault(NoiseColor.WHITE)
                val gain = intent?.getFloatExtra(EXTRA_GAIN, 0.6f) ?: 0.6f
                val sleep = intent?.getIntExtra(EXTRA_SLEEP, 0) ?: 0
                startPlayback(color, gain, sleep)
            }
        }
        return START_STICKY
    }

    private fun startPlayback(color: NoiseColor, gain: Float, sleepMinutes: Int) {
        ensureChannel()
        startForegroundCompat(buildNotification(color))

        engine.start(color, gain)
        running = true
        activeColor = color

        // (Re)arm the sleep timer.
        sleepRunnable?.let { handler.removeCallbacks(it) }
        sleepRunnable = null
        if (sleepMinutes > 0) {
            val r = Runnable { stopEverything() }
            sleepRunnable = r
            handler.postDelayed(r, sleepMinutes * 60_000L)
        }
    }

    private fun stopEverything() {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        sleepRunnable = null
        engine.stop()
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engine.stop()
        running = false
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.noise_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.noise_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(color: NoiseColor): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, NoiseService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val label = color.name.lowercase().replaceFirstChar { it.uppercase() }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("$label noise playing")
            .setContentText("Tap to open · swipe the button to stop")
            .setSmallIcon(R.drawable.ic_stat_noise)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, "Stop", stopIntent).build()
            )
            .build()
    }

    companion object {
        const val ACTION_START = "org.moblized.nfttag.START"
        const val ACTION_STOP = "org.moblized.nfttag.STOP"
        const val EXTRA_COLOR = "color"
        const val EXTRA_GAIN = "gain"
        const val EXTRA_SLEEP = "sleep"

        private const val CHANNEL_ID = "noise"
        private const val NOTIF_ID = 1

        /** Observable playback state for the Compose UI. */
        var running by mutableStateOf(false)
            private set
        var activeColor by mutableStateOf(NoiseColor.WHITE)
            private set

        fun start(context: Context, color: NoiseColor, gain: Float, sleepMinutes: Int) {
            val i = Intent(context, NoiseService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_COLOR, color.name)
                .putExtra(EXTRA_GAIN, gain)
                .putExtra(EXTRA_SLEEP, sleepMinutes)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, NoiseService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
