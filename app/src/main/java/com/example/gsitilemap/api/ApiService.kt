package com.example.gsitilemap.api

import com.example.gsitilemap.model.Measurement
import com.example.gsitilemap.model.MeasurementCreate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @GET("measurements")
    suspend fun getMeasurements(): List<Measurement>

    @POST("measurements")
    suspend fun createMeasurement(
        @Body measurement: MeasurementCreate
    ): Measurement
}