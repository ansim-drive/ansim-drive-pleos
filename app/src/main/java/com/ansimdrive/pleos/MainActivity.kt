package com.ansimdrive.pleos

import android.Manifest
import android.car.Car
import android.car.VehicleGear
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.ansimdrive.pleos.data.AppSettings
import com.ansimdrive.pleos.data.DriveLog
import com.ansimdrive.pleos.data.TripStats
import com.ansimdrive.pleos.databinding.ActivityMainBinding
import com.ansimdrive.pleos.detection.DetectionConfig
import com.ansimdrive.pleos.detection.WanderingDetector
import com.ansimdrive.pleos.detection.distanceM
import com.ansimdrive.pleos.location.AutoLocationSource
import com.ansimdrive.pleos.location.DemoLocationSource
import com.ansimdrive.pleos.location.LocationSource
import com.ansimdrive.pleos.navi.NaviHelperRepo
import com.ansimdrive.pleos.ui.DrivingFragment
import com.ansimdrive.pleos.ui.ParkedFragment
import com.ansimdrive.pleos.vehicle.CarPropertyRepo
import com.ansimdrive.pleos.vehicle.HardBrakeDetector
import com.ansimdrive.pleos.vehicle.PleosVehicleRepo
import com.ansimdrive.pleos.voice.VoiceAssistant
import com.ansimdrive.pleos.voice.MicCheck
import com.ansimdrive.pleos.voice.VoiceAssistant.Answer
import com.ansimdrive.pleos.voice.VoiceLine
import com.ansimdrive.pleos.voice.VoiceSettings
import com.ansimdrive.pleos.voice.VoskSpeechInput
import kotlinx.coroutines.launch
import java.util.Calendar

