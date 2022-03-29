package model

import kotlinx.serialization.json.JsonNames

// POST /oauth2/token
@kotlinx.serialization.Serializable
data class Oauth2Token(
    @JsonNames("access_token")
    val accessToken: String,
    @JsonNames("token_type")
    val tokenType: String,
    @JsonNames("expires_in")
    val expiresIn: Long,
    @JsonNames("refresh_token")
    val refreshToken: String,
    @JsonNames("scope")
    val scope: String,
)