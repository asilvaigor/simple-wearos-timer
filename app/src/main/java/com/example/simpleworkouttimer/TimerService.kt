package com.example.simpleworkouttimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class TimerService : Service(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var targetTimeMillis: Long = 0L
    private var _currentTimeMillis: Long = 0L
    val currentTimeMillis: Long
        get() = _currentTimeMillis

    private fun setCurrentTimeMillis(value: Long) {
        _currentTimeMillis = value
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    companion object {
        const val ACTION_START_TIMER = "com.example.simpleworkouttimer.ACTION_START_TIMER"
        const val ACTION_PAUSE_TIMER = "com.example.simpleworkouttimer.ACTION_PAUSE_TIMER"
        const val ACTION_STOP_TIMER = "com.example.simpleworkouttimer.ACTION_STOP_TIMER"
        const val EXTRA_TARGET_TIME_MILLIS = "com.example.simpleworkouttimer.EXTRA_TARGET_TIME_MILLIS"
        const val NOTIFICATION_CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_TIMER_FINISHED = "com.example.simpleworkouttimer.TIMER_FINISHED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimpleWorkoutTimer::WakeLockTag")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                targetTimeMillis = intent.getLongExtra(EXTRA_TARGET_TIME_MILLIS, 0L)

                // Stop any existing timer job and reset time
                timerJob?.cancel()
                setCurrentTimeMillis(0L)

                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)

                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(10*60*1000L /*10 minutes*/)
                }
                startTimer()
            }
            ACTION_PAUSE_TIMER -> {
                pauseTimer()
            }
            ACTION_STOP_TIMER -> {
                stopTimerAndService()
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Workout Timer Active")
            .setContentText("Timer is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startTimer() {
        // Ensure any previous job is cancelled before starting a new one.
        timerJob?.cancel()

        timerJob = launch {
            var internalCurrentTime = _currentTimeMillis

            var targetReached = false
            var lastUpdateTime = System.currentTimeMillis()

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - lastUpdateTime
                lastUpdateTime = currentTime

                internalCurrentTime += elapsedTime
                setCurrentTimeMillis(internalCurrentTime)

                if (!targetReached && targetTimeMillis > 0L && internalCurrentTime >= targetTimeMillis) {
                    targetReached = true
                    sendFinishedBroadcast()
                    vibrate()
                }

                delay(100)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun stopTimerAndService() {
        timerJob?.cancel()
        timerJob = null
        setCurrentTimeMillis(0L)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendFinishedBroadcast() {
        val intent = Intent(ACTION_TIMER_FINISHED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            val vibrationEffect =
                VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(vibrationEffect)
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Timer Service Notifications",
            NotificationManager.IMPORTANCE_LOW
        )
        serviceChannel.description = "Shows the active timer in the notification shade."
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        job.cancel()
    }
}
