package com.carpersona.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.carpersona.app.ai.ClaudeBrain
import com.carpersona.app.ai.RuleBrain
import com.carpersona.app.life.AppClock
import com.carpersona.app.life.CarCompanion
import com.carpersona.app.life.LifeStore
import com.carpersona.app.navi.NaviSource
import com.carpersona.app.navi.PleosNavi
import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.Intent
import com.carpersona.app.persona.PersonaEngine
import com.carpersona.app.ui.CarDataScreen
import com.carpersona.app.ui.DemoScreen
import com.carpersona.app.ui.GrowthScreen
import com.carpersona.app.ui.Onboarding
import com.carpersona.app.live.LivePrompt
import com.carpersona.app.live.LiveTalk
import com.carpersona.app.ui.LiveUi
import com.carpersona.app.ui.NavItem
import com.carpersona.app.ui.PleosAppShell
import com.carpersona.app.ui.PleosTheme
import com.carpersona.app.ui.StatusChip
import com.carpersona.app.vehicle.DrivingRestriction
import com.carpersona.app.ui.SettingsScreen
import com.carpersona.app.ui.SimScreen
import com.carpersona.app.ui.TalkScreen
import com.carpersona.app.vehicle.CarSource
import com.carpersona.app.vehicle.FakeCar
import com.carpersona.app.vehicle.PleosCar
import com.carpersona.app.voice.AndroidVoice
import com.carpersona.app.voice.Chime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 앱의 시작점.
 *  1) Pleos SDK가 APK 안에 있고 차량 서비스에 연결되면 → 진짜 차 (PleosCar + PleosNavi)
 *  2) 아니면(일반 폰·일반 에뮬레이터) → 가짜 차 (FakeCar, 시뮬레이터 탭이 생김)
 *  진짜 차에 연결됐는데 6초 동안 값이 하나도 안 오면 자동으로 가짜 차로 바꿉니다.
 */
class MainActivity : ComponentActivity() {

    private class Setup(val car: CarSource, val navi: NaviSource, val companion: CarCompanion, val fake: FakeCar?)

    private lateinit var store: LifeStore
    private lateinit var voice: AndroidVoice
    private lateinit var ux: DrivingRestriction
    private lateinit var live: LiveTalk
    private val toast = mutableStateOf<String?>(null)
    private val currentTab = mutableStateOf("talk")
    private val hideToast = Runnable { toast.value = null }

    /** Pleos Toast: 아래쪽에 4초 동안 */
    private fun showToast(text: String) {
        toast.value = text
        handler.removeCallbacks(hideToast)
        handler.postDelayed(hideToast, 4000)
    }
    private val setup = mutableStateOf<Setup?>(null)
    private val status = mutableStateOf<String?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingMic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LifeStore(this)
        voice = AndroidVoice(this).also { it.enabled = store.voiceOn }
        ux = DrivingRestriction(this).also { it.start() }
        live = LiveTalk(this)
        if (!tryPleos()) useFake("Pleos 차량 서비스가 없어 가짜 차로 실행 중이에요.")

