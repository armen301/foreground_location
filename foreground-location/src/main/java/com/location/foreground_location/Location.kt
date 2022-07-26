package com.location.foreground_location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Location(
    @SerialName("lat")
    val latitude: Double,
    @SerialName("lng")
    val longitude: Double,
    val description: String,
    val title: String,
)

@Serializable
class Data(
    val locations: List<Location>
)
