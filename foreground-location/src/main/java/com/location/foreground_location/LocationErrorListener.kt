package com.location.foreground_location

/**
 * This listener is used to track the errors during fetching location updates
 */
fun interface LocationErrorListener {
    /**
     * @param errorCode it can be
     * 0 - no permission,
     * 1 - GPS is off
     * 2 - Network is off
     * 3 - other error case
     */
    fun onError(errorCode: Int)
}
