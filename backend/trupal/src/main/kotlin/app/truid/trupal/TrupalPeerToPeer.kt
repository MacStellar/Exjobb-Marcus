package app.truid.trupal

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import jakarta.ws.rs.ForbiddenException
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import java.net.URI
import java.time.Instant

@RestController
class TrupalPeerToPeer(
    //    The value loads from a file in .gitignore
    @Value("\${oauth2.clientId}")
    val clientId: String,
    //    The value loads from a file in .gitignore
    @Value("\${oauth2.clientSecret}")
    val clientSecret: String,
    @Value("\${oauth2.redirectUri.create}")
    val createP2PSessionUri: String,
    @Value("\${oauth2.redirectUri.join}")
    val joinP2PSessionUri: String,
    @Value("\${oauth2.truid.signup-endpoint}")
    val truidSignupEndpoint: String,
    @Value("\${oauth2.truid.token-endpoint}")
    val truidTokenEndpoint: String,
    @Value("\${oauth2.truid.presentation-endpoint}")
    val truidPresentationEndpoint: String,
    @Value("\${web.signup.success}")
    val webSuccess: URI,
    @Value("\${web.signup.failure}")
    val webFailure: URI,
    val sessionDB: SessionRepository,
    val userSessionDB: UserSessionRepository,
    val userTokenDB: UserTokenRepository,
    val restTemplate: RestTemplate,
) {
    //    User 1 enters peer-to-peer initiator and gets redirected to Truid
    @GetMapping("/truid/v1/peer-to-peer")
    fun initPeerToPeerSession(
        response: HttpServletResponse,
        request: HttpServletRequest,
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
    ) {
        // Saves a session to the database
        val p2pSession = sessionDB.save(Session(null, SessionStatus.CREATED, Instant.now()))
        request.session.setAttribute("p2pSessionId", "${p2pSession.id}")

        // Initiates authorization and redirects user to truid signup
        initiateAuthorization(request, response, xRequestedWith, createP2PSessionUri)
    }

    // User redirects here after authorization at truid
    // Get token and fetch presentation
    // Creates a session and sets p2pSessionId in session on server-side
    // Fetch presentation and return link that user 1 can share
    @GetMapping("/truid/v1/peer-to-peer/create")
    fun createPeerToPeerSession(
        @RequestParam("code") code: String?,
        @RequestParam("state") state: String?,
        @RequestParam("error") error: String?,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ) {
        val session = request.session

        val p2pSessionId = session.getAttribute("p2pSessionId") as String? ?: throw SessionNotFound()
        val p2pSession =
            sessionDB.findById(p2pSessionId).orElseThrow {
                P2PSessionNotFound()
            }

        val (userId, userInfo) = getInitialPresentation(code, state, error, session, createP2PSessionUri)

        p2pSession.status = SessionStatus.INITIALIZED
        sessionDB.save(p2pSession)

        // Saves a user session to the database
        userSessionDB.save(
            UserSession(
                null,
                p2pSessionId,
                userId,
                userInfo,
                Instant.now(),
            ),
        )

        response.status = 302
        response.setHeader(HttpHeaders.LOCATION, "/truid/v1/peer-to-peer/$p2pSessionId/share")
    }

    // Inkludera polling på denna sida
    // First user is redirected here after creating and joining the p2p session.
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}/share")
    fun sharePeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
        response: HttpServletResponse,
        request: HttpServletRequest,
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
    ): String? {
        return "Send this to your friend: http://localhost:8080/truid/v1/peer-to-peer/$p2pSessionId/init-join </br> See the data of p2p-session: http://localhost:8080/truid/v1/peer-to-peer/$p2pSessionId/data"
    }

    // Second user enters here. Set cookie and redirect to truid
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}/init-join")
    fun joinPeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
        response: HttpServletResponse,
        request: HttpServletRequest,
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
    ) {
        // Check if session and userSessions exist for the given p2pSessionId
        val p2pSession =
            sessionDB.findById(p2pSessionId).orElseThrow {
                P2PSessionNotFound()
            }
        val userId = request.session.getAttribute("userId") as String?

        if (p2pSession.status == SessionStatus.INITIALIZED) {
            throw RuntimeException("Session has not been created yet")
        }

        if (p2pSession.status == SessionStatus.FAILED) {
            throw RuntimeException("Session has failed")
        }

        if (userId != null) {
            response.status = 302
            response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/$p2pSessionId/data")
            return
        }

        request.session.setAttribute("p2pSessionId", p2pSessionId)

        // Initiates authorization and redirects user to Truid signup
        initiateAuthorization(request, response, xRequestedWith, joinP2PSessionUri)
    }

    // Second user redirects here after authorization and completes the p2p
    @GetMapping("/truid/v1/peer-to-peer/join")
    fun completePeerToPeerSession(
        @RequestParam("code") code: String?,
        @RequestParam("state") state: String?,
        @RequestParam("error") error: String?,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Void? {
        val session = request.session

        // Skriv ett test för när det inte finns någon session som cookie eller p2pSession i databasen
        // Checks if user has a cookie for p2pSessionId and if the session exists in the database
        val p2pSessionId = session.getAttribute("p2pSessionId") as String? ?: throw SessionNotFound()
        val p2pSession =
            sessionDB.findById(p2pSessionId).orElseThrow {
                P2PSessionNotFound()
            }

        if (p2pSession.status == SessionStatus.INITIALIZED) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.writer.print("P2P-Session has not been created")
            return null
        }
        if (p2pSession.status == SessionStatus.FAILED) {
            throw RuntimeException("P2P-Session has failed")
        }

        val (userId, userInfo) = getInitialPresentation(code, state, error, session, joinP2PSessionUri)

        if (userSessionDB.existsUserSessionsBySessionIdAndUserId(p2pSessionId, userId)) {
            response.status = 302
            response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/$p2pSessionId/data")
        } else if (p2pSession.status == SessionStatus.COMPLETED) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.writer.print("P2P-Session is full")
            return null
        }

        userSessionDB.save(
            UserSession(
                null,
                p2pSessionId,
                userId,
                userInfo,
                Instant.now(),
            ),
        )

        p2pSession.status = SessionStatus.COMPLETED
        sessionDB.save(p2pSession)

        response.status = 302
        response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/$p2pSessionId/data")

        return null
    }

    // Return state of session (INITIATED, CREATED, COMPLETED, FAILED)
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}")
    fun getStatusOfPeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
    ): String? {
        sessionDB.findById(p2pSessionId).orElseThrow {
            P2PSessionNotFound()
        }
        val status = sessionDB.getStatusById(p2pSessionId)

        return status
    }

    // Get both users data from a p2p session
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}/data")
    fun getDataOfPeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): MutableList<PresentationResponse>? {
        val session = request.session

        sessionDB.findById(p2pSessionId).orElseThrow {
            P2PSessionNotFound()
        }

        if (request.session.getAttribute("userId") == null) {
            response.status = 302
            response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/$p2pSessionId/init-join")
        }
        val userId = request.session.getAttribute("userId") as String?
        val userData: List<UserSession>?
        if (userSessionDB.existsUserSessionsBySessionIdAndUserId(p2pSessionId, userId)) {
            try {
                userData = userSessionDB.getUserSessionsBySessionId(p2pSessionId)
            } catch (e: Exception) {
                throw UserSessionsNotFound()
            }
        } else {
            if (session.getAttribute("userId") == null) {
                response.status = 302
                response.setHeader(
                    "Location",
                    "http://localhost:8080/truid/v1/peer-to-peer/$p2pSessionId/init-join",
                )

                return null
            } else {
                throw Forbidden("access_denied", "User not part of session")
            }
        }

        val userPresentations = mutableListOf<PresentationResponse>()

        for (userSession in userData) {
            userPresentations.add(userSession.userPresentation!!)
        }

        // Returns cookie with session id
        request.session.id
        response.addCookie(Cookie("JSESSIONID", request.session.id))

        return userPresentations
    }

    @ExceptionHandler(SessionAlreadyComplete::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun noSession(): Map<String, String> {
        return mapOf("ErrorMsg" to "Session is already complete")
    }

    @ExceptionHandler(Forbidden::class)
    fun handleForbidden(
        e: Forbidden,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        if (request.getHeader(HttpHeaders.ACCEPT).contains(MediaType.TEXT_HTML_VALUE)) {
            // Redirect to error page in the webapp flow
            response.setHeader(HttpHeaders.LOCATION, URIBuilder(webFailure).addParameter("error", e.error).build().toString())
            response.status = HttpServletResponse.SC_FOUND

            return null
        } else {
            // Return a 403 response in case of an AJAX request
            response.status = HttpServletResponse.SC_FORBIDDEN

            return mapOf(
                "error" to e.error,
            )
        }
    }

    private fun initiateAuthorization(
        request: HttpServletRequest,
        response: HttpServletResponse,
        xRequestedWith: String?,
        redirect: String?,
    ) {
        val session = request.session

        val userId = request.session.getAttribute("userId") as String?
        if (userId != null) {
            clearPersistence(userId)
        }

        val truidSignupUrl =
            URIBuilder(truidSignupEndpoint)
                .addParameter("response_type", "code")
                .addParameter("client_id", clientId)
                .addParameter("scope", "truid.app/data-point/email truid.app/data-point/birthdate")
                .addParameter("redirect_uri", redirect)
                .addParameter("state", createOauth2State(session))
                .addParameter("code_challenge", createOauth2CodeChallenge(session))
                .addParameter("code_challenge_method", "S256")
                .build()

        session.setAttribute("oauth2-state", createOauth2State(session))

//        Setting redirect link
        response.setHeader("Location", truidSignupUrl.toString())

//        Check if app or browser
        if (xRequestedWith == "XMLHttpRequest") {
            // Return a 202 response in case of an AJAX request
            response.status = HttpServletResponse.SC_ACCEPTED
        } else {
            // Return a 302 response in case of browser redirect
            response.status = HttpServletResponse.SC_FOUND
        }

        session.setAttribute("oauth2-state", createOauth2State(session))
    }

    private fun getInitialPresentation(
        code: String?,
        state: String?,
        error: String?,
        session: HttpSession,
        redirect: String?,
    ): Pair<String?, PresentationResponse?> {
        val userId: String?
        val userInfo: PresentationResponse?

        if (error != null) {
            throw Forbidden(error, "There was an authorization error")
        }
        if (!verifyOauth2State(session, state)) {
            throw Forbidden("access_denied", "State does not match the expected value")
        }

        try {
            val body = LinkedMultiValueMap<String, String>()
            body.add("grant_type", "authorization_code")
            body.add("code", code)
            body.add("redirect_uri", redirect)
            body.add("client_id", clientId)
            body.add("client_secret", clientSecret)
            body.add("code_verifier", getOauth2CodeVerifier(session))

            // Get token
            val tokenResponse =
                restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class)

            // Get user info and set userId in session
            val getPresentationUri =
                URIBuilder(truidPresentationEndpoint)
                    .addParameter("claims", "truid.app/claim/email/v1,truid.app/claim/birthdate/v1")
                    .build()

            // Create entity with header including access token
            val header = HttpHeaders()
            header.contentType = MediaType.APPLICATION_JSON
            header.setBearerAuth(tokenResponse.body!!.accessToken)
            val entity = HttpEntity<String>(header)

            userInfo =
                restTemplate.exchange(
                    getPresentationUri,
                    HttpMethod.GET,
                    entity,
                    PresentationResponse::class.java,
                ).body

            userId = userInfo?.sub.toString()
            session.setAttribute("userId", userId)

            // Persist the refresh token
            persist(tokenResponse.body, userId)
        } catch (e: ForbiddenException) {
            throw Forbidden("access_denied", e.message)
        }

        val pair: Pair<String?, PresentationResponse?> = userId to userInfo

        return pair
    }

    // Removed refreshToken function

    private fun clearPersistence(userId: String?) {
        userTokenDB.deleteUserTokenByUserId(userId)
    }

    private fun persist(
        tokenResponse: TokenResponse?,
        userId: String?,
    ) {
        userTokenDB.deleteUserTokenByUserId(userId)

        userTokenDB.save(
            UserToken(
                null,
                userId,
                tokenResponse?.refreshToken,
                Instant.now(),
            ),
        )
    }

    private fun getPersistedToken(userId: String?): String? {
        val userToken = userTokenDB.getUserTokenByUserId(userId)

        return userToken?.refreshToken
    }
}
