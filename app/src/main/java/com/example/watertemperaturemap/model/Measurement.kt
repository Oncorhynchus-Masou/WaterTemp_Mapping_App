package com.example.watertemperaturemap.model

import com.google.gson.annotations.SerializedName

data class Measurement(
    @SerializedName("measured_at")
    val measuredAt: String,

    val latitude: Double,

    val longitude: Double,

    @SerializedName("air_temperature_c")
    val airTemperatureC: Double?,

    @SerializedName("position_source")
    val positionSource: String?,

    @SerializedName("positioning_mode")
    val positioningMode: String?,

    @SerializedName("horizontal_accuracy_m")
    val horizontalAccuracyM: Double?,

    @SerializedName("vertical_accuracy_m")
    val verticalAccuracyM: Double?,

    @SerializedName("satellite_count")
    val satelliteCount: Int?,

    @SerializedName("device_name")
    val deviceName: String?,

    @SerializedName("measurement_type")
    val measurementType: String,

    val readings: List<Reading>,

    val id: Int,

    @SerializedName("deletion_request_pending")
    val deletionRequestPending: Boolean = false
)

data class MeasurementDeletionRequestResponse(
    val id: Int,

    @SerializedName("measurement_id")
    val measurementId: Int,

    val status: String
)

data class Reading(
    @SerializedName("depth_m")
    val depthM: Double,

    @SerializedName("depth_uncertainty_m")
    val depthUncertaintyM: Double?,

    @SerializedName("water_temperature_c")
    val waterTemperatureC: Double,
)

data class MeasurementCreate(
    @SerializedName("measured_at")
    val measuredAt: String,

    val latitude: Double,

    val longitude: Double,

    @SerializedName("air_temperature_c")
    val airTemperatureC: Double? = null,

    @SerializedName("position_source")
    val positionSource: String? = null,

    @SerializedName("positioning_mode")
    val positioningMode: String? = null,

    @SerializedName("horizontal_accuracy_m")
    val horizontalAccuracyM: Double? = null,

    @SerializedName("vertical_accuracy_m")
    val verticalAccuracyM: Double? = null,

    @SerializedName("satellite_count")
    val satelliteCount: Int? = null,

    @SerializedName("device_name")
    val deviceName: String? = null,

    @SerializedName("measurement_type")
    val measurementType: String,

    val readings: List<ReadingCreate>
)

data class ReadingCreate(
    @SerializedName("depth_m")
    val depthM: Double,

    @SerializedName("depth_uncertainty_m")
    val depthUncertaintyM: Double? = null,

    @SerializedName("water_temperature_c")
    val waterTemperatureC: Double,
)
