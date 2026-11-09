package com.ansimdrive.pleos

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
import com.ansimdrive.pleos.data.TripStats
import com.ansimdrive.pleos.databinding.ActivityMainBinding
import com.ansimdrive.pleos.detection.DetectionConfig
import com.ansimdrive.pleos.ui.DrivingFragment
import com.ansimdrive.pleos.ui.ParkedFragment
import com.ansimdrive.pleos.vehicle.CarPropertyRepo
import com.ansimdrive.pleos.vehicle.HardBrakeDetector
import com.ansimdrive.pleos.vehicle.PleosVehicleRepo
import java.util.Calendar

/** 기어 상태에 따라 DrivingFragment / ParkedFragment를 전환한다. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var carRepo: CarPropertyRepo
    private lateinit var vehicleRepo: PleosVehicleRepo

    private val stats = TripStats()
    private val hardBrake = HardBrakeDetector()
    private var driving = false
    private var brakeOn = false
    private var headlightOn: Boolean? = null   // null = 정보 없음 → 시각으로 근사
    private var lastKmh = 0f
    private var lastSpeedMs = -1L

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

        if (savedInstanceState == null) show(ParkedFragment.newInstance(stats))

        val missing = RUNTIME_PERMISSIONS.filterNot(::granted)
        if (missing.isEmpty()) startSources() else permissionRequest.launch(missing.toTypedArray())
    }

    override fun onDestroy() {
        carRepo.stop()
        vehicleRepo.stop()
        super.onDestroy()
    }

    private fun startSources() {
        carRepo.start(withSpeed = granted(Car.PERMISSION_SPEED))
        vehicleRepo.start()
    }

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
    }

    private fun stopDriving() {
        driving = false
        Log.i(TAG, "Driving stopped: %.2f km, hard brakes=%d, night=%b"
            .format(stats.distanceKm, stats.hardBrakes, stats.nightDriving))
        show(ParkedFragment.newInstance(stats))
    }

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

    private fun show(fragment: Fragment) {
        supportFragmentManager.commit { replace(R.id.container, fragment) }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private val RUNTIME_PERMISSIONS = listOf(Car.PERMISSION_SPEED)
    }
}
