package app.truid.trupal

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface SessionRepository : CrudRepository<Session, String>

@Repository
interface UserSessionRepository : CrudRepository<UserSession, String> {
    fun getUserSessionsBySessionId(id: String?): List<UserSession>

    fun existsUserSessionsBySessionIdAndUserId(
        session_id: String?,
        user_id: String?,
    ): Boolean
}

@Repository
interface UserTokenRepository : CrudRepository<UserToken, String> {
    @Modifying
    @Query("delete from user_token o where o.user_id = :user_id")
    fun deleteUserTokenByUserId(user_id: String?)
}
