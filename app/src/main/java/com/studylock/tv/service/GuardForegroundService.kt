package com.studylock.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.studylock.tv.R
import com.studylock.tv.audio.TtsAndSoundManager
import com.studylock.tv.config.LockConfig
import com.studylock.tv.data.QuestionRepository
import com.studylock.tv.overlay.QuizOverlayManager
import java.util.Calendar

class GuardForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.studylock.tv.START_GUARD"
        const val ACTION_STOP = "com.studylock.tv.STOP_GUARD"
        const val ACTION_TEST_NOW = "com.studylock.tv.TEST_NOW"
        const val ACTION_RESET_TIMER = "com.studylock.tv.RESET_TIMER"
        const val BROADCAST_TICK = "com.studylock.tv.TIMER_TICK"
        const val EXTRA_REMAINING_SEC = "remaining_sec"
    }

    private lateinit var config: LockConfig
    private lateinit var repo: QuestionRepository
    private lateinit var ttsManager: TtsAndSoundManager
    private lateinit var overlayManager: QuizOverlayManager

    private val handler = Handler(Looper.getMainLooper())
    private var remainingSeconds = 15 * 60
    private var strictTickCounter = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!config.guardEnabled) return

            val nowMs = System.currentTimeMillis()
            val isTempPaused = nowMs < config.tempPauseUntilMillis
            val inWindow = isWithinActiveSchedule()

            if (overlayManager.isShowing) {
                strictTickCounter++
                if (config.strictMode && strictTickCounter % 2 == 0) {
                    overlayManager.bringToFrontIfNeeded()
                }
            } else if (!isTempPaused && inWindow) {
                remainingSeconds--
                if (remainingSeconds <= 0) {
                    overlayManager.showQuiz()
                }
            }

            sendBroadcast(Intent(BROADCAST_TICK).apply {
                setPackage(packageName)
                putExtra(EXTRA_REMAINING_SEC, remainingSeconds.coerceAtLeast(0))
            })

            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        config = LockConfig(this)
        repo = QuestionRepository(this)
        ttsManager = TtsAndSoundManager(this)
        overlayManager = QuizOverlayManager(this, config, repo, ttsManager) {
            remainingSeconds = config.watchMinutes * 60
        }
        remainingSeconds = config.watchMinutes * 60
        startForeground(1001, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                config.guardEnabled = false
                handler.removeCallbacks(tickRunnable)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TEST_NOW -> {
                overlayManager.showQuiz()
            }
            ACTION_RESET_TIMER -> {
                remainingSeconds = config.watchMinutes * 60
            }
            else -> {
                config.guardEnabled = true
                handler.removeCallbacks(tickRunnable)
                handler.post(tickRunnable)
            }
        }
        return START_STICKY
    }

    private fun isWithinActiveSchedule(): Boolean {
        if (config.scheduleAllDay) return true
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = config.scheduleStartMinutes
        val end = config.scheduleEndMinutes
        return if (start <= end) {
            nowMin in start..end
        } else {
            nowMin >= start || nowMin <= end
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "study_lock_guard"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "学习锁守护服务", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("学习锁守护运行中")
            .setContentText("每观看 ${config.watchMinutes} 分钟自动弹出答题")
            .setSmallIcon(R.drawable.ic_study_lock)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        ttsManager.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
