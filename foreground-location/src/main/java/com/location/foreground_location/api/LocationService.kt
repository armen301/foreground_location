package com.location.foreground_location.api

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import com.location.foreground_location.api.LocationService.Companion.init
import com.location.foreground_location.internal.ErrorState
import com.location.foreground_location.internal.ForegroundLocationService

/**
 * This class is used to create a foreground service to fetch location when the app is in background. The service
 * creates notification based on [notificationConfig].
 * To create instance of [LocationService] need call [init] function in the activity's [Activity.onCreate].
 * To start or stop listening location updates need to call [start] and [stop] accordingly. When service is running
 * and for some reason service triggers error via [LocationErrorListener] than the service is stops and exits from the
 * foreground state.
 */
class LocationService private constructor(
    private val activity: Activity,
    private val locationUpdateConfig: LocationUpdateConfig,
    private val notificationConfig: NotificationConfig,
    private var locationUpdateListener: LocationUpdateListener?,
    private var errorListener: LocationErrorListener?,
) {

    private var foregroundLocationService: ForegroundLocationService? = null

    // Monitors connection of the service.
    private val foregroundServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ForegroundLocationService.LocalBinder
            foregroundLocationService = binder.service.apply {
                locationUpdateListener = this@LocationService.locationUpdateListener
                errorListener = this@LocationService.errorListener
                notificationIcon = notificationConfig.iconResource
                notificationContentTitle = notificationConfig.contentTitle
                notificationContentText = notificationConfig.contentText
                updateInterval = locationUpdateConfig.updateInterval
            }
            if (ActivityCompat.checkSelfPermission(
                    binder.service.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    binder.service.applicationContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                errorListener?.onError(ErrorState.NO_LOCATION_PERMISSION.ordinal)
                return
            }
            foregroundLocationService?.startLocationUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            foregroundLocationService = null
        }
    }

    /**
     * This function binds the foreground service to the process and starting listen to location updates.
     */
    fun start() {
        val serviceIntent = Intent(activity, ForegroundLocationService::class.java)
        activity.bindService(serviceIntent, foregroundServiceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * This function unbinds the foreground service from the process and unsubscribe from the location updates.
     */
    fun stop() {
        foregroundLocationService?.let {
            it.stopLocationUpdates()
            activity.unbindService(foregroundServiceConnection)
        }
    }

    companion object {
        /**
         * Creates instance of [LocationService]. Should be called in the [Activity.onCreate]
         *
         * @param activity the activity which is hold the instance of [LocationService]
         * @param locationUpdateConfig config object for requesting location updates
         * @param notificationConfig config object for notifications
         * @param locationUpdateListener listener to subscribe on location updates
         * @param errorListener listener to subscribe on errors related to location
         */
        @JvmStatic
        fun init(
            activity: Activity,
            locationUpdateConfig: LocationUpdateConfig,
            notificationConfig: NotificationConfig,
            locationUpdateListener: LocationUpdateListener,
            errorListener: LocationErrorListener
        ): LocationService {
            return LocationService(
                activity,
                locationUpdateConfig,
                notificationConfig,
                locationUpdateListener,
                errorListener
            )
        }
    }
}