        setContent {
            PleosTheme {
                val s = setup.value ?: return@PleosTheme
                App(s)
            }
        }
    }

    private fun tryPleos(): Boolean {
        if (!PleosCar.sdkInApk()) return false
        val car = PleosCar(this)
        if (!car.connect()) return false
        val navi = PleosNavi(this)
        replace(Setup(car, navi, companionFor(car, navi), null))
        status.value = "Pleos 차량에 연결했어요. 값을 기다리는 중…"
        handler.postDelayed({
            if (setup.value?.car === car) {
                if (car.receivedAny.value) status.value = "Pleos 차량 값을 받고 있어요."
                else useFake("Pleos 차량에서 6초 동안 값이 오지 않아 가짜 차로 바꿨어요.")
            }
        }, 6000)
        return true
    }

    private fun useFake(reason: String) {
        val fake = FakeCar()
        replace(Setup(fake, fake, companionFor(fake, fake), fake))
        status.value = reason
    }

    private fun companionFor(car: CarSource, navi: NaviSource) =
        CarCompanion(
            car, navi, store,
            speak = { text, register ->
                // 대화 화면이 아닐 때 차가 먼저 말하면 아래쪽 토스트로도 보여 줌
                if (currentTab.value != "talk") showToast(text.replace("\n", " "))
                // 자유 대화(AI 음성) 중에는 목소리가 겹치지 않게, 안전 경고만 기본 목소리로 끼어듦
                if (!live.active) voice.speak(text, register)
                else if (text.contains(Dialogue.SAFETY_TAG)) {
                    stopLive()
                    voice.speak(text, register)
                }
            },
            chime = { if (store.voiceOn) Chime.play(it) },
            chimeMs = { if (store.voiceOn) Chime.durationMs(it) else 0L },
        )

    private fun replace(next: Setup) {
        setup.value?.let { old -> old.companion.stop(); old.car.stop(); old.navi.stop() }
        if (next.fake == null) AppClock.useRealClock()
        next.car.start(); next.navi.start(); next.companion.start()
        setup.value = next
    }

    /** 🎙 버튼: 마이크 권한 확인 → 듣기 → 질문 */
    private fun onMic(companion: CarCompanion) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMic = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (pendingLive) { pendingLive = false; startLive(companion); return }
        voice.listen(onText = { companion.ask(it) }, onError = { showToast(it) })
    }

    private var pendingLive = false

    /** 🗣 자유 대화 시작 (OpenAI Realtime). 마이크 권한이 없으면 먼저 물어봄 */
    private fun startLive(companion: CarCompanion) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingLive = true; pendingMic = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (store.life.value == null) return
        voice.stopSpeaking()
        live.onCarLine = { companion.chat += com.carpersona.app.ai.ChatLine(fromCar = true, text = it, byAi = true, badge = "AI 음성 · ${LiveTalk.MODEL}") }
        live.onUserLine = { companion.chat += com.carpersona.app.ai.ChatLine(fromCar = false, text = it) }
        live.start(store.openAiKey, LivePrompt.of(companion.context()))
    }

    private fun stopLive() = live.stop()

    /** 앱이 화면에서 사라지면 자유 대화를 끝냄 (마이크·요금 보호, Pleos 백그라운드 가이드) */
    override fun onStop() {
        if (live.active) live.stop("앱이 화면에서 사라져 자유 대화를 끝냈어요.")
        super.onStop()
    }

    @Deprecated("Activity API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (pendingMic && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            setup.value?.let { onMic(it.companion) }
        }
        pendingMic = false
        pendingLive = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        setup.value?.let { it.companion.stop(); it.car.stop(); it.navi.stop() }
        live.stop()
        voice.release()
        ux.stop()
        super.onDestroy()
    }

    @Composable
    private fun App(s: Setup) {
        var tab by currentTab
        // 차량·폰의 뒤로 가기: 아이콘으로 연 화면에서는 대화 화면으로
        BackHandler(enabled = tab != "talk") { tab = "talk" }
        val nowMillis by produceState(AppClock.now()) { while (true) { value = AppClock.now(); delay(1000) } }
        val life = store.life.value
        val car = s.car.snapshot.value

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            if (life == null) {
                Onboarding(car) { name, year, odo, consent, release ->
                    store.locationConsent = consent
                    store.save(PersonaEngine.birth(name, year, car.odometerKm ?: odo, AppClock.now(), release))
                    // 깨어나서 첫 인사 (자기소개)
                    val ctx = s.companion.context()
                    val first = Dialogue.compose(ctx, Intent.WHO)
                    s.companion.chat += com.carpersona.app.ai.ChatLine(fromCar = true, text = first)
                    voice.speak(first, ctx.life.register)
                }
                return@Box
            }
            // 주행 중 화면 규칙: 차가 알려 주는 주행 제한(CarUxRestrictions) 또는 주행 감지(시뮬레이터 포함)
            val drivingUi = ux.restricted.value || s.companion.driving.value
            val ctx = s.companion.context(life).copy(now = nowMillis, driving = drivingUi)
            val aiReady = store.aiOn && store.apiKey.isNotBlank()
            val demoBrain = remember(aiReady, store.apiKey) { if (aiReady) ClaudeBrain(store.apiKey) else RuleBrain }
            val items = buildList {
                add(NavItem("talk", "대화", Icons.Filled.Face))
                add(NavItem("growth", "성장 기록", Icons.AutoMirrored.Filled.List, lockedWhileDriving = true))
                add(NavItem("data", "차량 상태", Icons.Filled.Info))
                add(NavItem("demo", "시연", Icons.Filled.PlayArrow, lockedWhileDriving = true, suffix = true))
                if (s.fake != null) add(NavItem("sim", "시뮬레이터", Icons.Filled.Build, suffix = true))
                add(NavItem("settings", "설정", Icons.Filled.Settings, lockedWhileDriving = true, suffix = true))
            }
            val chips = buildList {
                add(StatusChip(if (drivingUi) "🚗 주행 중" else "🅿️ 정차 중", highlight = drivingUi))
                add(StatusChip("${ctx.mood.kind.emoji} ${ctx.mood.kind.word}"))
                car.energyPercent?.let { add(StatusChip("${if (car.isEv == true) "🔋" else "⛽"} ${it.toInt()}%")) }
                add(StatusChip("📍 ${ctx.province?.label ?: "위치 모름"} · ${ctx.dialect.label}"))
                add(StatusChip("🕒 ${SimpleDateFormat("HH:mm", Locale.KOREA).format(nowMillis)}"))
            }
            // 자유 대화 중에 성격·기분·지역(사투리)이 바뀌면 AI에게 다시 알려 줌
            // (초마다 변하는 주행 거리·시간으로는 다시 보내지 않고, 기분·경고·지역·주행 여부가 바뀔 때만)
            val liveKey = listOf(ctx.mood.kind, ctx.mood.facts, ctx.mood.notes, ctx.dialect, ctx.province, ctx.driving,
                ctx.life.register, ctx.destination?.name, car.energyPercent?.let { (it / 5).toInt() })
            LaunchedEffect(liveKey) { if (live.active) live.updateInstructions(LivePrompt.of(ctx)) }
            val liveUi = LiveUi(
                available = store.openAiKey.isNotBlank(),
                on = live.active,
                status = when (live.phase.value) {
                    LiveTalk.Phase.CONNECTING -> "연결 중…"
                    LiveTalk.Phase.SPEAKING -> "말하는 중"
                    else -> "듣고 있어요"
                },
                error = live.error.value,
                onStart = { startLive(s.companion) },
                onStop = { stopLive() },
            )
            PleosAppShell(
                title = life.nickname,
                subtitle = PersonaEngine.archetype(life.traits) + " · " + s.car.label,
                chips = chips, items = items, selected = tab, onSelect = { tab = it }, driving = drivingUi, toast = toast.value,
            ) { wide ->
                when (tab) {
                    "talk" -> TalkScreen(ctx, s.companion, voice.listening.value, aiReady, wide, liveUi) { onMic(s.companion) }
                    "growth" -> GrowthScreen(life, nowMillis, wide)
                    "demo" -> DemoScreen(demoBrain, voice, wide)
                    "data" -> CarDataScreen(ctx, s.car.label, s.car.isReal, status.value,
                        onUseFake = { useFake("직접 가짜 차로 바꿨어요.") },
                        onTryPleos = { if (!tryPleos()) status.value = "Pleos 차량 서비스에 연결할 수 없어요 (일반 폰/에뮬레이터)." })
                    "sim" -> s.fake?.let { SimScreen(it, car, s.companion, nowMillis) }
                    else -> SettingsScreen(store) { voice.enabled = it }
                }
            }
        }
    }
}
