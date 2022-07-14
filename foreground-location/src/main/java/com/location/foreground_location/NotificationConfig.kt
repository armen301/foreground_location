package com.location.foreground_location

import androidx.annotation.DrawableRes
import com.location.foreground_location.R

/**
 * For testing purpose to see fetched latitude and longitude on the notification
 * do not pass [contentTitle] and [contentText].
 *
 * @param iconResource drawable resource id
 * @param contentTitle title of the notification
 * @param contentText text of the notification
 */
data class NotificationConfig @JvmOverloads constructor(
    @DrawableRes val iconResource: Int = R.drawable.ic_location,
    val contentTitle: String? = null,
    val contentText: String? = null,
)
