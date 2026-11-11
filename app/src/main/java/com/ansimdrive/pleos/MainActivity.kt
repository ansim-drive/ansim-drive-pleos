package com.ansimdrive.pleos

import android.Manifest
import android.car.Car
import android.car.VehicleGear
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.ansimdrive.pleos.data.AppSettings
import com.ansimdrive.pleos.data.TripStats
import com.ansimdrive.pleos.data.WeekTotals
import com.ansimdrive.pleos.data.WeeklyStatus
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
    private val stats = TripStats()
    private val hardBrake = HardBrakeDetector()
    private val wandering = WanderingDetector()
    private var driving = false
    private var brakeOn = false
    private var headlightOn: Boolean? = null   // null = 정보 없음 → 시각으로 근사
    private var lastKmh = 0f
    private var lastSpeedMs = -1L
    private var lastFix: Pair<Double, Double>? = null
    private var gpsKm = 0.0                    // 속도 정보가 없을 때(데모) 주행거리 대체
    private var asking = false                 // 음성·버튼 대답이 겹쳐도 한 번만 처리

    private val permissionRequest = registerForActivityResult(RequestMultiplePermissions()) { result ->
        Log.i(TAG, "Permissions: $result")
        startSources()
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
        settings = AppSettings(this)

        if (savedInstanceState == null) showSummary()

        // 권한 다이얼로그는 주행 중(UX 제한)에는 차단되므로 정차 중 첫 실행에서 받는다.
        val missing = RUNTIME_PERMISSIONS.filterNot(::granted)
        if (missing.isEmpty()) startSources() else permissionRequest.launch(missing.toTypedArray())

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
            VehicleGear.GEAR_PARK -> if (driving) stopDriving()
        }
    }

    private fun startDriving() {
        driving = true
        Log.i(TAG, "Driving started")
        show(DrivingFragment())
        updateNight()
        wandering.reset()
        lastFix = null
        location.start(::onLocation)
    }

    private fun stopDriving() {
        driving = false
        asking = false
        location.stop()
        voice.cancel()
        if (stats.distanceKm == 0.0) stats.distanceKm = gpsKm
        Log.i(TAG, "Driving stopped: %.2f km, hard brakes=%d, night=%b"
            .format(stats.distanceKm, stats.hardBrakes, stats.nightDriving))
        showSummary()
    }

    /** 1차: 오늘 기록만 메모리에 있어 전주 비교가 없으므로 이번 주 상태는 맑음. */
    private fun showSummary() {
        show(ParkedFragment.newInstance(
            distanceKm = stats.distanceKm,
            hardBrakes = stats.hardBrakes,
            nightDriving = stats.nightDriving,
            week = WeeklyStatus.of(WeekTotals(stats.hardBrakes, stats.wanderings, nightMinutes = 0), lastWeek = null),
            familySharing = settings.familySharing,
        ))
    }

    // ---- 헤맴 감지 → 음성 질문 → 집 안내 ----

    private fun onLocation(lat: Double, lon: Double, timeMs: Long) {
        lastFix?.let { gpsKm += distanceM(it.first, it.second, lat, lon) / 1000 }
        if (lastFix == null) Log.i(TAG, "First fix from ${location.name}: $lat, $lon")
        lastFix = lat to lon

        wandering.home = navi.home?.let { it.latitude to it.longitude }
        wandering.routeActive = navi.routeActive
        val reason = wandering.onLocation(lat, lon, timeMs) ?: return
        Log.i(TAG, "Wandering detected: $reason at $lat, $lon")
        stats.wanderings++
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
            stats.distanceKm += (lastKmh + kmh) / 2 * dtMs / 3_600_000.0
        }
        lastKmh = kmh
        lastSpeedMs = timeMs

        if (driving && hardBrake.onSpeed(kmh, timeMs, brakeOn)) {
            stats.hardBrakes++
            Log.i(TAG, "Hard brake #${stats.hardBrakes} (now %.0f km/h)".format(kmh))
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

    private fun updateNight() {
        if (!driving || stats.nightDriving) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = headlightOn
            ?: (hour >= DetectionConfig.NIGHT_START_HOUR || hour < DetectionConfig.NIGHT_END_HOUR)
        if (night) {
            stats.nightDriving = true
            Log.i(TAG, "Night driving (source=${if (headlightOn != null) "headlight" else "clock"})")
        }
    }

    // ---- 기타 ----

    private fun drivingFragment() = supportFragmentManager.findFragmentById(R.id.container) as? DrivingFragment

    /** 내비가 앞에 있을 때(onStop 이후)도 기어 이벤트가 오므로 상태 손실을 허용한다. 요약 값은 arguments에 있다. */
    private fun show(fragment: Fragment) {
        supportFragmentManager.commit(allowStateLoss = true) { replace(R.id.container, fragment) }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private val RUNTIME_PERMISSIONS = listOf(
            Car.PERMISSION_SPEED,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
