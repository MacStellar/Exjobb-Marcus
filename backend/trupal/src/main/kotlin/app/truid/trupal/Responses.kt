package app.truid.trupal

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
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

data class PresentationResponseList(
    val presentations: List<PresentationResponse>
)

data class PresentationResponse(
    val sub: String,
    val claims: List<PresentationResponseClaims>
)

data class PresentationResponseClaims(
    val type: String,
    val value: String
) {
}

@Table("session")
data class Session(
    @Id var id: String?,
    @Column("status")
    var status: String,
    @Column("created")
    val created: Instant
)


@Table("user_session")
data class UserSession(
    @Id var id: String?,
    @Column("session_id")
    val sessionId: String?,
    @Column("user_id")
    val userId: String?,
    @Column("user_presentation")
//    @Convert(converter = PresentationResponseAttributeConverter::class)
    val userPresentation: PresentationResponse?,
    @Column("created")
    val created: Instant
) {

}

@Table("user_token")
data class UserToken(
    @Id var id: String?,
    @Column("user_id")
    val userId: String?,
    @Column("refresh_token")
    val refreshToken: String?,
    @Column("created")
    val created: Instant
)

