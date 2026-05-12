package com.chiiii5640.thsrapp.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant

class TdxAuthInterceptor(
    private val client: HttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val clock: Clock,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var cachedToken: Token? = null

    suspend fun bearerToken(forceRefresh: Boolean = false): String {
        val now = Instant.now(clock)
        val token = cachedToken
        if (!forceRefresh && token != null && token.expiresAt.minusSeconds(60).isAfter(now)) {
            return token.accessToken
        }
        require(clientId.isNotBlank() && clientSecret.isNotBlank()) {
            "TDX_CLIENT_ID / TDX_CLIENT_SECRET 尚未設定"
        }
        val response = client.postForm(
            url = TOKEN_URL,
            body = mapOf(
                "grant_type" to "client_credentials",
                "client_id" to clientId,
                "client_secret" to clientSecret,
            ),
        )
        if (!response.isSuccessful) error("TDX auth failed: HTTP ${response.code}")
        val payload = json.decodeFromString<TokenPayload>(response.body)
        val refreshed = Token(
            accessToken = payload.accessToken,
            expiresAt = now.plusSeconds(payload.expiresIn),
        )
        cachedToken = refreshed
        return refreshed.accessToken
    }

    private data class Token(
        val accessToken: String,
        val expiresAt: Instant,
    )

    @Serializable
    private data class TokenPayload(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
    )

    companion object {
        const val TOKEN_URL = "https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token"
    }
}
