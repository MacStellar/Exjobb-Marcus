package app.truid.trupal

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
interface SessionRepository : CrudRepository<Session, String> {

    fun existsSessionById(id: String?): Boolean
}

@Repository
interface UserSessionRepository : CrudRepository<UserSession, String> {
    fun getUserSessionsBySessionId(id: String?): List<UserSession>?

    fun existsUserSessionBySessionId(id: String?): Boolean

    fun existsUserSessionsBySessionIdAndCookieId(session_id: String?, cookie_id: String?): Boolean

    @Modifying
    @Query("delete from user_session o where o.session_id = :session_id")
    fun deleteUserSessionBySessionId(session_id: String?)

}

@Repository
interface UserTokenRepository : CrudRepository<UserToken, String> {
    fun getUserTokenByCookie(cookie: String?): UserToken?

    @Modifying
    @Query("delete from user_token o where o.cookie = :cookie")
    fun deleteUserTokenByCookie(cookie: String?)

}

@Table("session")
data class Session(@Id var id: String?, val created: Instant)

@Table("user_session")
data class UserSession(
    @Id var id: String?,
    val sessionId: String?,
    val cookieId: String?,
    val userId: String?,
    val userInfo: String?,
    val created: Instant
)

@Table("user_token")
data class UserToken(
    @Id var id: String?,
    val cookie: String?,
    val userId: String?,
    val refreshToken: String?,
    val created: Instant
)

// Kod från johannes för persistable (kunna ha egna primary keys på databasgrejer):

class TrupalDatabase {
}

//open class Tester(
//    @Id var id: String?,
//    val session_id: String?,
//    val user_id: String?,
//    val user_info: String?,
//    val created: Instant
//) : Persistable<String> {
//    override fun getId(): String? {
//        return id
//    }
//
//    override fun isNew(): Boolean {
//        return true
//    }
//}
//open class NewTester(
//    session_id: String?,
//    user_id: String?,
//    user_info: String?,
//    created: Instant
//) : Tester(null, session_id, user_id, user_info, created) {
//    override fun getId(): String? {
//        return id
//    }
//
//    override fun isNew(): Boolean {
//        return false
//    }
//}


//@Embeddable
//data class UsersInfoId(
//    val sessionP2P: String? = null,
//    val sessionCookie: String? = null
//) : Serializable  {
//
//}
//
//@Entity
//@Table("usersinfo")
//data class UsersInfo(@EmbeddedId var id: UsersInfoId? = null, var user_info: String? = null)
