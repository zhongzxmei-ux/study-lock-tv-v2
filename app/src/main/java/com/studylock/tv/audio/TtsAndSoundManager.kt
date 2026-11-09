package com.studylock.tv.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import com.studylock.tv.config.PraiseMode
import com.studylock.tv.model.Question
import java.util.Locale

class TtsAndSoundManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ttsReady = false
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.95f)
        }
    }

    fun speakQuestion(q: Question) {
        if (!ttsReady) return
        val labels = listOf("A", "B", "C", "D")
        val optionsText = q.options.mapIndexed { idx, opt ->
            "${labels.getOrElse(idx) { "" }}，$opt"
        }.joinToString("。")
        val fullText = "请听题。${q.prompt}。选项是：$optionsText"
        tts?.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "question_${q.id}")
    }

    fun speakExplanation(q: Question) {
        if (!ttsReady) return
        val labels = listOf("A", "B", "C", "D")
        val ansLabel = labels.getOrElse(q.correctIndex) { "" }
        val ansText = q.options.getOrElse(q.correctIndex) { "" }
        val text = "答错了。正确答案是 $ansLabel，$ansText。解题思路：${q.explanation}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "explain_${q.id}")
    }

    fun playPraise(mode: PraiseMode) {
        when (mode) {
            PraiseMode.OFF -> Unit
            PraiseMode.SFX_ONLY -> {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 240)
            }
            PraiseMode.SFX_AND_VOICE -> {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                if (ttsReady) {
                    tts?.speak("答对了，真棒！继续保持哦！", TextToSpeech.QUEUE_FLUSH, null, "praise")
                }
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        toneGenerator.release()
    }
}
