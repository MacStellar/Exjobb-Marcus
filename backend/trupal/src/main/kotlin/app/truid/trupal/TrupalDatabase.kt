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

    @Query("select status from session where id = :id")
    fun getStatusById(id: String?): String?
}

@Repository
interface UserSessionRepository : CrudRepository<UserSession, String> {
    fun getUserSessionsBySessionId(id: String?): List<UserSession>

    fun existsUserSessionsBySessionIdAndUserId(session_id: String?, user_id: String?): Boolean
}

@Repository
interface UserTokenRepository : CrudRepository<UserToken, String> {
    fun getUserTokenByUserId(user_id: String?): UserToken?

    @Modifying
    @Query("delete from user_token o where o.user_id = :user_id")
    fun deleteUserTokenByUserId(user_id: String?)

}

