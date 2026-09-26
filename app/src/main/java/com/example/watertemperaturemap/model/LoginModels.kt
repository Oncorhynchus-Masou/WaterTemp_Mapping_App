package com.example.watertemperaturemap.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserInfo(
    val id: String,

    @SerializedName("display_name")
    val displayName: String,

    val email: String,
    val role: String
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("refresh_token")
    val refreshToken: String,

    @SerializedName("token_type")
    val tokenType: String,

    val user: UserInfo
)
// /auth/refresh に送るRefresh Token
data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

// /auth/refresh から返る新しいAccess Token
data class RefreshResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("token_type")
    val tokenType: String
)
