package com.ansimdrive.pleos.detection

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 외부 라이브러리 없는 geohash 인코더. precision 7 ≈ 150m 격자. */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lon: Double, precision: Int = DetectionConfig.GEOHASH_PRECISION): String {
        var latLo = -90.0; var latHi = 90.0
        var lonLo = -180.0; var lonHi = 180.0
        val out = StringBuilder(precision)
        var lonBit = true
        var bits = 0
        var ch = 0
        while (out.length < precision) {
            if (lonBit) {
                val mid = (lonLo + lonHi) / 2
                if (lon >= mid) { ch = ch shl 1 or 1; lonLo = mid } else { ch = ch shl 1; lonHi = mid }
            } else {
                val mid = (latLo + latHi) / 2
                if (lat >= mid) { ch = ch shl 1 or 1; latLo = mid } else { ch = ch shl 1; latHi = mid }
            }
            lonBit = !lonBit
            if (++bits == 5) { out.append(BASE32[ch]); bits = 0; ch = 0 }
        }
        return out.toString()
    }
}

/** 두 좌표 사이 거리(m), haversine. */
fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
    return 2 * 6_371_000.0 * asin(sqrt(a))
}
