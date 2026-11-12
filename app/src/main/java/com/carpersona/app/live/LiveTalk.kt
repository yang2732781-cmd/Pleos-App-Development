package com.carpersona.app.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 자유 대화 모드 (하이브리드의 "AI 음성" 쪽).
 *
 * 평소 대사(출발 인사·기념일·경고)는 기기 안의 규칙 대사 + 안드로이드 기본 목소리로 말하고,
 * 운전자가 🗣 자유 대화를 켰을 때만 OpenAI Realtime API 로 "말로 듣고 말로 답하는" 대화를 합니다.
 * 그래서 비용은 자유 대화를 켠 시간에만 듭니다.
 *
 *  - 모델: gpt-realtime-2.1-mini (공식 가격표 기준 Realtime 중 가장 저렴)
 *  - 연결: WebSocket  wss://api.openai.com/v1/realtime?model=…  (Authorization: Bearer 키)
 *  - 소리: 마이크 24kHz 16bit 모노 PCM → input_audio_buffer.append (base64)
 *          답은 response.output_audio.delta (base64 PCM) → 스피커
 *  - 말 차례는 서버가 알아챔(semantic_vad). 차가 말하는 중에 사람이 말하면 차가 바로 멈춤.
 *  - 성격·기분·지역 사투리·차 상태는 session.update 의 instructions 로 보냄 (LivePrompt)
 *  - 키는 설정 화면에서만 입력. 시간 제한 [MAX_MS] 이 지나면 자동으로 끝남 (요금 보호)
 *
 * 이 클래스는 실제 서버에 연결해 검증하지 못했습니다 (문서 기준 구현). 연결이 안 되면 [error] 에 이유가 뜨고
 * 앱은 평소 방식(기본 대사 + 기본 목소리)으로 그대로 동작합니다.
 */
class LiveTalk(context: Context) {
    companion object {
        const val MODEL = "gpt-realtime-2.1-mini"
        const val VOICE = "marin"
        /** 한 번 켜면 최대 이 시간까지만 (요금 보호) */
        const val MAX_MS = 5 * 60_000L
        private const val RATE = 24_000
        private const val TAG = "CarPersona/Live"
    }

    enum class Phase { OFF, CONNECTING, LISTENING, SPEAKING }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _phase = mutableStateOf(Phase.OFF)
    val phase: State<Phase> = _phase
    private val _error = mutableStateOf<String?>(null)
    /** 마지막 오류 (화면에 보여 줌). 정상이면 null */
    val error: State<String?> = _error
    val active: Boolean get() = _phase.value != Phase.OFF

    /** 차가 한 말(글자)이 완성될 때마다 불림 → 대화창에 추가 */
    var onCarLine: (String) -> Unit = {}
    /** 사람이 한 말(글자). 서버가 받아쓰기를 줄 때만 불림 */
    var onUserLine: (String) -> Unit = {}

