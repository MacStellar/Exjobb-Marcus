package app.truid.trupal

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponse.*
import jakarta.ws.rs.ForbiddenException
import kotlinx.coroutines.sync.Mutex
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


interface MessageRepository : CrudRepository<Message, String>

@Table("messages")
data class Message(@Id var id: String?, val text: String?)


@RestController
class Login(

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

    val restTemplate: RestTemplate,

    val db: MessageRepository
) {
    // This variable acts as our persistence in this example
    private var _persistedRefreshToken: String? = null
    private val refreshMutex = Mutex()

    @GetMapping("/truid/v1/confirm-signup")
    fun test(
        response: HttpServletResponse,
        request: HttpServletRequest,
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
    ) {
        val session = request.session

        clearPersistence()


        println("kom in på / sidan")
        println("client id: $clientId")
        println("client secret: $clientSecret")

        val truidSignupUrl = URIBuilder(truidSignupEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "truid.app/data-point/email")
            .addParameter("redirect_uri", redirectUri)
            .addParameter("state", createOauth2State(session))
            .addParameter("code_challenge", createOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .build()

        println("truidSignupUrl: $truidSignupUrl")

        println("xRequestedWith: $xRequestedWith")

        response.setHeader("Location", truidSignupUrl.toString())

        println("response getheader: ${response.getHeader("Location")}")



        if (xRequestedWith == "XMLHttpRequest") {
            // Return a 202 response in case of an AJAX request
            response.status = SC_ACCEPTED
        } else {
            // Return a 302 response in case of browser redirect
            response.status = SC_FOUND
        }
    }

    @GetMapping(path=["/truid/v1/complete-signup"],
                produces = [APPLICATION_JSON_VALUE]
    )
    fun completeSignup(
        @RequestParam("code") code: String?,
        @RequestParam("state") state: String?,
        @RequestParam("error") error: String?,
        response: HttpServletResponse,
        request: HttpServletRequest,

    ): String? {
        val session = request.session

        println("kom in på /truid/v1/complete-signup sidan")

        val presentation: ResponseEntity<PresentationResponse>

//        Fixa så att allt ligger i if,else,try grejen sen

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

                val tokenResponse =
                    restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class)


                val getPresentationUri = URIBuilder(truidPresentationEndpoint)
                    .addParameter("claims", "truid.app/claim/email/v1")
                    .build()

                val header = HttpHeaders()
                header.contentType = APPLICATION_JSON
                header.setBearerAuth(tokenResponse.body!!.accessToken)

                val entity = HttpEntity<String>(header)

                println("entity: $entity")

                presentation =
                    restTemplate.exchange(getPresentationUri, HttpMethod.GET, entity, PresentationResponse::class.java)

                println("presentation: ${presentation.body}")
                println("presentation email: ${presentation.body?.claims?.get(0)?.value}")
                println("Fetching presentation: $truidPresentationEndpoint")

                persist(tokenResponse.body)

            } catch (e: ForbiddenException) {
                throw Forbidden("access_denied", e.message)
            }

            if (request.getHeader(HttpHeaders.ACCEPT).contains(TEXT_HTML_VALUE)) {
                // Redirect to success page in the webapp flow
                response.setHeader("Location", webSuccess.toString())
                response.status = SC_FOUND
//            return null
            } else {
                // Return a 200 response in case of an AJAX request
                response.status = SC_OK
//            return null
            }

            val email = presentation.body?.claims?.get(0)?.value
            val emailMessage = Message(id = null, text = email)

            println("emailMessageInJson: $emailMessage")

            //Get the email from the presentation and save it to the database
//            saveToDatabase(emailMessage)

            return presentation.body?.claims?.get(0)?.value


        }
    }

    @GetMapping(
        path = ["/truid/v1/presentation"],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun getPresentation(): String? {
        println("kom in på /signup/success sidan")



        val accessToken = refreshToken()

        println("accessToken: $accessToken")

        val getPresentationUri = URIBuilder(truidPresentationEndpoint)
            .addParameter("claims", "truid.app/claim/email/v1")
            .build()
//
//        println("getPresentationUri: $getPresentationUri")
//
        val header = HttpHeaders()
        header.contentType = APPLICATION_JSON
        header.setBearerAuth(accessToken)

        val entity = HttpEntity<String>(header)

        return restTemplate.exchange(getPresentationUri, HttpMethod.GET, entity, PresentationResponse::class.java).body?.claims?.get(0)?.value
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

    private fun refreshToken(): String {
        // Synchronized, two refreshes with same refresh token
        // invalidates all access tokens and refresh tokens in accordance to
        // https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-15#section-4.12.2

        val refreshToken = getPersistedToken() ?: throw Forbidden("access_denied", "No refresh_token found")

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "refresh_token")
        body.add("refresh_token", refreshToken)
        body.add("client_id", clientId)
        body.add("client_secret", clientSecret)

        val refreshedTokenResponse = restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class).body

//        val refreshedTokenResponse = webClient.post()
//            .uri(URIBuilder(truidTokenEndpoint).build())
//            .contentType(APPLICATION_FORM_URLENCODED)
//            .accept(APPLICATION_JSON)
//            .body(fromFormData(body))
//            .retrieve()
//            .awaitBody<TokenResponse>()



        persist(refreshedTokenResponse)
        return refreshedTokenResponse!!.accessToken

    }

    private fun clearPersistence() {
        _persistedRefreshToken = null
    }

    private fun persist(tokenResponse: TokenResponse?) {
        _persistedRefreshToken = tokenResponse?.refreshToken
    }
    private fun getPersistedToken(): String? {
        return _persistedRefreshToken
    }

    fun saveToDatabase(message: Message) {
        println("Saving to database: $message")
        db.save(message)
    }

}