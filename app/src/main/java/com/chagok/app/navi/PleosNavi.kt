package com.chagok.app.navi

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.chagok.app.trip.AppClock
import com.chagok.app.vehicle.PleosReflect

/**
 * Pleos NaviHelper SDK 연결부.
 *
 * 공식 문서에서 확인한 사용법:
 *   val naviHelper = NaviHelper(applicationContext)
 *   naviHelper.initialize(onConnected = { ... }, onFailed = { throwable -> ... })
 *   naviHelper.addListener(NaviHelperEventListener)   // onRouteStarted, onDestinationArrived, onDestinationInfo ...
 *   naviHelper.getDestinationInfo()                    // 결과는 onDestinationInfo(DestinationInfo) 로 옴
 *   naviHelper.requestRoute(RouteInfo(latitude = .., longitude = .., poiName = ..))
 *   naviHelper.removeListener(...) ; naviHelper.release()
 */
class PleosNavi(context: Context) : NaviSource {

    override val label = "Pleos 내비"
    private val appContext = context.applicationContext
    private val _state = mutableStateOf(NaviState())
    override val state: State<NaviState> = _state

    /** NaviHelper 연결 완료 여부 */
    val connected = mutableStateOf(false)

    private var helper: Any? = null
    private var listener: Any? = null

    companion object {
        private const val TAG = "Chagok/PleosNavi"
        private const val HELPER = "ai.pleos.playground.navi.helper.NaviHelper"
        private const val LISTENER = "ai.pleos.playground.navi.helper.listener.NaviHelperEventListener"
        private const val ROUTE_INFO = "ai.pleos.playground.navi.data.RouteInfo"

        fun sdkInApk(): Boolean = PleosReflect.classOrNull(HELPER) != null
    }

    override fun start() {
        val cls = PleosReflect.classOrNull(HELPER) ?: return
        try {
            val h = cls.java.getConstructor(Context::class.java).newInstance(appContext)
            helper = h
            listener = PleosReflect.listener(LISTENER, ::onEvent)
            // initialize(onConnected, onFailed) 가 있으면 그걸, 없으면 initialize()
            val init = PleosReflect.findFunction(h, "initialize")
            val withCallbacks = PleosReflect.functionsOf(h).firstOrNull { it.name == "initialize" && it.parameters.size >= 3 }
            if (withCallbacks != null) {
                PleosReflect.get(h, withCallbacks, null, onValue = { onConnected() }, onError = { Log.w(TAG, "연결 실패: $it") })
            } else if (init != null) {
                init.call(h); onConnected()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper 시작 실패: ${t.message}")
        }
    }

    private fun onConnected() {
        val h = helper ?: return
        connected.value = true
        runCatching { h.javaClass.methods.first { it.name == "addListener" }.invoke(h, listener) }
        askDestination()
    }

    /** 지금 목적지를 물어봄 → onDestinationInfo 로 답이 옴 */
    fun askDestination() {
        val h = helper ?: return
        runCatching { h.javaClass.methods.first { it.name == "getDestinationInfo" && it.parameterCount == 0 }.invoke(h) }
    }

    /** 내비가 알려 주는 일들 */
    private fun onEvent(name: String, args: List<Any?>) {
        Log.d(TAG, "event $name $args")
        when (name) {
            "onRouteStarted", "onDestinationInfo", "onDestinationChanged" -> {
                placeFrom(args.firstOrNull())?.let { _state.value = _state.value.copy(destination = it) }
                if (name != "onDestinationInfo") askDestination()
            }
            "onDestinationArrived" -> _state.value = _state.value.copy(arrivedAt = AppClock.now())
            "onRouteCancelled" -> _state.value = _state.value.copy(destination = null)
        }
    }

    private fun placeFrom(info: Any?): Place? {
        info ?: return null
        val name = PleosReflect.prop(info, "poiName", "name", "destinationName", "title")?.toString()
        val lat = PleosReflect.prop(info, "latitude", "lat")?.toString()?.toDoubleOrNull()
        val lon = PleosReflect.prop(info, "longitude", "lon", "lng")?.toString()?.toDoubleOrNull()
        if (name.isNullOrBlank() && lat == null) return null
        return Place(name?.takeIf { it.isNotBlank() } ?: "목적지", lat, lon)
    }

    override fun routeTo(place: Place): Boolean {
        val h = helper ?: return false
        if (place.lat == null || place.lon == null) return false
        return try {
            val fn = PleosReflect.functionsOf(h).firstOrNull { it.name == "requestRoute" } ?: return false
            // requestRoute 의 첫 매개변수 타입(RouteInfo)을 SDK에서 직접 알아냄
            val routeInfoClass = (fn.parameters.getOrNull(1)?.type?.classifier as? kotlin.reflect.KClass<*>)
                ?: PleosReflect.classOrNull(ROUTE_INFO) ?: return false
            val info = PleosReflect.construct(
                routeInfoClass,
                mapOf("latitude" to place.lat, "longitude" to place.lon, "poiName" to place.name)
            )
            val args = fn.parameters.associateWith { p ->
                when {
                    p.kind == kotlin.reflect.KParameter.Kind.INSTANCE -> h
                    p.index == 1 -> info
                    else -> null
                }
            }.filter { (p, v) -> v != null || !p.isOptional }
                .mapValues { (p, v) -> v ?: defaultEnum(p) }
            fn.callBy(args)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestRoute 실패: ${t.message}")
            false
        }
    }

    private fun defaultEnum(p: kotlin.reflect.KParameter): Any? =
        (p.type.classifier as? kotlin.reflect.KClass<*>)?.java?.enumConstants?.let { c ->
            c.firstOrNull { (it as Enum<*>).name == "RECOMMENDED" } ?: c.firstOrNull()
        }

    override fun stop() {
        val h = helper ?: return
        runCatching { h.javaClass.methods.first { it.name == "removeListener" }.invoke(h, listener) }
        runCatching { h.javaClass.getMethod("release").invoke(h) }
        helper = null
    }
}
