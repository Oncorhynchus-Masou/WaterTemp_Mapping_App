// ApiService.kt
package com.example.gsitilemap.api

import com.example.gsitilemap.model.LoginRequest
import com.example.gsitilemap.model.LoginResponse
import com.example.gsitilemap.model.Measurement
import com.example.gsitilemap.model.MeasurementCreate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @GET("measurements")
    suspend fun getMeasurements(): List<Measurement>

    @POST("measurements")
    suspend fun createMeasurement(
        @Header("Authorization") authorization: String,
        @Body measurement: MeasurementCreate
    ): Measurement

    @POST("users/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse
}