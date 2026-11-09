package com.ansimdrive.pleos.navi

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ansimdrive.pleos.BuildConfig
import com.ansimdrive.pleos.TAG

/**
 * Pleos NaviHelper: 집 좌표(HOME 북마크), 현재 위치, 경로 안내 상태, 경로 요청.
 * 조회 API는 결과가 리스너로 오므로 상태를 필드에 들고 있다. 콜백은 메인 스레드로 넘긴다.
 */
class NaviHelperRepo(context: Context) : NaviHelperEventListener {

    private val helper = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false

    var home: BookmarkInfo? = null
        private set
    var routeActive = false
        private set
    var onLocation: ((lat: Double, lon: Double) -> Unit)? = null

    fun start() {
        try {
            helper.initialize()
            helper.addListener(this)
            ready = true
        } catch (e: Exception) {
            Log.e(TAG, "NaviHelper initialize failed", e)
            return
        }
        // 연결 확인 겸 초기 상태 조회
        call("getBookmarkInfo") { helper.getBookmarkInfo() }
        call("getRouteStateInfo") { helper.getRouteStateInfo() }
    }

    /** 결과는 [onLocation]으로 온다. */
    fun requestLocation(): Boolean = ready && call("getCurrentLocationInfo") { helper.getCurrentLocationInfo() }

    fun routeHome(): Boolean {
        val h = home
        val route = when {
            h != null -> RouteInfo(
                latitude = h.latitude,
                longitude = h.longitude,
                poiId = h.poiId.orEmpty(),
                poiName = h.name?.ifEmpty { null } ?: "집",
                poiSubId = h.poiSubId ?: "0",
                address = h.address.orEmpty(),
            )
            BuildConfig.DEMO_MODE -> DEMO_HOME.also { Log.i(TAG, "No HOME bookmark: using demo home") }
            else -> {
                Log.w(TAG, "No HOME bookmark: cannot route home")
                return false
            }
        }
        Log.i(TAG, "requestRoute home (${route.latitude}, ${route.longitude})")
        return ready && call("requestRoute") { helper.requestRoute(route) }
    }

    fun stop() {
        if (!ready) return
        ready = false
        runCatching {
            helper.removeListener(this)
            helper.release()
        }.onFailure { Log.w(TAG, "NaviHelper release failed", it) }
    }

    private inline fun call(name: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: Exception) {
        Log.w(TAG, "NaviHelper $name failed", e)
        false
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
        main.post { onLocation?.invoke(currentLocationInfo.latitude, currentLocationInfo.longitude) }
    }

    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
        // 등록되지 않은 HOME은 (0, 0)으로 온다.
        val h = bookmarkInfoList.firstOrNull {
            it.category == BookmarkType.HOME && (it.latitude != 0.0 || it.longitude != 0.0)
        }
        main.post {
            home = h
            Log.i(TAG, "NaviHelper ok: ${bookmarkInfoList.size} bookmarks, home=${h?.let { "${it.latitude},${it.longitude}" }}")
        }
    }

    override fun onBookMarkChanged() {
        call("getBookmarkInfo") { helper.getBookmarkInfo() }
    }

    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
        // isRouting은 안내 중에도 false로 온다(에뮬레이터 확인) → naviMode로 판단.
        val guiding = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING ||
            routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
        main.post {
            routeActive = guiding
            Log.i(TAG, "Route state: mode=${routeStateInfo.naviMode}, active=$guiding")
        }
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        main.post {
            routeActive = status == NavigationStatus.ROUTE_DRIVE
            Log.i(TAG, "Navigation status: $status")
        }
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        Log.i(TAG, "Route started: $routeStartInfo")
    }

    override fun onError(errorCode: NaviErrorCode) {
        Log.w(TAG, "NaviHelper error: $errorCode")
    }

    companion object {
        /** 데모 빌드에서 HOME 북마크가 없을 때 쓰는 가상의 집 (데모 경로·에뮬레이터 지도 위치에서 약 2km). */
        private val DEMO_HOME = RouteInfo(
            latitude = 37.4000,
            longitude = 127.1080,
            poiId = "",
            poiName = "집",
            poiSubId = "0",
            address = "",
        )
    }
}
