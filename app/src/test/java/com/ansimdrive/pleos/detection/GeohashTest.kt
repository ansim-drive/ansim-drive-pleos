package com.ansimdrive.pleos.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashTest {
    @Test
    fun encodesKnownPoint() {
        // 위키백과 예시: 57.64911, 10.40744 → u4pruydqqvj
        assertEquals("u4pruyd", Geohash.encode(57.64911, 10.40744))
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
    }

    @Test
    fun nearbyPointsShareCellAndFarPointsDont() {
        val a = Geohash.encode(37.5665, 126.9780)
        assertEquals(a, Geohash.encode(37.56655, 126.97805))   // 약 7m
        assertTrue(a != Geohash.encode(37.5700, 126.9780))      // 약 390m
    }

    @Test
    fun distanceIsRoughlyRight() {
        val d = distanceM(37.5665, 126.9780, 37.5755, 126.9780) // 위도 0.009° ≈ 1km
        assertTrue(d in 990.0..1010.0)
    }
}
