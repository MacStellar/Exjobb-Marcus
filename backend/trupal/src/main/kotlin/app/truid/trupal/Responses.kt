package app.truid.trupal

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.sql.Timestamp
import java.time.Instant

data class TokenResponse(
    @JsonProperty("refresh_token")
    val refreshToken: String,
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JsonProperty("token_type")
    val tokenType: String,
    val scope: String
)
data class ParResponse(
    @JsonProperty("request_uri")
    val requestUri: String,
    @JsonProperty("expires_in")
    val expiresIn: Long
)
data class PresentationResponse(
    val sub: String,
    val claims: List<PresentationResponseClaims>
)

data class PresentationResponseClaims(
    val type: String,
    val value: String
)

@Table("session")
data class Session(
    @Id var id: String?,
    var status: String,
    val created: Instant
)

@Table("user_session")
data class UserSession(
    @Id var id: String?,
    val sessionId: String?,
    val userId: String?,
    val userInfo: String?,
    val created: Instant
)

@Table("user_token")
data class UserToken(
    @Id var id: String?,
    val userId: String?,
    val refreshToken: String?,
    val created: Instant
)
