package com.carpersona.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.carpersona.app.persona.Register
import java.util.Locale

/**
 * 음성 입출력. 교체 가능한 모듈입니다.
 * Pleos STT·TTS SDK가 아직 "준비 중"이라 지금은 안드로이드 기본 엔진을 씁니다.
 * Pleos SDK가 열리면 이 인터페이스만 새로 구현하면 됩니다.
 */
interface Voice {
    /** 말하기. 말투에 따라 목소리 높이·빠르기를 바꿈. queue=true 면 앞 문장 다음에 이어서 */
    fun speak(text: String, register: Register, queue: Boolean = false)
    /** 듣기 시작. 알아들으면 onText */
    fun listen(onText: (String) -> Unit, onError: (String) -> Unit)
    val listening: androidx.compose.runtime.State<Boolean>
    fun release()
}

class AndroidVoice(context: Context) : Voice {
    private val appContext = context.applicationContext
    private var ready = false
    // ── 오디오 포커스 (Pleos 오디오 포커스 가이드)
    // 차의 음성 비서 말소리는 ASSISTANT 용도. 음악은 끄지 않고 잠깐 줄이도록(GAIN_TRANSIENT_MAY_DUCK) 요청하고,
    // 다른 앱(Gleo 음성 명령·전화 등)이 포커스를 가져가면 바로 멈춥니다.
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (ready) tts.stop()
                releaseFocus()
            }
        }
        .build()
    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.KOREAN
            tts.setAudioAttributes(attrs)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (!tts.isSpeaking) releaseFocus() }
                @Deprecated("old API") override fun onError(utteranceId: String?) { releaseFocus() }
            })
        }
    }

    private var holdingFocus = false

    private fun takeFocus(): Boolean {
        if (holdingFocus) return true
        holdingFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdingFocus
    }

    private fun releaseFocus() {
        if (holdingFocus) { audio.abandonAudioFocusRequest(focus); holdingFocus = false }
    }
    private var recognizer: SpeechRecognizer? = null
    private val _listening = mutableStateOf(false)
    override val listening: androidx.compose.runtime.State<Boolean> = _listening

    /** 꺼져 있으면 말하지 않음 (설정의 "목소리로 말하기") */
    var enabled = true

    override fun speak(text: String, register: Register, queue: Boolean) {
        if (!enabled || !ready) return
        // 포커스를 못 받으면 (예: 비상 상황·전화 중) 말하지 않음
        if (!takeFocus()) { Log.i("CarPersona/Voice", "오디오 포커스 거절 → 말하지 않음"); return }
        // 성격별 목소리: 새내기는 높고 빠르게, 어르신은 낮고 느리게
        when (register) {
            Register.ROOKIE -> { tts.setPitch(1.25f); tts.setSpeechRate(1.1f) }
            Register.BUDDY -> { tts.setPitch(1.0f); tts.setSpeechRate(1.0f) }
            Register.ELDER -> { tts.setPitch(0.75f); tts.setSpeechRate(0.85f) }
        }
        val spoken = text.replace("[차량 정보]", "차량 정보.")
        tts.speak(spoken, if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "cp-${System.nanoTime()}")
    }

    override fun listen(onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("이 기기에는 음성 인식이 없어요. 아래 칸에 글자로 물어봐 주세요."); return
        }
        tts.stop()
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                _listening.value = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onError("잘 못 들었어요. 다시 말해 주세요.") else onText(text)
            }
            override fun onError(error: Int) {
                _listening.value = false
                Log.w("CarPersona/Voice", "인식 오류 $error")
                onError(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "마이크 권한이 필요해요." else "잘 못 들었어요. 다시 말해 주세요.")
            }
            override fun onReadyForSpeech(params: Bundle?) { _listening.value = true }
            override fun onEndOfSpeech() { _listening.value = false }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        })
    }

    fun stopSpeaking() { if (ready) tts.stop(); releaseFocus() }

    override fun release() {
        recognizer?.destroy()
        releaseFocus()
        tts.shutdown()
    }
}
