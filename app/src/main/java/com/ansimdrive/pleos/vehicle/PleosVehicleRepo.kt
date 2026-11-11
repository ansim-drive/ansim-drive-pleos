package com.ansimdrive.pleos.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ansimdrive.pleos.TAG

/**
 * Pleos Vehicle SDK로 브레이크 페달·헤드라이트(로우빔) 상태를 받는다.
 * SDK 콜백 스레드가 문서화돼 있지 않아 메인 스레드로 넘겨서 전달한다.
 */
class PleosVehicleRepo(
    context: Context,
    private val onBrake: (pressed: Boolean) -> Unit,
    private val onHeadlight: (on: Boolean) -> Unit,
) {
    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            if (status != null) main.post { onBrake(status) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "Brake listener failed", e)
        }
    }

    private val lightListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            if (state != null) main.post { onHeadlight(state == LightState.ON) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "Light listener failed", e)
        }
    }

    fun start() {
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle SDK initialize failed", e)
            return
        }
        started = true
        val brake = vehicle.getBrake()
        val light = vehicle.getLight()

        // initialize()가 예외 없이 끝나도 서비스 연결이 보장되지 않으므로 실제 값을 한 번 읽어 확인한다.
        brake.getCurrentBrakePedalStatus(
            { pressed ->
                Log.i(TAG, "Vehicle SDK ok: brake pedal=$pressed")
                if (pressed != null) main.post { onBrake(pressed) }
            },
            { Log.w(TAG, "Vehicle SDK brake read failed", it) },
        )
        light.getCurrentLowBeamLightState(
            { state ->
                Log.i(TAG, "Vehicle SDK ok: low beam=$state")
                if (state != null) main.post { onHeadlight(state == LightState.ON) }
            },
            { Log.w(TAG, "Vehicle SDK low beam read failed (night falls back to clock)", it) },
        )
        brake.registerBrakePedalStatus(brakeListener)
        light.registerLowBeamLightState(lightListener)
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
            vehicle.getLight().unregisterLowBeamLightState(lightListener)
            vehicle.release()
        }.onFailure { Log.w(TAG, "Vehicle SDK release failed", it) }
    }
}
