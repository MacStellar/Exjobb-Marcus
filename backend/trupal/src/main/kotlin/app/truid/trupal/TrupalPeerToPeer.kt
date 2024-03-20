package app.truid.trupal

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

class SessionNotFound() : Exception()
class SessionAlreadyComplete() : Exception()

@RestController
class TrupalPeerToPeer(
    val sessionDB: SessionRepository,
    val userSessionDB: UserSessionRepository,
    val trupalSignup: TrupalSignup
) {

    // Creates a session and sets id in http session
    @GetMapping("/peer-to-peer")
    fun createPeerToPeerSession(
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): String {
        // Saves a session to the database
        val p2pSession = sessionDB.save(Session(null, Instant.now()))

        userSessionDB.save(
            UserSession(
                null,
                p2pSession.id,
                request.session.id,
                "test_user_id",
                null,
                Instant.now()
            )
        )

        // Set the P2P session attribute
        request.session.setAttribute("sessionP2P", "${p2pSession.id}")

        response.setHeader("Location", "/truid/v1/confirm-signup")
        response.status = HttpServletResponse.SC_FOUND

        return p2pSession.id!!
    }


    // User redirects here after authorization at truid
    // Fetch presentation and return link that user 1 can share
    @GetMapping("/peer-to-peer/create")
    fun fetchInitialPresentation(
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): String {
        // Saves a session to the database
        val p2pSession = sessionDB.save(Session(null, Instant.now()))

        userSessionDB.save(
            UserSession(
                null,
                p2pSession.id,
                request.session.id,
                "test_user_id",
                null,
                Instant.now()
            )
        )

        // Set the P2P session attribute
        request.session.setAttribute("sessionP2P", "${p2pSession.id}")

        response.setHeader("Location", "/truid/v1/confirm-signup")
        response.status = HttpServletResponse.SC_FOUND

        return p2pSession.id!!
    }

    // Second user enters here. Set cookie and redirect to truid
    @GetMapping("/peer-to-peer/{sessionId}/init-join")
    fun joinPeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ) {
        val session = sessionDB.findById(p2pSessionId).orElse(null) ?: throw SessionNotFound()
        val userSessions = userSessionDB.getUserSessionsBySessionId(p2pSessionId)

        if (userSessions.size > 1) {
            throw SessionAlreadyComplete()
        }
        if (userSessions.isEmpty()) {
            throw RuntimeException("Session has no other member")
        }

        userSessionDB.save(
            UserSession(
                null,
                p2pSessionId,
                request.session.id,
                "test_user_id",
                null,
                Instant.now()
            )
        )

        request.session.setAttribute("sessionP2P", p2pSessionId)
        response.status = 302
        response.setHeader("Location", "/truid/v1/confirm-signup")
    }

    // Second user redirects here after authorization and completes the p2p
    @GetMapping("/peer-to-peer/join")
    fun completePeerToPeerSession(
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): String {
        val p2pSessionId = request.session.getAttribute("sessionP2P") as String
        val session = sessionDB.findById(p2pSessionId).orElse(null) ?: throw SessionNotFound()
        val userSessions = userSessionDB.getUserSessionsBySessionId(p2pSessionId)

        if (userSessions.size > 1) {
            throw SessionAlreadyComplete()
        }

        var userInfoPrintOut = ""
        var index = 0

        for (userSession in userSessions) {
            index++
            userInfoPrintOut += "<h2> User${index}: </h2> <h3> ${userSession.userInfo} </h3>"
        }
        response.status = HttpServletResponse.SC_OK

        return userInfoPrintOut
    }
}
