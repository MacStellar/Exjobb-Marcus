package app.truid.trupal

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
    @Value("\${app.domain}")
    val localDomain: String,
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
    ) {
        val session = request.session

        // Saves a session to the database
        val p2pSession = sessionDB.save(Session(null, SessionStatus.CREATED, Instant.now()))
        session.setAttribute("p2pSessionId", "${p2pSession.id}")

        // Removes the user session if user is already logged in
        val userId = session.getAttribute("userId") as String?
        if (userId != null) {
            clearPersistence(userId)
            session.removeAttribute("userId")
        }

        // Initiates authorization and redirects user to truid signup
        val truidSignupUrl =
            URIBuilder(truidSignupEndpoint)
                .addParameter("response_type", "code")
                .addParameter("client_id", clientId)
                // TODO lägg till portrait image här ifall jag ska använda det
//                .addParameter("scope", "truid.app/data-point/email truid.app/data-point/birthdate truid.app/data-point/portrait-image")
                .addParameter("scope", "truid.app/data-point/email truid.app/data-point/birthdate")
                .addParameter("redirect_uri", createP2PSessionUri)
                .addParameter("state", createOauth2State(session))
                .addParameter("code_challenge", createOauth2CodeChallenge(session))
                .addParameter("code_challenge_method", "S256")
                .build()

        session.setAttribute("oauth2-state", createOauth2State(session))

//        Setting redirect link
        response.setHeader("Location", truidSignupUrl.toString())

        response.status = HttpServletResponse.SC_FOUND

        session.setAttribute("oauth2-state", createOauth2State(session))
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

        if (error != null) {
            throw Forbidden(error, "There was an authorization error")
        }
        if (!verifyOauth2State(session, state)) {
            throw Forbidden("access_denied", "State does not match the expected value")
        }

        val p2pSessionId = session.getAttribute("p2pSessionId") as String? ?: throw CookieSessionNotFound()
        val p2pSession =
            sessionDB.findById(p2pSessionId).orElseThrow {
                P2PSessionNotFound()
            }

        val userId: String?
        val userInfo: PresentationResponse?

        try {
            val body = LinkedMultiValueMap<String, String>()
            body.add("grant_type", "authorization_code")
            body.add("code", code)
            body.add("redirect_uri", createP2PSessionUri)
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

            // TODO
            // Ändra data klasser och struktur på koden för att stödja portrait image
//            val getPresentationUri =
//                URIBuilder(truidPresentationEndpoint)
//                    .addParameter("claims", "truid.app/claim/email/v1,truid.app/claim/birthdate/v1,truid.app/claim/portrait-image/v1")
//                    .build()

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
    ): Map<String, String>? {
        // TODO Configure host/add host in frontend

        val session = request.session

        sessionDB.findById(p2pSessionId).orElseThrow {
            P2PSessionNotFound()
        }

        val userId = session.getAttribute("userId") as String?
        if (userId == null) {
            throw Forbidden("access_denied", "User not logged in.")
        }
        if (!userSessionDB.existsUserSessionsBySessionIdAndUserId(p2pSessionId, userId)) {
            throw Forbidden("access_denied", "Your user has not joined this session.")
        }

        return mapOf("link" to "$localDomain/truid/v1/peer-to-peer/$p2pSessionId/init-join")
    }

    // Second user enters here. Set cookie and redirect to truid
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}/init-join")
    fun joinPeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ) {
        val session = request.session

        // Check if session and userSessions exist for the given p2pSessionId
        val p2pSession =
            sessionDB.findById(p2pSessionId).orElseThrow {
                P2PSessionNotFound()
            }

        val userSessions = userSessionDB.getUserSessionsBySessionId(p2pSessionId)
        if (userSessions.size > 1) {
            throw P2PSessionStatusException("P2P-Session is full", null)
        }

        if (userSessions.isEmpty()) {
            throw P2PSessionStatusException("P2P-Session has not been created", null)
        }
        when (p2pSession.status) {
            SessionStatus.INITIALIZED -> {} // Continue
            SessionStatus.CREATED -> {
                throw P2PSessionStatusException("P2P-session has not been joined by user 1 yet", null)
            }

            SessionStatus.FAILED -> {
                throw P2PSessionStatusException("P2P-Session has failed", null)
            }

            SessionStatus.COMPLETED -> {
                throw P2PSessionStatusException("P2P-Session is full", null)
            }
        }

        session.setAttribute("p2pSessionId", p2pSessionId)

        val userId = session.getAttribute("userId") as String?
        if (userId != null) {
            clearPersistence(userId)
        }

        // Initiates authorization and redirects user to Truid signup
        val truidSignupUrl =
            URIBuilder(truidSignupEndpoint)
                .addParameter("response_type", "code")
                .addParameter("client_id", clientId)
                .addParameter("scope", "truid.app/data-point/email truid.app/data-point/birthdate")
                .addParameter("redirect_uri", joinP2PSessionUri)
                .addParameter("state", createOauth2State(session))
                .addParameter("code_challenge", createOauth2CodeChallenge(session))
                .addParameter("code_challenge_method", "S256")
                .build()

        session.setAttribute("oauth2-state", createOauth2State(session))

//        Setting redirect link
        response.setHeader("Location", truidSignupUrl.toString())

        response.status = HttpServletResponse.SC_FOUND

        session.setAttribute("oauth2-state", createOauth2State(session))
    }

    // Second user redirects here after authorization and completes the p2p
    @GetMapping("/truid/v1/peer-to-peer/join")
    fun completePeerToPeerSession(
        @RequestParam("code") code: String?,
        @RequestParam("state") state: String?,
        @RequestParam("error") error: String?,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ) {
        val session = request.session

        // Skriv ett test för när det inte finns någon session som cookie eller p2pSession i databasen
        // Checks if user has a cookie for p2pSessionId and if the session exists in the database
        val p2pSessionId = session.getAttribute("p2pSessionId") as String? ?: throw CookieSessionNotFound()
        val p2pSession =
            sessionDB.findById(p2pSessionId).orElseThrow {
                P2PSessionNotFound()
            }

        val userId: String?
        val userInfo: PresentationResponse?

        if (error != null) {
            throw Forbidden(error, "There was an authorization error")
        }
        if (!verifyOauth2State(session, state)) {
            throw Forbidden("access_denied", "State does not match the expected value")
        }

        val userSessions = userSessionDB.getUserSessionsBySessionId(p2pSessionId)
        if (userSessions.size > 1) {
            throw P2PSessionStatusException("P2P-Session is full", null)
        }

        if (userSessions.isEmpty()) {
            throw P2PSessionStatusException("P2P-Session has not been created", null)
        }

        when (p2pSession.status) {
            SessionStatus.INITIALIZED -> {} // Continue
            SessionStatus.CREATED -> {
                throw P2PSessionStatusException("P2P-session has not been joined by user 1 yet", null)
            }

            SessionStatus.FAILED -> {
                throw P2PSessionStatusException("P2P-Session has failed", null)
            }

            SessionStatus.COMPLETED -> {
                throw P2PSessionStatusException("P2P-Session is full", null)
            }
        }

        try {
            val body = LinkedMultiValueMap<String, String>()
            body.add("grant_type", "authorization_code")
            body.add("code", code)
            body.add("redirect_uri", joinP2PSessionUri)
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
        response.setHeader("Location", "/truid/v1/peer-to-peer/$p2pSessionId/data")
    }

    // Return state of session (INITIATED, CREATED, COMPLETED, FAILED)
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}")
    fun getStatusOfPeerToPeerSession(
        response: HttpServletResponse,
        request: HttpServletRequest,
        @PathVariable(value = "sessionId") p2pSessionId: String,
    ): Session? {
        val session = request.session

        val p2pSession =
            sessionDB.findById(p2pSessionId).orElseThrow {
                P2PSessionNotFound()
            }

        val userId = session.getAttribute("userId") as String?
        if (userId == null) {
            throw CookieSessionNotFound()
        }
        if (!userSessionDB.existsUserSessionsBySessionIdAndUserId(p2pSessionId, userId)) {
            throw Forbidden("access_denied", "Your user has not joined this session.")
        }

        return p2pSession
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

        val userId = session.getAttribute("userId") as String? ?: throw CookieSessionNotFound()
        val userData: List<UserSession>?

        // The process of checking is user has access to the session is done when
        // they have a user id in the session.
        if (userSessionDB.existsUserSessionsBySessionIdAndUserId(p2pSessionId, userId)) {
            userData = userSessionDB.getUserSessionsBySessionId(p2pSessionId)
            if (userData.isEmpty()) {
                throw UserSessionsNotFound()
            }
        } else {
            throw Forbidden("access_denied", "Your user has not joined this session")
        }

        val userPresentations = mutableListOf<PresentationResponse>()

        for (userSession in userData) {
            userPresentations.add(userSession.userPresentation!!)
        }

        // Returns cookie with session id
        session.id
        response.addCookie(Cookie("JSESSIONID", session.id))

        return userPresentations
    }

    @ExceptionHandler(Forbidden::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleForbidden(
        e: Forbidden,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        return mapOf("ErrorMsg" to "Forbidden, error: ${e.error}, message: ${e.message}")
        // Alternativt led om till en error-sida i frontend
    }

    @ExceptionHandler(P2PSessionStatusException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleP2PSessionStatusException(
        e: P2PSessionStatusException,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        return mapOf("ErrorMsg" to "P2P session error, message: ${e.message}, cause: ${e.cause}")
        // Alternativt led om till en error-sida i frontend
    }

    @ExceptionHandler(CookieSessionNotFound::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleCookieSessionNotFound(
        e: CookieSessionNotFound,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        return mapOf("ErrorMsg" to "Cookie session not found, message: ${e.message}, cause: ${e.cause}")
    }

    @ExceptionHandler(P2PSessionNotFound::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleP2PSessionNotFound(
        e: P2PSessionNotFound,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        return mapOf("ErrorMsg" to "P2P session not found, message: ${e.message}, cause: ${e.cause}")
    }

    @ExceptionHandler(UserSessionsNotFound::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUserSessionsNotFound(
        e: UserSessionsNotFound,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        return mapOf("ErrorMsg" to "User sessions not found, message: ${e.message}, cause: ${e.cause}")
    }

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
}
