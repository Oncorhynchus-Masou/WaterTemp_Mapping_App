// ApiService.kt
package com.example.watertemperaturemap.api

import com.example.watertemperaturemap.model.LoginRequest
import com.example.watertemperaturemap.model.LoginResponse
import com.example.watertemperaturemap.model.RefreshRequest
import com.example.watertemperaturemap.model.RefreshResponse
import com.example.watertemperaturemap.model.Measurement
import com.example.watertemperaturemap.model.MeasurementCreate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @GET("measurements")
    suspend fun getMeasurements(): List<Measurement>

    @GET("users/me/measurements")
    suspend fun getMyMeasurements(): List<Measurement>

    @POST("measurements")
    suspend fun createMeasurement(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body measurement: MeasurementCreate
    ): Measurement

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshRequest
    ): RefreshResponse

    @POST("auth/logout")
    suspend fun logout(
        @Body request: RefreshRequest
    )

    @POST("users/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse
}