/** 기어 상태에 따라 DrivingFragment / ParkedFragment를 전환하고, 주행 중 헤맴을 감지한다. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var carRepo: CarPropertyRepo
    private lateinit var vehicleRepo: PleosVehicleRepo
    private lateinit var navi: NaviHelperRepo
    private lateinit var location: LocationSource
    private lateinit var voice: VoiceAssistant

    private lateinit var settings: AppSettings
    private lateinit var driveLog: DriveLog
    private var trip = TripStats()
    private var nightSinceMs: Long? = null       // 지금 야간 주행 중이면 시작 시각
    private val hardBrake = HardBrakeDetector()
    private val wandering = WanderingDetector()
    private var driving = false
    private var brakeOn = false
    private var headlightOn: Boolean? = null   // null = 정보 없음 → 시각으로 근사
    private var lastKmh = 0f
    private var lastSpeedMs = -1L
    private var lastFix: Pair<Double, Double>? = null
    private var gpsKm = 0.0                    // 속도 정보가 없을 때(데모) 이번 주행 거리 대체
    private var asking = false                 // 음성·버튼 대답이 겹쳐도 한 번만 처리
    private var parkedBeforeDrive = false      // P→D 전환일 때만 출발 전 한마디

    private val permissionRequest = registerForActivityResult(RequestMultiplePermissions()) { result ->
        Log.i(TAG, "Permissions: $result")
        restartInNewTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(TAG, "MainActivity created (demoMode=${BuildConfig.DEMO_MODE})")

        carRepo = CarPropertyRepo(this, ::onGear, ::onSpeed)
        vehicleRepo = PleosVehicleRepo(this, ::onBrake, ::onHeadlight)
        navi = NaviHelperRepo(this)
        location = if (BuildConfig.DEMO_MODE) DemoLocationSource(this) else AutoLocationSource(this, navi)
        voice = VoiceAssistant(this, VoiceSettings(this))
        voice.onTextOnly = ::showCaption
        settings = AppSettings(this)
        driveLog = DriveLog(this)
        // 전주 비교 확인용(디버그 빌드만): --ez debug_seed_last_week true
        if (BuildConfig.DEBUG && intent.getBooleanExtra("debug_seed_last_week", false)) driveLog.seedEmptyLastWeek()

        if (savedInstanceState == null) showSummary()

        // 권한 다이얼로그는 주행 중(UX 제한)에는 차단되므로 정차 중 첫 실행에서 받는다.
        val missing = RUNTIME_PERMISSIONS.filterNot(::granted)
        val restarted = intent.getBooleanExtra(EXTRA_AFTER_PERMISSIONS, false)
        if (missing.isEmpty() || restarted) startSources() else permissionRequest.launch(missing.toTypedArray())

        // 음성 테스트용(디버그 빌드만): adb shell am start -S -n com.ansimdrive.pleos/.MainActivity --ez debug_ask true
        if (BuildConfig.DEBUG && intent.hasExtra("debug_vosk_grammar")) {
            VoskSpeechInput.useGrammar = intent.getBooleanExtra("debug_vosk_grammar", true)
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("debug_ask", false)) {
            binding.root.postDelayed(::askRouteHome, 4_000)   // 음성 인식 모델 로딩(약 2.5초) 뒤
        }
        // 마이크 점검(디버그 빌드만): --ei debug_mic <3초 구간 수>
        if (BuildConfig.DEBUG && intent.getIntExtra("debug_mic", 0) > 0) {
            MicCheck.run(this, intent.getIntExtra("debug_mic", 0))
        }
    }

    override fun onDestroy() {
        location.stop()
        carRepo.stop()
        vehicleRepo.stop()
        navi.stop()
        voice.release()
        super.onDestroy()
    }

    /**
     * 권한 창을 띄웠던 실행(태스크)은 Pleos 홈 화면이 주행 중에 "사용할 수 없습니다"로 막는다(에뮬레이터 확인).
     * 새 태스크로 다시 시작하면 정상이므로 권한 결과를 받으면 바로 다시 시작한다. 다시 시작한 실행은 권한을 묻지 않는다.
     */
    private fun restartInNewTask() {
        Log.i(TAG, "Restarting in a new task after the permission dialog")
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_AFTER_PERMISSIONS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        )
        finishAndRemoveTask()
    }

    private fun startSources() {
        carRepo.start(withSpeed = granted(Car.PERMISSION_SPEED))
        vehicleRepo.start()
        navi.start()
    }

    // ---- 기어 → 화면 전환 ----

    private fun onGear(gear: Int) {
        Log.i(TAG, "Gear: ${VehicleGear.toString(gear)}")
        when (gear) {
            VehicleGear.GEAR_DRIVE -> if (!driving) startDriving()
            VehicleGear.GEAR_PARK -> {
                parkedBeforeDrive = true
                if (driving) stopDriving()
            }
        }
    }

    private fun startDriving() {
        driving = true
        trip = TripStats()
        gpsKm = 0.0
        nightSinceMs = null
        Log.i(TAG, "Driving started")
        show(DrivingFragment())
        updateNight()
        if (parkedBeforeDrive) sayDepartureRemark()
        parkedBeforeDrive = false
        wandering.reset()
        lastFix = null
        location.start(::onLocation)
    }

    private fun stopDriving() {
        driving = false
        asking = false
        location.stop()
        voice.cancel()
        nightSinceMs?.let { trip.nightMs += SystemClock.elapsedRealtime() - it }
        nightSinceMs = null
        val km = if (trip.distanceKm > 0) trip.distanceKm else gpsKm
        val nightMinutes = ((trip.nightMs + 59_999) / 60_000).toInt()   // 조금이라도 있으면 1분
        Log.i(TAG, "Driving stopped: %.2f km, night=%d min".format(km, nightMinutes))
        driveLog.endTrip(km, nightMinutes)
        showSummary()
    }

    /** DB에서 오늘 합계와 이번 주 상태를 읽어 요약 화면을 띄운다. */
    private fun showSummary() {
        lifecycleScope.launch {
            val s = driveLog.summary()
            if (driving) return@launch
            show(ParkedFragment.newInstance(
                distanceKm = s.distanceKm,
                hardBrakes = s.hardBrakes,
                nightDriving = s.nightDriving,
                week = s.week,
                familySharing = settings.familySharing,
            ))
        }
    }

    /** 출발 전 한마디: 어둡거나(헤드라이트 ON) 비가 오면(더미 날씨) 한 번 말한다. 둘 다 아니면 조용히. */
    private fun sayDepartureRemark() {
        val dark = headlightOn == true
        val rain = BuildConfig.WEATHER == "rain"
        val line = when {
            dark && rain -> VoiceLine.DEPART_DARK_RAIN
            dark -> VoiceLine.DEPART_DARK
            rain -> VoiceLine.DEPART_RAIN
            else -> null
        }
        Log.i(TAG, "Departure remark: dark=$dark, rain=$rain -> ${line ?: "silent"}")
        line?.let { voice.say(it) }
    }

    // ---- 헤맴 감지 → 음성 질문 → 집 안내 ----

    private fun onLocation(lat: Double, lon: Double, timeMs: Long) {
        lastFix?.let { gpsKm += distanceM(it.first, it.second, lat, lon) / 1000 }
        if (lastFix == null) Log.i(TAG, "First fix from ${location.name}: $lat, $lon")
        lastFix = lat to lon

        wandering.home = navi.home?.let { it.latitude to it.longitude }
        wandering.routeActive = navi.routeActive
        drivingFragment()?.setDetectionActive(!wandering.suppressedAt(lat, lon))
        val reason = wandering.onLocation(lat, lon, timeMs) ?: return
        Log.i(TAG, "Wandering detected: $reason at $lat, $lon")
        driveLog.wandering()
        askRouteHome()
    }

    private fun askRouteHome() {
        asking = true
        drivingFragment()?.showQuestion(
            getString(VoiceLine.ASK_ROUTE_HOME.text),
            onYes = { onAnswer(true) },
            onNo = { onAnswer(false) },
        )
        voice.ask(VoiceLine.ASK_ROUTE_HOME) { answer ->
            when (answer) {
                Answer.YES -> onAnswer(true)
                Answer.NO -> onAnswer(false)
                Answer.UNKNOWN -> Log.i(TAG, "No voice answer: waiting for button")
            }
        }
    }

    private fun onAnswer(yes: Boolean) {
        if (!asking) return
        asking = false
        Log.i(TAG, "Answer: ${if (yes) "yes" else "no"}")
        drivingFragment()?.clearQuestion()
        val reply = when {
            !yes -> VoiceLine.DECLINED
            navi.routeHome() -> VoiceLine.ROUTING_HOME
            else -> VoiceLine.NO_HOME
        }
        voice.say(reply)
    }

    // ---- 기록: 주행거리·급제동·야간 ----

    private fun onSpeed(kmh: Float, timeMs: Long) {
        if (driving && lastSpeedMs >= 0) {
            val dtMs = (timeMs - lastSpeedMs).coerceIn(0, 5_000)
            trip.distanceKm += (lastKmh + kmh) / 2 * dtMs / 3_600_000.0
        }
        lastKmh = kmh
        lastSpeedMs = timeMs

        if (driving && hardBrake.onSpeed(kmh, timeMs, brakeOn)) {
            Log.i(TAG, "Hard brake (now %.0f km/h)".format(kmh))
            driveLog.hardBrake(kmh)
        }
    }

    private fun onBrake(pressed: Boolean) {
        if (pressed != brakeOn) Log.d(TAG, "Brake pedal: $pressed")
        brakeOn = pressed
    }

    private fun onHeadlight(on: Boolean) {
        Log.i(TAG, "Headlight (low beam): $on")
        headlightOn = on
        updateNight()
    }

    /** 야간 주행 시간을 잰다: 헤드라이트 ON 구간, 정보가 없으면 시각(일몰~일출 근사). */
    private fun updateNight() {
        if (!driving) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = headlightOn
            ?: (hour >= DetectionConfig.NIGHT_START_HOUR || hour < DetectionConfig.NIGHT_END_HOUR)
        val now = SystemClock.elapsedRealtime()
        val since = nightSinceMs
        if (night && since == null) {
            nightSinceMs = now
            Log.i(TAG, "Night driving started (source=${if (headlightOn != null) "headlight" else "clock"})")
        } else if (!night && since != null) {
            trip.nightMs += now - since
            nightSinceMs = null
            Log.i(TAG, "Night driving paused")
        }
    }

    // ---- 기타 ----

    /** 소리를 못 낼 때 같은 문장을 주행 화면에 자막으로. 질문은 질문 화면에 이미 보이므로 뺀다. */
    private fun showCaption(line: VoiceLine) {
        if (line == VoiceLine.ASK_ROUTE_HOME) return
        supportFragmentManager.executePendingTransactions()   // 출발 직후엔 주행 화면이 아직 붙기 전일 수 있다
        drivingFragment()?.showCaption(getString(line.text))
    }

    private fun drivingFragment() = supportFragmentManager.findFragmentById(R.id.container) as? DrivingFragment

    /** 내비가 앞에 있을 때(onStop 이후)도 기어 이벤트가 오므로 상태 손실을 허용한다. 요약 값은 arguments에 있다. */
    private fun show(fragment: Fragment) {
        supportFragmentManager.commit(allowStateLoss = true) { replace(R.id.container, fragment) }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val EXTRA_AFTER_PERMISSIONS = "after_permissions"
        private val RUNTIME_PERMISSIONS = listOf(
            Car.PERMISSION_SPEED,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,   // Android 12+: FINE은 COARSE와 함께 요청
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
