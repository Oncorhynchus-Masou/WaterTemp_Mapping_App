package com.example.watertemperaturemap.api

import android.content.Context
import com.example.watertemperaturemap.model.LoginRequest
import com.example.watertemperaturemap.model.LoginResponse
import com.example.watertemperaturemap.model.Measurement
import com.example.watertemperaturemap.model.MeasurementCreate
import com.example.watertemperaturemap.model.RefreshRequest
import com.example.watertemperaturemap.model.RefreshResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    internal const val baseUrl = "http://192.168.1.7:8000/"
    private const val PREFERENCES_NAME = "app_preferences"
    private const val ACCESS_TOKEN_KEY = "access_token"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val tokenClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val path = original.url.encodedPath
                val isAuthenticationRequest = path.endsWith("/users/login") ||
                    path.endsWith("/auth/refresh")

                val context = appContext
                val accessToken = if (isAuthenticationRequest || context == null) {
                    null
                } else {
                    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                        .getString(ACCESS_TOKEN_KEY, null)
                }

                val request = if (accessToken.isNullOrBlank()) {
                    original
                } else {
                    original.newBuilder()
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                }
                chain.proceed(request)
            })
            .authenticator(TokenAuthenticator(requireNotNull(appContext) {
                "RetrofitClient.initialize(context) must be called before using the API"
            }))
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(tokenClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}


