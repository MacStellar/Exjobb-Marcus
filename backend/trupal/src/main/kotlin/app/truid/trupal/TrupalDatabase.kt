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

    fun getSessionById(id: String?): Session?

    @Query("select status from session where id = :id")
    fun getStatusById(id: String?): String?

}

@Repository
interface UserSessionRepository : CrudRepository<UserSession, String> {
    fun getUserSessionsBySessionId(id: String?): List<UserSession>

    fun existsUserSessionBySessionId(id: String?): Boolean

    fun existsUserSessionsBySessionIdAndUserId(session_id: String?, user_id: String?): Boolean

    @Modifying
    @Query("delete from user_session o where o.session_id = :session_id")
    fun deleteUserSessionBySessionId(session_id: String?)

}

@Repository
interface UserTokenRepository : CrudRepository<UserToken, String> {
    fun getUserTokenByUserId(user_id: String?): UserToken?

    @Modifying
    @Query("delete from user_token o where o.user_id = :user_id")
    fun deleteUserTokenByUserId(user_id: String?)

}

