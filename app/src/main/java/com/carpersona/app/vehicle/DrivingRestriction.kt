package com.carpersona.app.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.lang.reflect.Proxy

/**
 * Pleos 주행 제한 (AAOS CarUxRestrictions).
 * Pleos 가이드: "주행 중 방해 최소화(DO)를 선언한 앱은 CarUxRestrictions 를 확인해 기능별 제한을 직접 적용해야 한다."
 *  - CarUxRestrictionsManager.registerListener → onUxRestrictionsChanged 로 받음
 *  - isRequiresDistractionOptimization() 이 true 면 주행 중 화면 규칙을 적용
 *
 * android.car 라이브러리는 차(Pleos Connect)에만 있어서, 일반 폰에서도 앱이 죽지 않게 리플렉션으로 부릅니다.
 * 폰에서는 [available] = false 이고 [restricted] 는 늘 false.
 */
class DrivingRestriction(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var car: Any? = null
    private var manager: Any? = null
    private var listener: Any? = null

    private val _restricted = mutableStateOf(false)
    /** 지금 주행 제한이 걸려 있는지 (true 면 키보드·긴 글·설정 화면을 숨김) */
    val restricted: State<Boolean> = _restricted
    private val _noKeyboard = mutableStateOf(false)
    /** 키보드 입력 금지 비트(UX_RESTRICTIONS_NO_KEYBOARD)가 켜져 있는지 */
    val noKeyboard: State<Boolean> = _noKeyboard
    var available = false
        private set

    fun start() {
        try {
            val carCls = Class.forName("android.car.Car")
            val c = carCls.getMethod("createCar", Context::class.java).invoke(null, appContext) ?: return
            val service = carCls.getField("CAR_UX_RESTRICTION_SERVICE").get(null) as String
            val mgr = carCls.getMethod("getCarManager", String::class.java).invoke(c, service) ?: return
            val lCls = Class.forName("android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener")
            val l = Proxy.newProxyInstance(lCls.classLoader, arrayOf(lCls)) { proxy, m, args ->
                when (m.name) {
                    "onUxRestrictionsChanged" -> { val r = args?.firstOrNull(); main.post { update(r) }; null }
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "CarPersonaUxListener"
                    else -> null
                }
            }
            mgr.javaClass.getMethod("registerListener", lCls).invoke(mgr, l)
            update(mgr.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(mgr))
            car = c; manager = mgr; listener = l; available = true
            Log.i(TAG, "CarUxRestrictions 연결됨")
        } catch (t: Throwable) {
            available = false
            Log.i(TAG, "android.car 없음 (일반 폰): ${t.javaClass.simpleName}")
        }
    }

    private fun update(r: Any?) {
        r ?: return
        runCatching {
            val required = r.javaClass.getMethod("isRequiresDistractionOptimization").invoke(r) as Boolean
            val active = r.javaClass.getMethod("getActiveRestrictions").invoke(r) as Int
            val noKb = runCatching {
                Class.forName("android.car.drivingstate.CarUxRestrictions").getField("UX_RESTRICTIONS_NO_KEYBOARD").getInt(null)
            }.getOrDefault(8)
            _restricted.value = required
            _noKeyboard.value = required && (active and noKb) != 0
        }.onFailure { Log.w(TAG, "제한 상태 읽기 실패", it) }
    }

    fun stop() {
        runCatching { manager?.javaClass?.getMethod("unregisterListener")?.invoke(manager) }
        runCatching { car?.javaClass?.getMethod("disconnect")?.invoke(car) }
        car = null; manager = null; listener = null
    }

    private companion object { const val TAG = "CarPersona/UxRestrict" }
}
