package com.ansimdrive.pleos.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.ansimdrive.pleos.TAG

/**
 * AAOS 표준 Car API로 기어·속도를 받는다 (Pleos "표준 우선" 정책).
 * 콜백은 메인 스레드에서 호출된다.
 */
class CarPropertyRepo(
    private val context: Context,
    private val onGear: (gear: Int) -> Unit,
    private val onSpeed: (kmh: Float, timestampMs: Long) -> Unit,
) {
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> onGear(value.value as Int)
                VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                    onSpeed((value.value as Float) * 3.6f, value.timestamp / 1_000_000)
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "Car property error: ${VehiclePropertyIds.toString(propertyId)}")
        }
    }

    /** [withSpeed]가 false면 CAR_SPEED 권한이 없는 것 → 기어만 구독. */
    fun start(withSpeed: Boolean) {
        car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready ->
            if (!ready) {
                Log.w(TAG, "Car service disconnected")
                manager = null
                return@createCar
            }
            val mgr = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = mgr
            register(mgr, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            if (withSpeed) {
                register(mgr, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            } else {
                Log.w(TAG, "CAR_SPEED not granted: speed disabled")
            }
        }
    }

    private fun register(mgr: CarPropertyManager, propertyId: Int, rate: Float) {
        val name = VehiclePropertyIds.toString(propertyId)
        try {
            val ok = mgr.registerCallback(callback, propertyId, rate)
            Log.i(TAG, "Car API subscribe $name: $ok")
        } catch (e: Exception) {
            Log.e(TAG, "Car API subscribe $name failed", e)
        }
    }

    fun stop() {
        manager?.unregisterCallback(callback)
        manager = null
        car?.disconnect()
        car = null
    }
}
