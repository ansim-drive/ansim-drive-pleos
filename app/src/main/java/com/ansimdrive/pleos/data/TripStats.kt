package com.ansimdrive.pleos.data

/** 이번 주행(P→D→P) 한 번의 값. 끝나면 [DriveLog.endTrip]으로 저장한다. */
class TripStats {
    var distanceKm = 0.0
    var nightMs = 0L
}
