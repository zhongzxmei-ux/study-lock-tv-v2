package com.studylock.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.studylock.tv.audio.TtsAndSoundManager
import com.studylock.tv.config.LockConfig
import com.studylock.tv.config.PraiseMode
import com.studylock.tv.data.QuestionRepository
import com.studylock.tv.service.GuardForegroundService
import com.studylock.tv.util.AppDisguiseManager

class MainActivity : AppCompatActivity() {
    private lateinit var config: LockConfig
    private lateinit var repo: QuestionRepository
    private lateinit var ttsManager: TtsAndSoundManager

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sec = intent?.getIntExtra(GuardForegroundService.EXTRA_REMAINING_SEC, 0) ?: return
            val min = sec / 60
            val rem = sec % 60
            findViewById<TextView>(R.id.tvStatusHeadline).text =
                String.format("%02d:%02d 后可继续观看", min, rem)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = LockConfig(this)
        repo = QuestionRepository(this)
        ttsManager = TtsAndSoundManager(this)

        findViewById<TextView>(R.id.tvHeaderBadge).text =
            "题库 ${repo.getTotalCount()} 题 · v1.0 电视版"

        bindActions()
        refreshUi()
        promptPinIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(GuardForegroundService.BROADCAST_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tickReceiver, filter)
        }
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(tickReceiver)
        } catch (_: Exception) {
        }
    }

    private fun promptPinIfNeeded() {
        if (!config.guardEnabled) return
        val input = EditText(this).apply { hint = "输入家长密码（默认 0000）" }
        AlertDialog.Builder(this)
            .setTitle("家长验证")
            .setMessage("守护已开启，请输入数字密码进入控制台")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("确认") { _, _ ->
                if (input.text.toString() != config.parentalPin) {
                    Toast.makeText(this, "密码错误，已退出", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("退出") { _, _ -> finish() }
            .show()
    }

    private fun bindActions() {
        findViewById<Button>(R.id.btnToggleGuard).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予「悬浮窗/显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return@setOnClickListener
            }
            val next = !config.guardEnabled
            config.guardEnabled = next
            val svc = Intent(this, GuardForegroundService::class.java).apply {
                action = if (next) GuardForegroundService.ACTION_START else GuardForegroundService.ACTION_STOP
            }
            if (next) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
                else startService(svc)
            } else {
                startService(svc)
            }
            refreshUi()
        }

        // 观看时长预设
        val durationMap = mapOf(
            R.id.btnDur15 to 15, R.id.btnDur20 to 20, R.id.btnDur30 to 30,
            R.id.btnDur45 to 45, R.id.btnDur60 to 60
        )
        durationMap.forEach { (id, mins) ->
            findViewById<Button>(id).setOnClickListener {
                config.watchMinutes = mins
                refreshUi()
            }
        }
        findViewById<Button>(R.id.btnDurMinus).setOnClickListener {
            config.watchMinutes = (config.watchMinutes - 1).coerceAtLeast(1)
            refreshUi()
        }
        findViewById<Button>(R.id.btnDurPlus).setOnClickListener {
            config.watchMinutes = (config.watchMinutes + 1).coerceAtMost(600)
            refreshUi()
        }

        // 每次答题数量
        val qCountMap = mapOf(
            R.id.btnQ1 to 1, R.id.btnQ2 to 2, R.id.btnQ3 to 3, R.id.btnQ5 to 5
        )
        qCountMap.forEach { (id, cnt) ->
            findViewById<Button>(id).setOnClickListener {
                config.questionsPerRound = cnt
                refreshUi()
            }
        }

        // 答错等待秒数
        val waitMap = mapOf(
            R.id.btnWait30 to 30, R.id.btnWait60 to 60, R.id.btnWait90 to 90, R.id.btnWait120 to 120
        )
        waitMap.forEach { (id, sec) ->
            findViewById<Button>(id).setOnClickListener {
                config.wrongWaitSeconds = sec
                refreshUi()
            }
        }

        // 答对鼓励
        findViewById<Button>(R.id.btnPraiseOff).setOnClickListener {
            config.praiseMode = PraiseMode.OFF; refreshUi()
        }
        findViewById<Button>(R.id.btnPraiseSfx).setOnClickListener {
            config.praiseMode = PraiseMode.SFX_ONLY; refreshUi()
        }
        findViewById<Button>(R.id.btnPraiseVoice).setOnClickListener {
            config.praiseMode = PraiseMode.SFX_AND_VOICE; refreshUi()
        }
        findViewById<Button>(R.id.btnTestPraise).setOnClickListener {
            ttsManager.playPraise(PraiseMode.SFX_AND_VOICE)
        }

        // 伪装应用名与图标
        findViewById<Button>(R.id.btnAliasDefault).setOnClickListener {
            config.disguiseAlias = "DefaultAlias"
            AppDisguiseManager.switchAlias(this, "DefaultAlias")
            Toast.makeText(this, "已恢复原名：学习锁（重启桌面生效）", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAliasSettings).setOnClickListener {
            config.disguiseAlias = "SettingsAlias"
            AppDisguiseManager.switchAlias(this, "SettingsAlias")
            Toast.makeText(this, "已切换为「系统设置」", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAliasBattery).setOnClickListener {
            config.disguiseAlias = "BatteryAlias"
            AppDisguiseManager.switchAlias(this, "BatteryAlias")
            Toast.makeText(this, "已切换为「电池与性能」", Toast.LENGTH_SHORT).show()
        }

        // 立即测试弹题
        findViewById<Button>(R.id.btnTestQuizNow).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val svc = Intent(this, GuardForegroundService::class.java).apply {
                action = GuardForegroundService.ACTION_TEST_NOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        }

        findViewById<Button>(R.id.btnResetTimer).setOnClickListener {
            startService(Intent(this, GuardForegroundService::class.java).apply {
                action = GuardForegroundService.ACTION_RESET_TIMER
            })
            Toast.makeText(this, "计时已重置为 ${config.watchMinutes} 分钟", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshUi() {
        val enabled = config.guardEnabled
        findViewById<Button>(R.id.btnToggleGuard).text = if (enabled) "暂停守护" else "开启守护"
        findViewById<TextView>(R.id.tvGuardSub).text =
            if (enabled) "守护已开启 · 到点自动全屏弹题" else "守护已关闭 · 点击右侧按钮开启"
        if (!enabled) {
            findViewById<TextView>(R.id.tvStatusHeadline).text = "守护未开启"
        }
        findViewById<TextView>(R.id.tvCustomDuration).text = "${config.watchMinutes} 分钟"
        findViewById<TextView>(R.id.tvCurrentSummary).text =
            "每轮 ${config.watchMinutes} 分钟 · 每次 ${config.questionsPerRound} 题 · 答错等待 ${config.wrongWaitSeconds} 秒"
    }

    override fun onDestroy() {
        ttsManager.release()
        super.onDestroy()
    }
}
