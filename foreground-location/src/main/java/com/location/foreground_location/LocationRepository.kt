package com.location.foreground_location

import android.Manifest.permission
import android.location.Location
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationListenerCompat

class LocationRepository(
    private val locationManager: LocationManager?,
    private val updateListener: LocationChangeListener,
) : LocationListenerCompat {

    var lastLocation: Location? = null
        private set

    @RequiresPermission(anyOf = [permission.ACCESS_COARSE_LOCATION, permission.ACCESS_FINE_LOCATION])
    fun startLocationUpdates(interval: Long) {
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0f, this)
    }

    fun stopLocationUpdates() {
        locationManager?.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        updateListener.onChange(location)
        lastLocation = location
    }

    override fun onProviderDisabled(provider: String) {
        val error = when (provider) {
            LocationManager.GPS_PROVIDER -> ErrorState.GPS_OFF
            LocationManager.NETWORK_PROVIDER -> ErrorState.NETWORK_OFF
            else -> ErrorState.UNKNOWN
        }
        updateListener.onError(error)
    }
}

interface LocationChangeListener {
    fun onChange(location: Location)
    fun onError(errorState: ErrorState)
}
