package com.example.watertemperaturemap.api

import android.content.Context
import com.example.watertemperaturemap.model.RefreshRequest
import com.example.watertemperaturemap.model.RefreshResponse
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.Call
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/** Automatically refreshes an expired access token and retries the failed request once. */
class TokenAuthenticator(context: Context) : Authenticator {

    private val preferences = context.applicationContext.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )

    private val refreshApi: RefreshApiService = Retrofit.Builder()
        .baseUrl(RetrofitClient.baseUrl)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RefreshApiService::class.java)

    override fun authenticate(route: Route?, response: Response): Request? {
        val requestPath = response.request.url.encodedPath
        if (requestPath.endsWith("/users/login") || requestPath.endsWith("/auth/refresh")) return null

        // Avoid an infinite retry loop if the refreshed token is also rejected.
        if (response.responseCount() >= 2) return null

        synchronized(this) {
            val currentAccessToken = preferences.getString("access_token", null)
                ?.takeIf { it.isNotBlank() }
                ?: return null

            val failedAccessToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")

            // Another request may already have refreshed the token while this one waited.
            if (failedAccessToken != null && failedAccessToken != currentAccessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            val refreshToken = preferences.getString("refresh_token", null)
                ?.takeIf { it.isNotBlank() }
                ?: return null

            val refreshed = try {
                refreshApi.refreshToken(RefreshRequest(refreshToken)).execute()
            } catch (_: Exception) {
                return null
            }

            if (!refreshed.isSuccessful) {
                if (refreshed.code() == 401 || refreshed.code() == 403) {
                    preferences.edit()
                        .remove("access_token")
                        .remove("refresh_token")
                        .commit()
                    SessionManager.notifySessionExpired()
                }
                return null
            }
            val tokenResponse = refreshed.body() ?: return null
            val newAccessToken = tokenResponse.accessToken.takeIf { it.isNotBlank() }
                ?: return null
            val newRefreshToken = tokenResponse.refreshToken.takeIf { it.isNotBlank() }
                ?: return null

            preferences.edit()
                .putString("access_token", newAccessToken)
                .putString("refresh_token", newRefreshToken)
                .commit()

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }

    private fun Response.responseCount(): Int {
        var count = 1
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

private interface RefreshApiService {
    @POST("auth/refresh")
    fun refreshToken(@Body request: RefreshRequest): Call<RefreshResponse>
}



