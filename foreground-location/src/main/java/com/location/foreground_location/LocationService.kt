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
import androidx.core.app.NotificationManagerCompat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.properties.Delegates

private const val NOTIFICATION_ID_LOCATION = 1
private const val NOTIFICATION_ID_PIN = 2
private const val NOTIFICATION_CHANNEL_ID_LOCATION = "LocationUpdates"
private const val NOTIFICATION_CHANNEL_ID_PIN = "NearPin"

class LocationService : Service() {

    companion object {

        private var minDistance by Delegates.notNull<Double>()
        private lateinit var locations: List<Location>

        @SuppressLint("StaticFieldLeak")
        private var activity: Activity? = null
        private var updateInterval by Delegates.notNull<Long>()
        @SuppressLint("StaticFieldLeak")
        private var service: Service? = null

        private var locationNotificationContentTitle: String? = null
        private var locationNotificationContentText: String? = null
        private var notificationIcon by Delegates.notNull<Int>()

        private var locationRepository: LocationRepository? = null

        @JvmStatic
        fun start(
            activity: Activity,
            locationUpdateConfig: LocationUpdateConfig,
            notificationConfig: NotificationConfig,
            coordinates: String,
            minDistance: Double, // in meters
        ) {
            init(activity, locationUpdateConfig, notificationConfig, coordinates, minDistance)

            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(activity, "LocationService no permission", Toast.LENGTH_SHORT).show()
                return
            }

            Toast.makeText(activity, "LocationService.start()", Toast.LENGTH_SHORT).show()

            activity.startService(Intent(activity, LocationService::class.java))

            locationRepository = LocationRepository(
                locationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager,
                updateListener = object : LocationChangeListener {
                    override fun onChange(location: android.location.Location) {
                        locations.forEach {
                            val distance = distance(
                                lat1 = location.latitude,
                                lon1 = location.longitude,
                                lat2 = it.latitude,
                                lon2 = it.longitude
                            )
                            if (distance <= minDistance) {
                                with(NotificationManagerCompat.from(service!!)) {
                                    createNotificationChannel(NOTIFICATION_CHANNEL_ID_PIN)
                                    // notificationId is a unique int for each notification that you must define
                                    notify(
                                        NOTIFICATION_ID_PIN,
                                        buildNotification(NOTIFICATION_CHANNEL_ID_PIN, it.title, it.description)
                                    )
                                }
                            }
                        }

                        Toast.makeText(
                            activity,
                            "LocationService onChange() lat: ${location.latitude} long: ${location.longitude}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onError(errorState: ErrorState) {
                        Toast.makeText(
                            activity,
                            "LocationService onError() error: ${errorState.ordinal}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            Toast.makeText(activity, "LocationService.start()", Toast.LENGTH_SHORT).show()

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
            coordinates: String,
            minDistance: Double,
        ) {
            this.activity = activity
            notificationIcon = notificationConfig.iconResource
            locationNotificationContentTitle = notificationConfig.contentTitle
            locationNotificationContentText = notificationConfig.contentText
            updateInterval = locationUpdateConfig.updateInterval
            this.minDistance = minDistance

            val data: Data = Json.decodeFromString(coordinates)
            this.locations = data.locations
        }

        private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            if ((lat1 == lat2) && (lon1 == lon2)) return 0.0

            val theta = lon1 - lon2
            var dist =
                sin(degToRadian(lat1)) * sin(degToRadian(lat2)) + cos(degToRadian(lat1)) * cos(degToRadian(lat2)) * cos(
                    degToRadian(theta)
                )
            dist = acos(dist)
            dist = radToDegree(dist)
            dist *= 60 * 1.1515

            return abs(dist)
        }

        private fun degToRadian(deg: Double): Double {
            return deg * Math.PI / 180.0
        }

        private fun radToDegree(rad: Double): Double {
            return rad * 180 / Math.PI
        }

        private fun showNotification() {
            createNotificationChannel(NOTIFICATION_CHANNEL_ID_LOCATION)
            service?.startForeground(NOTIFICATION_ID_LOCATION, buildNotification(NOTIFICATION_CHANNEL_ID_LOCATION, getLocationContentTitle(), getLocationContentText()))
        }

        private fun createNotificationChannel(channelId: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel = NotificationChannel(
                    channelId,
                    "Location updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                val manager = service?.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(notificationChannel)
            }
        }

        private fun buildNotification(channelId: String, contentTitle: String, contentText: String): Notification {
            // Tapping the notification opens the app.
            val pendingIntent = PendingIntent.getActivity(
                service,
                0,
                service!!.packageManager.getLaunchIntentForPackage(service!!.packageName).apply {
                    this?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(service!!, channelId)
                .setContentIntent(pendingIntent)
                .setSmallIcon(notificationIcon)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

            if (channelId == NOTIFICATION_CHANNEL_ID_LOCATION) {
                builder.setSilent(true)
            } else {
                builder.setAutoCancel(true)
            }

            return builder.build()
        }

        private fun getLocationContentTitle(): String {
            return if (locationNotificationContentTitle.isNullOrBlank()) "LocationUpdates" else locationNotificationContentTitle!!
        }

        private fun getLocationContentText(): String {
            return if (locationNotificationContentText.isNullOrBlank()) "fetching location" else locationNotificationContentText!!
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        service = this
        Toast.makeText(activity, "LocationService onStartCommand()", Toast.LENGTH_SHORT).show()
        showNotification()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Toast.makeText(activity, "LocationService onDestroy()", Toast.LENGTH_SHORT).show()
        locationRepository = null
        activity = null
        service = null
        super.onDestroy()
    }
}
