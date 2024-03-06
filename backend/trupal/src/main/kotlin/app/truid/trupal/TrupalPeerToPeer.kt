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

            // Saves a session to the database
            val testEntrySession = Session(null, Instant.now())
            val dbTestEntrySession = sessionDB.save(testEntrySession)

            // Set the P2P session attribute
            request.session.setAttribute("sessionP2P", "${dbTestEntrySession.id}")

            response.setHeader("Location", "http://localhost:8080/truid/v1/confirm-signup")
            response.status = HttpServletResponse.SC_FOUND

            return dbTestEntrySession.id!!

        }

        if (sessionDB.existsSessionById(sessionP2P)) {
            // This is where a user tries to join a session
            if (userSessionDB.existsUserSessionsBySessionIdAndCookieId(sessionP2P, request.session.id)) {
                // This is where a user goes if he/she has access through a cookie

                // Downloads all userSessions from the database that are connected to the sessionP2P
                val foundUserSessionsList = userSessionDB.getUserSessionsBySessionId(sessionP2P)
                var userInfoPrintOut = ""
                var index = 0

                for (userSession in foundUserSessionsList!!) {
                    index++
                    userInfoPrintOut += "<h2> User${index}: </h2> <h3> ${userSession.userInfo} </h3>"
                }
                response.status = HttpServletResponse.SC_OK

                return userInfoPrintOut

                // Here I'm going to add the code for redirecting to the P2P chat later

            } else {
                //This is where a new user comes in and connects their cookie to a session
                val userInfo: String?

                // Block user from entering if there already are 2 in session
                if ((userSessionDB.getUserSessionsBySessionId(sessionP2P)?.size ?: 0) >= 2) {
                    response.status = 403
                    return "Session is full"
                } else {
                    // 1. Tries to get Truid data with token

                    // Kanske ändrar på flowe:t här sen. Inte det bästa att köra en try catch
                    // för varje användare som kommer in och försöker connecta sin cookie
                    // till session.
                    try {
                        userInfo = trupalSignup.getPresentation(request)
                    } catch (e: Forbidden) {
                        // 2. If it doesn't work, redirect to confirm-signup with p2p session id saved in session for cookie on server side

                        request.session.setAttribute("sessionP2P", "$sessionP2P")
                        response.status = 302
                        response.setHeader("Location", "http://localhost:8080/truid/v1/confirm-signup")
                        return "null"
                    }

                    // 3. If it works, add the data and cookie session to the database
                    try {
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
                    } catch (e: Exception) {
                        response.status = 500
                        return "Error: Could not join session."
                    }

                    response.status = 302
                    response.setHeader("Location", "http://localhost:8080/peer-to-peer?session=$sessionP2P")

                    return "Session existed and you have now connected your cookie to the session. Redirecting to the P2P session again."

                }


            }

        } else {
            response.status = 404
            return "Session does not exist"
        }
    }


}
