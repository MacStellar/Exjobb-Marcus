package app.truid.trupal

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.ws.rs.ForbiddenException
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.*
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

    val map: ObjectMapper,
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
    ): Void? {
        val session = request.session

        val userId = session.getAttribute("userId") as String?
        if (userId != null) {
            clearPersistence(userId)
        }

        // Saves a session to the database
        val p2pSession = sessionDB.save(Session(null, "CREATED", Instant.now()))
        session.setAttribute("p2pSessionId", "${p2pSession.id}")

        val truidSignupUrl = URIBuilder(truidSignupEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "truid.app/data-point/email truid.app/data-point/birthdate")
            .addParameter("redirect_uri", createP2PSessionUri)
            .addParameter("state", createOauth2State(session))
            .addParameter("code_challenge", createOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .build()

        request.session.setAttribute("oauth2-state", createOauth2State(session))

//        Setting redirect link
        response.setHeader("Location", truidSignupUrl.toString())

//        Check if app or browser
        if (xRequestedWith == "XMLHttpRequest") {
            // Return a 202 response in case of an AJAX request
            response.status = HttpServletResponse.SC_ACCEPTED
        } else {
            // Return a 302 response in case of browser redirect
            // Eventuellt gör om till 303
            response.status = HttpServletResponse.SC_FOUND
        }
        return null
    }

    // Checks if signup went well
    // Get token and fetch presentation
    // Creates a session and sets id in http session
    // User redirects here after authorization at truid
    // Fetch presentation and return link that user 1 can share
    @GetMapping("/truid/v1/peer-to-peer/create")
    fun createPeerToPeerSession(
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
        val p2pSession = sessionDB.findById(p2pSessionId).orElseThrow() {
            P2PSessionNotFound()
        }

        if (error != null) {
            throw Forbidden(error, "There was an authorization error")
        }
        if (!verifyOauth2State(session, state)) {
            throw Forbidden("access_denied", "State does not match the expected value")
        }

        val userOneInfo: PresentationResponse?
        val userOneId: String?
        try {

            val body = LinkedMultiValueMap<String, String>()
            body.add("grant_type", "authorization_code")
            body.add("code", code)
            body.add("redirect_uri", createP2PSessionUri)
            body.add("client_id", clientId)
            body.add("client_secret", clientSecret)
            body.add("code_verifier", getOauth2CodeVerifier(session))

            //Get token
            val tokenResponse =
                restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class)

            // Get user info and set userId in session
            val getPresentationUri = URIBuilder(truidPresentationEndpoint)
                .addParameter("claims", "truid.app/claim/email/v1,truid.app/claim/birthdate/v1")
                .build()

            //Create entity with header including access token
            val header = HttpHeaders()
            header.contentType = MediaType.APPLICATION_JSON
            header.setBearerAuth(tokenResponse.body!!.accessToken)
            val entity = HttpEntity<String>(header)

            userOneInfo = restTemplate.exchange(
                getPresentationUri,
                HttpMethod.GET,
                entity,
                PresentationResponse::class.java
            ).body

            userOneId = userOneInfo?.sub.toString()
            session.setAttribute("userId", userOneId)

            //Persist the refresh token
            persist(tokenResponse.body, userOneId)
        } catch (e: ForbiddenException) {
            throw Forbidden("access_denied", e.message)
        }

        // Set status of session to INITIATED
        p2pSession.status = "INITIATED"
        sessionDB.save(p2pSession)

        println("userOneInfo: $userOneInfo")

        // Saves a user session to the database
        userSessionDB.save(
            UserSession(
                null,
                p2pSessionId,
                userOneId,
                userOneInfo,
                Instant.now()
            )
        )

        response.status = 302
        response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/${p2pSessionId}/share")

        return null
    }

    // Inkludera polling på denna sidan
    // Ha så att p2pSessionID sparas i session istället?
    // First user is redirected here after creating and joining the p2p session.
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}/share")
    fun sharePeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
        response: HttpServletResponse,
        request: HttpServletRequest,
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
    ): String? {


        return "http://localhost:8080/truid/v1/peer-to-peer/${p2pSessionId}/init-join </br> http://localhost:8080/truid/v1/peer-to-peer/${p2pSessionId}/data"
    }


    // Second user enters here. Set cookie and redirect to truid
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}/init-join")
    fun joinPeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
        response: HttpServletResponse,
        request: HttpServletRequest,
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
    ): Void? {
        // Check if session and userSessions exist for the given p2pSessionId
        sessionDB.findById(p2pSessionId).orElseThrow() {
            P2PSessionNotFound()
        }
        val userSessions = userSessionDB.getUserSessionsBySessionId(p2pSessionId)

        request.session.setAttribute("p2pSessionId", p2pSessionId)

        val session = request.session
        clearPersistence(session.id)

        val truidSignupUrl = URIBuilder(truidSignupEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "truid.app/data-point/email truid.app/data-point/birthdate")
            .addParameter("redirect_uri", joinP2PSessionUri)
            .addParameter("state", createOauth2State(session))
            .addParameter("code_challenge", createOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .build()

        request.session.setAttribute("oauth2-state", createOauth2State(session))

//        Setting redirect link
        response.setHeader("Location", truidSignupUrl.toString())

//        Check if app or browser
        if (xRequestedWith == "XMLHttpRequest") {
            // Return a 202 response in case of an AJAX request
            response.status = HttpServletResponse.SC_ACCEPTED
        } else {
            // Return a 302 response in case of browser redirect
            // Eventuellt gör om till 303
            response.status = HttpServletResponse.SC_FOUND
        }

        return null
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
        val p2pSession = sessionDB.findById(p2pSessionId).orElseThrow() {
            P2PSessionNotFound()
        }
        val userSessions =
            userSessionDB.getUserSessionsBySessionId(p2pSessionId) as List<UserSession>? ?: throw UserSessionsNotFound()

        if (p2pSession.status.equals("INITIALIZED")) {
            throw RuntimeException("Session has no other member")
        }


        if (error != null) {
            throw Forbidden(error, "There was an authorization error")
        }
        if (!verifyOauth2State(session, state)) {
            throw Forbidden("access_denied", "State does not match the expected value")
        }

        val userTwoInfo: PresentationResponse?
        val userTwoId: String?
        try {

            val body = LinkedMultiValueMap<String, String>()
            body.add("grant_type", "authorization_code")
            body.add("code", code)
            body.add("redirect_uri", joinP2PSessionUri)
            body.add("client_id", clientId)
            body.add("client_secret", clientSecret)
            body.add("code_verifier", getOauth2CodeVerifier(session))

            //Get token
            val tokenResponse =
                restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class)

            // Get user info and set userId in session
            val getPresentationUri = URIBuilder(truidPresentationEndpoint)
                .addParameter("claims", "truid.app/claim/email/v1,truid.app/claim/birthdate/v1")
                .build()

            //Create entity with header including access token
            val header = HttpHeaders()
            header.contentType = MediaType.APPLICATION_JSON
            header.setBearerAuth(tokenResponse.body!!.accessToken)
            val entity = HttpEntity<String>(header)

            userTwoInfo = restTemplate.exchange(
                getPresentationUri,
                HttpMethod.GET,
                entity,
                PresentationResponse::class.java
            ).body

            userTwoId = userTwoInfo?.sub.toString()
            session.setAttribute("userId", userTwoId)

            //Persist the refresh token
            persist(tokenResponse.body, userTwoId)
        } catch (e: ForbiddenException) {
            throw Forbidden("access_denied", e.message)
        }

        if (userSessionDB.existsUserSessionsBySessionIdAndUserId(p2pSessionId, request.session.id)) {
            response.status = 302
            response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/${p2pSessionId}/data")
        } else if (p2pSession.status.equals("COMPLETED")) {
            throw SessionAlreadyComplete()
        }

        userSessionDB.save(
            UserSession(
                null,
                p2pSessionId,
                userTwoId,
                userTwoInfo,
                Instant.now()
            )
        )

        // Set status of session to COMPLETED
        p2pSession.status = "COMPLETED"
        sessionDB.save(p2pSession)

        response.status = 302
        response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/${p2pSessionId}/data")

        return null
    }

    // Return state of session (INITIATED, CREATED, COMPLETED, FAILED)
    @GetMapping("/truid/v1/peer-to-peer/{sessionId}")
    fun getStatusOfPeerToPeerSession(
        @PathVariable(value = "sessionId") p2pSessionId: String,
    ): String? {
        sessionDB.findById(p2pSessionId).orElseThrow() {
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
        request: HttpServletRequest
    ): MutableList<PresentationResponse>? {
        // Get PPID from server side session connected to cookie
        // Check if PPID is connected to any of the userSessions connected to the session
        // Return data from both users if TRUE
        val session = request.session

        sessionDB.findById(p2pSessionId).orElseThrow() {
            P2PSessionNotFound()
        }

        if (request.session.getAttribute("userId") == null) {
            response.status = 302
            response.setHeader("Location", "http://localhost:8080/truid/v1/peer-to-peer/${p2pSessionId}/init-join")
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
                    "http://localhost:8080/truid/v1/peer-to-peer/${p2pSessionId}/init-join"
                )

                return null
            } else {
                throw Forbidden("access_denied", "User not part of session")
            }
        }

        println("userData: ${userData[0].userPresentation}")

        val userPresentations = mutableListOf<PresentationResponse>()

        for (userSession in userData) {
            userPresentations.add(userSession.userPresentation!!)
        }

        // For some reason the session cookie is not returned to the user browser in the response unless i add this line
        // It seems like this is because the return is a string (and not a redirect or something)
        // The session id is probably automatically added to the header when the request.session.id is called
        request.session.id
        response.addCookie(Cookie("JSESSIONID", request.session.id))

        return userPresentations
    }

    @ExceptionHandler(Forbidden::class)
    fun handleForbidden(
        e: Forbidden,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        if (request.getHeader(HttpHeaders.ACCEPT).contains(MediaType.TEXT_HTML_VALUE)) {
            // Redirect to error page in the webapp flow
            response.setHeader("Location", URIBuilder(webFailure).addParameter("error", e.error).build().toString())
            response.status = HttpServletResponse.SC_FOUND

            return null
        } else {
            // Return a 403 response in case of an AJAX request
            response.status = HttpServletResponse.SC_FORBIDDEN

            return mapOf(
                "error" to e.error
            )
        }
    }

    private fun getPresentation(userId: String): PresentationResponse? {

        //Get the access token
        val accessToken = refreshToken(userId)

        val getPresentationUri = URIBuilder(truidPresentationEndpoint)
            .addParameter("claims", "truid.app/claim/email/v1,truid.app/claim/birthdate/v1")
            .build()

        //Create entity with header including access token
        val header = HttpHeaders()
        header.contentType = MediaType.APPLICATION_JSON
        header.setBearerAuth(accessToken)
        val entity = HttpEntity<String>(header)

        return restTemplate.exchange(
            getPresentationUri,
            HttpMethod.GET,
            entity,
            PresentationResponse::class.java
        ).body
    }

    private fun refreshToken(userId: String?): String {

        val refreshToken = getPersistedToken(userId) ?: throw Forbidden("access_denied", "No refresh_token found")

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "refresh_token")
        body.add("refresh_token", refreshToken)
        body.add("client_id", clientId)
        body.add("client_secret", clientSecret)

        val refreshedTokenResponse =
            restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class).body

        persist(refreshedTokenResponse, userId)

        if (refreshedTokenResponse == null) {
            throw Forbidden("access_denied", "No token response found")
        } else {
            return refreshedTokenResponse.accessToken
        }

    }

    private fun clearPersistence(userId: String?) {

        userTokenDB.deleteUserTokenByUserId(userId)
    }

    private fun persist(tokenResponse: TokenResponse?, userId: String?) {
        userTokenDB.deleteUserTokenByUserId(userId)

        userTokenDB.save(
            UserToken(
                null,
                userId,
                tokenResponse?.refreshToken,
                Instant.now()
            )
        )
    }

    private fun getPersistedToken(userId: String?): String? {
        val userToken = userTokenDB.getUserTokenByUserId(userId)

        return userToken?.refreshToken
    }
}
