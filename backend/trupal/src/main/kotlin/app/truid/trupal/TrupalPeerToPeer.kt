package app.truid.trupal

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant


@RestController
class TrupalPeerToPeer(
    val sessionDB: SessionRepository,
    val userSessionDB: UserSessionRepository,
    val trupalSignup: TrupalSignup
) {


    @GetMapping("/peer-to-peer")
    fun peerToPeer(
        @RequestParam("session") sessionP2P: String?, response: HttpServletResponse, request: HttpServletRequest
    ): String {


        if (sessionP2P == null) {
//        Sparar en session till databasen
            val testEntrySession = Session(null, Instant.now())
            val dbTestEntrySession = sessionDB.save(testEntrySession)

            // Set the P2P session attribute
            request.session.setAttribute("sessionP2P", "${dbTestEntrySession.id}")

            response.setHeader("Location", "http://localhost:8080/truid/v1/confirm-signup")
            response.status = HttpServletResponse.SC_FOUND

            return dbTestEntrySession.id!!

        }
//        else if ("more than 2 in the session") { "unauthorized" }

        if (sessionDB.existsSessionById(sessionP2P)) {
            if (userSessionDB.existsUserSessionsBySessionIdAndCookieId(sessionP2P, request.session.id)) {
                // Session exist and user has access

//            Hämtar alla userSessions från databasen som är kopplade till ett session_id
                val foundUserSessionsList = userSessionDB.getUserSessionsBySessionId(sessionP2P)
                var userInfoPrintOut = ""
                if (foundUserSessionsList != null) {
                    for (userSession in foundUserSessionsList) {
                        userInfoPrintOut = userInfoPrintOut + userSession.userInfo
                    }
                    response.status = HttpServletResponse.SC_OK

                    return userInfoPrintOut

                    // If the session exists, redirect to the P2P chat
                    // *skriv kod för detta här*
                }
                return "null"
            } else {
//            Detta är vart en ny användare kommer in och kopplar sin cookie till en session
                val userInfo: String?

                // 1. Försök hämta Truid data med access token
                try {
                    userInfo = trupalSignup.getPresentation(request)
                } catch (e: Forbidden) {
                    // 2. Om det inte går, redirect till confirm-signup med session id sparad i session för cookie på server side

                    request.session.setAttribute("sessionP2P", "$sessionP2P")
                    response.status = 302
                    response.setHeader("Location", "http://localhost:8080/truid/v1/confirm-signup")
                    return "null"
                }

                // 3. Om det går, lägg till datan och cookie session i databas

                userSessionDB.save(
                    UserSession(
                        null,
                        sessionP2P,
                        request.session.id,
                        "test_user_id",
                        userInfo,
                        Instant.now()
                    )
                )


                response.status = 302
                response.setHeader("Location", "http://localhost:8080/peer-to-peer?session=$sessionP2P")

                return "Session does exist in the database but your cookie is not connected, redirecting to confirm-signup"

            }

        } else {
            response.status = 404
            return "Session does not exist"
        }
    }


}