    private var socket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var micThread: Thread? = null
    private var player: ExecutorService? = null
    @Volatile private var generation = 0
    @Volatile private var running = false
    private var instructions = ""

    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    // 대화하는 동안은 음악을 잠깐 멈춤(GAIN_TRANSIENT). 전화·음성 명령이 포커스를 가져가면 대화를 끝냄.
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                main.post { stop("다른 소리(전화·음성 명령)가 시작돼 자유 대화를 끝냈어요.") }
            }
        }.build()

    private val timeout = Runnable { stop("자유 대화는 한 번에 ${MAX_MS / 60_000}분까지예요. 다시 누르면 이어서 할 수 있어요.") }

    /** 시작. 마이크 권한은 부르는 쪽에서 미리 확인 */
    fun start(apiKey: String, instructions: String) {
        if (active) return
        if (apiKey.isBlank()) { _error.value = "설정에서 OpenAI API 키를 먼저 넣어 주세요."; return }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _error.value = "지금은 소리를 낼 수 없어요 (전화 중이거나 다른 안내가 나오는 중)."; return
        }
        _error.value = null
        _phase.value = Phase.CONNECTING
        this.instructions = instructions
        running = true
        val c = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build()
        client = c
        val req = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=$MODEL")
            .header("Authorization", "Bearer $apiKey")
            .build()
        socket = c.newWebSocket(req, Listener())
        main.postDelayed(timeout, MAX_MS)
    }

    /** 성격·기분·사투리가 바뀌면 대화 도중에도 다시 알려 줌 */
    fun updateInstructions(text: String) {
        if (!active || text == instructions) return
        instructions = text
        socket?.send(sessionUpdate().toString())
    }

    fun stop(reason: String? = null) {
        if (!running && _phase.value == Phase.OFF) { if (reason != null) _error.value = reason; return }
        running = false
        main.removeCallbacks(timeout)
        generation++
        runCatching { socket?.close(1000, "bye") }
        socket = null
        runCatching { client?.dispatcher?.executorService?.shutdown() }
        client = null
        micThread = null
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null
        player?.shutdownNow(); player = null
        runCatching { track?.pause(); track?.flush(); track?.release() }; track = null
        audio.abandonAudioFocusRequest(focus)
        _phase.value = Phase.OFF
        if (reason != null) _error.value = reason
    }

    // ───────── 서버에 보내는 설정 ─────────

    private fun sessionUpdate() = JSONObject().put("type", "session.update").put("session", JSONObject()
        .put("type", "realtime")
        .put("instructions", instructions)
        .put("output_modalities", JSONArray().put("audio"))
        .put("audio", JSONObject()
            .put("input", JSONObject()
                .put("format", JSONObject().put("type", "audio/pcm").put("rate", RATE))
                .put("turn_detection", JSONObject().put("type", "semantic_vad")
                    .put("create_response", true).put("interrupt_response", true)))
            .put("output", JSONObject()
                .put("format", JSONObject().put("type", "audio/pcm").put("rate", RATE))
                .put("voice", VOICE))))

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(sessionUpdate().toString())
            main.post { if (running) { startAudio(webSocket); _phase.value = Phase.LISTENING } }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val e = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (e.optString("type")) {
                "response.output_audio.delta" -> {
                    val pcm = runCatching { Base64.decode(e.optString("delta"), Base64.DEFAULT) }.getOrNull() ?: return
                    val gen = generation
                    main.post { if (running) _phase.value = Phase.SPEAKING }
                    runCatching { player?.execute { if (gen == generation) track?.write(pcm, 0, pcm.size) } }
                }
                "response.output_audio_transcript.done" ->
                    e.optString("transcript").takeIf { it.isNotBlank() }?.let { t -> main.post { onCarLine(t.trim()) } }
                "conversation.item.input_audio_transcription.completed" ->
                    e.optString("transcript").takeIf { it.isNotBlank() }?.let { t -> main.post { onUserLine(t.trim()) } }
                // 사람이 말을 시작하면 차는 하던 말을 바로 멈춤
                "input_audio_buffer.speech_started" -> {
                    generation++
                    runCatching { track?.pause(); track?.flush(); track?.play() }
                    main.post { if (running) _phase.value = Phase.LISTENING }
                }
                "response.done" -> main.post { if (running) _phase.value = Phase.LISTENING }
                "error" -> {
                    val msg = e.optJSONObject("error")?.optString("message").orEmpty()
                    Log.w(TAG, "서버 오류: $msg")
                    main.post { _error.value = "AI 서버 오류: ${msg.take(120)}" }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "연결 실패", t)
            val why = when (response?.code) {
                401 -> "OpenAI API 키가 맞지 않아요. 설정에서 다시 확인해 주세요."
                403, 404 -> "이 키로는 $MODEL 모델을 쓸 수 없어요."
                429 -> "사용 한도를 넘었어요. OpenAI 결제·한도를 확인해 주세요."
                else -> "AI 서버에 연결하지 못했어요 (인터넷 확인). 평소 대화는 그대로 돼요."
            }
            main.post { stop(why) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            main.post { if (running) stop("자유 대화 연결이 끝났어요.") }
        }
    }

    // ───────── 마이크·스피커 ─────────

    @SuppressLint("MissingPermission")
    private fun startAudio(ws: WebSocket) {
        val outMin = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(outMin, RATE))   // 약 0.5초
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        track = t
        player = Executors.newSingleThreadExecutor()

        val inMin = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_COMMUNICATION: 스피커에서 나온 차 목소리가 다시 마이크로 들어가는 것(울림)을 기기가 줄여 줌
        val r = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(inMin, RATE / 5 * 2))
        }.getOrNull()
        if (r == null || r.state != AudioRecord.STATE_INITIALIZED) { stop("마이크를 열지 못했어요."); return }
        recorder = r
        r.startRecording()
        micThread = Thread({
            val buf = ByteArray(RATE / 25 * 2)   // 40ms
            while (running && recorder === r) {
                val n = runCatching { r.read(buf, 0, buf.size) }.getOrDefault(-1)
                if (n <= 0) break
                val b64 = Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)
                ws.send("{\"type\":\"input_audio_buffer.append\",\"audio\":\"$b64\"}")
            }
        }, "carpersona-mic").also { it.start() }
    }
}
