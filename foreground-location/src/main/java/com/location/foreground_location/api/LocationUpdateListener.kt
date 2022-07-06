package com.location.foreground_location.api

/**
 * This listener is used to track the location changes.
 */
fun interface LocationUpdateListener {
    /**
     * @param newLocation updated location
     */
    fun onUpdate(newLocation: Location)
}
