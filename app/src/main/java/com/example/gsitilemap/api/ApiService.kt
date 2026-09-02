package com.example.gsitilemap.api

import com.example.gsitilemap.model.Measurement
import retrofit2.http.GET

interface ApiService {

    @GET("measurements")
    suspend fun getMeasurements(): List<Measurement>
}