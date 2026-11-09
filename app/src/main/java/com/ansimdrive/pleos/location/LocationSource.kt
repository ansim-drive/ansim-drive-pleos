package com.ansimdrive.pleos.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.ansimdrive.pleos.TAG
import com.ansimdrive.pleos.navi.NaviHelperRepo
import org.json.JSONArray

/** 위치 콜백. 시각은 [SystemClock.elapsedRealtime] 기준(ms). 메인 스레드에서 호출된다. */
typealias OnLocation = (lat: Double, lon: Double, timeMs: Long) -> Unit

interface LocationSource {
    val name: String
    fun start(onLocation: OnLocation)
    fun stop()
}

/** ① NaviHelper `getCurrentLocationInfo`를 1초마다 요청. */
class NaviLocationSource(private val navi: NaviHelperRepo) : LocationSource {
    override val name = "NaviHelper"
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            navi.requestLocation()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun start(onLocation: OnLocation) {
        navi.onLocation = { lat, lon -> onLocation(lat, lon, SystemClock.elapsedRealtime()) }
        handler.post(poll)
    }

    override fun stop() {
        handler.removeCallbacks(poll)
        navi.onLocation = null
    }
}

/** ② Android LocationManager GPS (에뮬레이터 Extended Controls 위치). */
class AndroidLocationSource(context: Context) : LocationSource {
    override val name = "LocationManager(GPS)"
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission") // MainActivity에서 ACCESS_FINE_LOCATION 요청 후 사용
    override fun start(onLocation: OnLocation) {
        val l = LocationListener { onLocation(it.latitude, it.longitude, SystemClock.elapsedRealtime()) }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, l, Looper.getMainLooper())
            listener = l
        } catch (e: SecurityException) {
            Log.e(TAG, "GPS permission missing", e)
        }
    }

    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }
}

/** ③ 데모: assets/demo_route.json의 [[lat, lon], ...]을 1초 간격으로 재생. */
class DemoLocationSource(private val context: Context) : LocationSource {
    override val name = "Demo(assets/demo_route.json)"
    private val handler = Handler(Looper.getMainLooper())

    override fun start(onLocation: OnLocation) {
        val json = context.assets.open("demo_route.json").bufferedReader().use { it.readText() }
        val points = JSONArray(json).let { arr ->
            List(arr.length()) { arr.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
        }
        Log.i(TAG, "Demo route: ${points.size} points")
        points.forEachIndexed { i, (lat, lon) ->
            handler.postDelayed({ onLocation(lat, lon, SystemClock.elapsedRealtime()) }, i * 1_000L)
        }
    }

    override fun stop() = handler.removeCallbacksAndMessages(null)
}

/**
 * 우선순위: ① NaviHelper → 5초 안에 응답이 없으면 ② LocationManager.
 * 데모 모드(BuildConfig.DEMO_MODE)는 MainActivity에서 [DemoLocationSource]를 직접 쓴다.
 */
class AutoLocationSource(context: Context, navi: NaviHelperRepo) : LocationSource {
    private val primary = NaviLocationSource(navi)
    private val fallback = AndroidLocationSource(context)
    private val handler = Handler(Looper.getMainLooper())
    private var active: LocationSource? = null
    override val name get() = active?.name ?: "none"

    override fun start(onLocation: OnLocation) {
        var gotFix = false
        use(primary) { lat, lon, t -> gotFix = true; onLocation(lat, lon, t) }
        handler.postDelayed({
            if (!gotFix) {
                Log.w(TAG, "No location from ${primary.name} in 5s, falling back")
                primary.stop()
                use(fallback, onLocation)
            }
        }, 5_000)
    }

    private fun use(source: LocationSource, onLocation: OnLocation) {
        active = source
        Log.i(TAG, "Location source: ${source.name}")
        source.start(onLocation)
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        active?.stop()
        active = null
    }
}
