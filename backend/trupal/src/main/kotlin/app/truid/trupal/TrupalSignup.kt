package app.truid.trupal

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponse.*
import jakarta.servlet.http.HttpSession
import jakarta.ws.rs.ForbiddenException
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import java.net.URI
import org.springframework.http.*
import org.springframework.http.MediaType.*
import org.springframework.web.bind.annotation.*
import java.time.Instant


interface MessageRepository : CrudRepository<Message, String>

@Table("messages")
data class Message(@Id var id: String?, val text: String?)


@RestController
class TrupalSignup(

    //    Changed so it loads from a file in .gitignore
    @Value("\${oauth2.clientId}")
    val clientId: String,

    //    Changed so it loads from a file in .gitignore
    @Value("\${oauth2.clientSecret}")
    val clientSecret: String,

    @Value("\${oauth2.redirectUri.signup}")
    val redirectUri: String,

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

    @Value("\${web.peer-to-peer}")
    val peerToPeer: URI,

    val restTemplate: RestTemplate,

    val db: MessageRepository,

    val userTokenDB: UserTokenRepository
) {
    // This variable acts as our persistence in this example
    private var _persistedRefreshToken: String? = null

//    http://localhost:8080/truid/v1/confirm-signup

    @GetMapping("/truid/v1/confirm-signup")
    fun test(
        response: HttpServletResponse,
        request: HttpServletRequest,
//        Value to check for browser or app
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
    ) {
        val session = request.session

        clearPersistence(session.id)

        val truidSignupUrl = URIBuilder(truidSignupEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "truid.app/data-point/email truid.app/data-point/birthdate")
            .addParameter("redirect_uri", redirectUri)
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
            response.status = SC_ACCEPTED
        } else {
            // Return a 302 response in case of browser redirect
            response.status = SC_FOUND
        }

    }

    @GetMapping(
        path = ["/truid/v1/complete-signup"],
    )
    fun completeSignup(
        @RequestParam("code") code: String?,
        @RequestParam("state") state: String?,
        @RequestParam("error") error: String?,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): String? {
        val session = request.session
        val presentation: ResponseEntity<PresentationResponse>

        if (error != null) {
            throw Forbidden(error, "There was an authorization error")
        } else if (!verifyOauth2State(session, state)) {
            throw Forbidden("access_denied", "State does not match the expected value")
        } else {
            try {

                val body = LinkedMultiValueMap<String, String>()
                body.add("grant_type", "authorization_code")
                body.add("code", code)
                body.add("redirect_uri", redirectUri)
                body.add("client_id", clientId)
                body.add("client_secret", clientSecret)
                body.add("code_verifier", getOauth2CodeVerifier(session))


//                Get token
                val tokenResponse =
                    restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class)

                val cookieId = request.session.id

//                Persist the refresh token
                persist(tokenResponse.body, cookieId)
            } catch (e: ForbiddenException) {
                throw Forbidden("access_denied", e.message)
            }

            if (request.session.getAttribute("sessionP2P") != null) {
                val sessionP2P = request.session.getAttribute("sessionP2P") as String
                val redirectP2P = URIBuilder(peerToPeer).addParameter("session", sessionP2P).build()
                response.setHeader("Location", redirectP2P.toString())
                response.status = SC_FOUND
            } else {
                if (request.getHeader(HttpHeaders.ACCEPT).contains(TEXT_HTML_VALUE)) {
                    // Redirect to success page in the webapp flow
                    response.setHeader("Location", webSuccess.toString())
                    response.status = SC_FOUND

                } else {
                    // Return a 200 response in case of an AJAX request
                    response.status = SC_OK
                }
            }



            return null

        }
    }

//    http://localhost:8080/truid/v1/presentation
//    http://localhost:8080/peer-to-peer?session=73399f28-a94b-45bd-aec5-8cbcb4cddfb9

    @GetMapping(
        path = ["/truid/v1/presentation"],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun getPresentation(
        request: HttpServletRequest,
    ): String? {

        val cookieId = request.session.id

//        Get the access token
        val accessToken = refreshToken(cookieId)

        val getPresentationUri = URIBuilder(truidPresentationEndpoint)
            .addParameter("claims", "truid.app/claim/email/v1,truid.app/claim/birthdate/v1")
            .build()

//        Create entity with header including access token
        val header = HttpHeaders()
        header.contentType = APPLICATION_JSON
        header.setBearerAuth(accessToken)
        val entity = HttpEntity<String>(header)

        val presentationResponse =
            restTemplate.exchange(getPresentationUri, HttpMethod.GET, entity, PresentationResponse::class.java)

        return "Email: " + "${presentationResponse.body?.claims?.get(0)?.value}" + " and birthdate: " + "${
            presentationResponse.body?.claims?.get(
                1
            )?.value
        }"
    }

    @ExceptionHandler(Forbidden::class)
    fun handleForbidden(
        e: Forbidden,
        response: HttpServletResponse,
        request: HttpServletRequest,
    ): Map<String, String>? {
        if (request.getHeader(HttpHeaders.ACCEPT).contains(TEXT_HTML_VALUE)) {
            // Redirect to error page in the webapp flow
            response.setHeader("Location", URIBuilder(webFailure).addParameter("error", e.error).build().toString())
            response.status = SC_FOUND

            return null
        } else {
            // Return a 403 response in case of an AJAX request
            response.status = SC_FORBIDDEN

            return mapOf(
                "error" to e.error
            )
        }
    }

    fun refreshToken(cookieId: String?): String {

        val refreshToken = getPersistedToken(cookieId) ?: throw Forbidden("access_denied", "No refresh_token found")

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "refresh_token")
        body.add("refresh_token", refreshToken)
        body.add("client_id", clientId)
        body.add("client_secret", clientSecret)

        val refreshedTokenResponse =
            restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class).body

        persist(refreshedTokenResponse, cookieId)

        if (refreshedTokenResponse == null) {
            throw Forbidden("access_denied", "No token response found")
        } else {
            return refreshedTokenResponse.accessToken
        }

    }

    private fun clearPersistence(cookieId: String?) {
//        _persistedRefreshToken = null

        userTokenDB.deleteUserTokenByCookie(cookieId)
    }

    private fun persist(tokenResponse: TokenResponse?, cookieId: String?) {
//        _persistedRefreshToken = tokenResponse?.refreshToken

        println("kommer in i persist")

        userTokenDB.deleteUserTokenByCookie(cookieId)

        println("kommer in i persist 2")

        userTokenDB.save(
            UserToken(
                null,
                cookieId,
                "test_user_persist",
                tokenResponse?.refreshToken,
                Instant.now()
            )
        )

        println("kommer in i persist 3")
    }

    private fun getPersistedToken(cookieId: String?): String? {

        println("kommer in i getPersistedToken")

        val userToken = userTokenDB.getUserTokenByCookie(cookieId)

        println("kommer in i getPersistedToken 2")
        println("refreshToken: ${userToken?.refreshToken}")

        return userToken?.refreshToken

//        return _persistedRefreshToken
    }

    fun saveToDatabase(message: Message) {
        db.save(message)
    }

}