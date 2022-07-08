package com.location.foreground_location.internal

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.location.foreground_location.api.Location
import com.location.foreground_location.api.LocationErrorListener
import com.location.foreground_location.api.LocationUpdateListener
import kotlin.properties.Delegates

class ForegroundLocationService : Service() {

    private val tag = ForegroundLocationService::class.simpleName

    private var locationRepository: LocationRepository? = null
    private var configurationChange = false
    private val localBinder = LocalBinder(this)
    private var isForeground = false

    var updateInterval by Delegates.notNull<Long>()

    var locationUpdateListener: LocationUpdateListener? = null
    var errorListener: LocationErrorListener? = null

    var notificationContentTitle: String? = null
    var notificationContentText: String? = null
    var notificationIcon by Delegates.notNull<Int>()

    override fun onBind(intent: Intent): IBinder {
        Log.d(tag, "onBind()")
        configurationChange = false
        return localBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(tag, "onUnbind()")
        if (!configurationChange) {
            exitForeground()
        }
        // Allow clients to rebind, in which case onRebind will be called.
        return true
    }

    override fun onRebind(intent: Intent?) {
        Log.d(tag, "onRebind()")
        // client returns to the foreground and rebinds to service, so the service
        // can become a background services.
        stopForeground(true)
        configurationChange = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChange = true
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "onCreate()")
        locationRepository = LocationRepository(
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager,
            updateListener = object : LocationChangeListener {
                override fun onChange(location: android.location.Location) {
                    Log.d(tag, "lat:${location.latitude}, long:${location.longitude}")
                    val newLocation = Location(location.latitude, location.longitude)
                    locationUpdateListener?.onUpdate(newLocation)
                    // Update any foreground notification when we receive location updates.
                    showNotification(newLocation)
                }

                override fun onError(errorState: ErrorState) {
                    Log.d(tag, "errorState:$errorState")
                    errorListener?.onError(errorState.ordinal)
                    exitForeground()
                    stopLocationUpdates()
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(tag, "onStartCommand()")
        // Decide whether to remain in the background, promote to the foreground, or stop.
        enterForeground()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locationRepository = null
        locationUpdateListener = null
        errorListener = null
        super.onDestroy()
        Log.d(tag, "onDestroy()")
    }

    private fun exitForeground() {
        if (isForeground) {
            isForeground = false
            stopForeground(true)
        }
    }

    private fun enterForeground() {
        if (!isForeground) {
            isForeground = true

            // Show notification with the latest location.
            val lastLocation = locationRepository?.lastLocation
            val location = if (lastLocation != null) {
                Location(lastLocation.latitude, lastLocation.longitude)
            } else {
                Location(0.0, 0.0)
            }
            showNotification(location)
        }
    }

    private fun showNotification(location: Location) {
        if (!isForeground) {
            return
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(location))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(notificationChannel)
        }
    }

    private fun buildNotification(location: Location): Notification {
        // Tapping the notification opens the app.
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(this.packageName).apply {
                this?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentTitle =
            if (notificationContentTitle.isNullOrBlank()) "LocationUpdates" else notificationContentTitle
        val contentText =
            if (notificationContentText.isNullOrBlank()) "Latitude:${location.latitude}, longitude:${location.longitude}" else notificationContentText

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentIntent(pendingIntent)
            .setSmallIcon(notificationIcon)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    fun startLocationUpdates() {
        startService(Intent(this, this::class.java))
        locationRepository?.startLocationUpdates(updateInterval)
    }

    fun stopLocationUpdates() {
        locationRepository?.stopLocationUpdates()
        stopSelf()
    }

    class LocalBinder(val service: ForegroundLocationService) : Binder()

    private companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "LocationUpdates"
    }
}
