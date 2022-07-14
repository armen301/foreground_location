package com.location.foreground_location

import com.location.foreground_location.Location

/**
 * This listener is used to track the location changes.
 */
fun interface LocationUpdateListener {
    /**
     * @param newLocation updated location
     */
    fun onUpdate(newLocation: Location)
}
