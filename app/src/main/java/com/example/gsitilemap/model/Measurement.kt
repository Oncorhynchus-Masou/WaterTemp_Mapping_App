package com.example.gsitilemap.model

import com.google.gson.annotations.SerializedName

data class Measurement(
    @SerializedName("measured_at")
    val measuredAt: String,

    val latitude: Double,

    val longitude: Double,

    @SerializedName("air_temperature_c")
    val airTemperatureC: Double?,

    @SerializedName("measurement_type")
    val measurementType: String,

    val readings: List<Reading>,

    val id: Int
)

data class Reading(
    @SerializedName("depth_m")
    val depthM: Double,

    @SerializedName("depth_uncertainty_m")
    val depthUncertaintyM: Double,

    @SerializedName("water_temperature_c")
    val waterTemperatureC: Double,

    @SerializedName("depth_type")
    val depthType: String
)