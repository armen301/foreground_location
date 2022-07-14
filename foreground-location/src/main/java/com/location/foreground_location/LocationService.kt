package com.location.foreground_location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlin.properties.Delegates

private const val NOTIFICATION_ID = 1
private const val NOTIFICATION_CHANNEL_ID = "LocationUpdates"

class LocationService : Service() {

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var activity: Activity? = null
        private var locationUpdateListener: LocationUpdateListener? = null
        private var errorListener: LocationErrorListener? = null
        private var updateInterval by Delegates.notNull<Long>()

        private var notificationContentTitle: String? = null
        private var notificationContentText: String? = null
        private var notificationIcon by Delegates.notNull<Int>()

        private var locationRepository: LocationRepository? = null

        /**
         * @param activity the activity which is hold the instance of [LocationService]
         * @param locationUpdateConfig config object for requesting location updates
         * @param notificationConfig config object for notifications
         * @param locationUpdateListener listener to subscribe on location updates
         * @param errorListener listener to subscribe on errors related to location
         */
        @JvmStatic
        fun start(
            activity: Activity,
            locationUpdateConfig: LocationUpdateConfig,
            notificationConfig: NotificationConfig,
            locationUpdateListener: LocationUpdateListener,
            errorListener: LocationErrorListener
        ) {
            init(activity, locationUpdateConfig, notificationConfig, locationUpdateListener, errorListener)

            activity.startService(Intent(activity, LocationService::class.java))

            locationRepository = LocationRepository(
                locationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager,
                updateListener = object : LocationChangeListener {
                    override fun onChange(location: android.location.Location) {
                        locationUpdateListener.onUpdate(Location(location.latitude, location.longitude))
                        Toast.makeText(
                            activity,
                            "LocationService onChange() lat: ${location.latitude} long: ${location.longitude}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onError(errorState: ErrorState) {
                        errorListener.onError(errorState.ordinal)
                        Toast.makeText(
                            activity,
                            "LocationService onError() error: ${errorState.ordinal}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            Toast.makeText(activity, "LocationService.start()", Toast.LENGTH_SHORT).show()

            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                errorListener.onError(ErrorState.NO_LOCATION_PERMISSION.ordinal)
                Toast.makeText(activity, "LocationService no permission", Toast.LENGTH_SHORT).show()
                return
            }
            locationRepository?.startLocationUpdates(updateInterval)
        }

        @JvmStatic
        fun stop() {
            activity?.let {
                locationRepository?.stopLocationUpdates()
                it.stopService(Intent(it, LocationService::class.java))
                Toast.makeText(it, "LocationService.stop()", Toast.LENGTH_SHORT).show()
            }
        }

        private fun init(
            activity: Activity,
            locationUpdateConfig: LocationUpdateConfig,
            notificationConfig: NotificationConfig,
            locationUpdateListener: LocationUpdateListener,
            errorListener: LocationErrorListener
        ) {
            this.activity = activity
            notificationIcon = notificationConfig.iconResource
            notificationContentTitle = notificationConfig.contentTitle
            notificationContentText = notificationConfig.contentText
            updateInterval = locationUpdateConfig.updateInterval
            this.locationUpdateListener = locationUpdateListener
            this.errorListener = errorListener
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Toast.makeText(activity, "LocationService onStartCommand()", Toast.LENGTH_SHORT).show()
        showNotification()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Toast.makeText(activity, "LocationService onDestroy()", Toast.LENGTH_SHORT).show()
        locationRepository = null
        locationUpdateListener = null
        errorListener = null
        activity = null
        super.onDestroy()
    }

    private fun showNotification() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
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

    private fun buildNotification(): Notification {
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
            if (notificationContentText.isNullOrBlank()) "fetching location" else notificationContentText

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
}
