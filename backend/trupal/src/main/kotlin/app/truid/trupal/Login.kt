package app.truid.trupal

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.shaded.gson.JsonObject
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponse.*
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import java.net.URI
import org.springframework.http.*
import org.springframework.http.MediaType.*


interface MessageRepository : CrudRepository<Message, String>

@Table("messages")
data class Message(@Id var id: String?, val text: String?)


@RestController
class Login(

    //    Changed so it loads from a file in .gitignore
    @Value("\${truid.clientId}")
    val clientId: String,

    //    Changed so it loads from a file in .gitignore
    @Value("\${truid.clientSecret}")
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

    @GetMapping("/login")
    fun test(
        response: HttpServletResponse,
        request: HttpServletRequest,
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
//             exchange: ServerWebExchange
    ) {
//        val session = exchange.session.awaitSingle()
        val session = request.session

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


//        response.sendRedirect("http://www.google.com")
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
//        val session = exchange.session.awaitSingle()
        val session = request.session

//        Fixa så att allt ligger i if,else,try grejen sen

        println("code: $code")
        println("state: $state")
        println("error: $error")

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "authorization_code")
        body.add("code", code)
        body.add("redirect_uri", redirectUri)
        body.add("client_id", clientId)
        body.add("client_secret", clientSecret)
        body.add("code_verifier", getOauth2CodeVerifier(session))

        val tokenResponse = restTemplate.postForEntity<TokenResponse>(truidTokenEndpoint, body, TokenResponse::class)

        println("tokenResponse: ${tokenResponse.body}")

        val getPresentationUri = URIBuilder(truidPresentationEndpoint)
            .addParameter("claims", "truid.app/claim/email/v1")
            .build()


//        val presentation = restTemplate.getForEntity(getPresentationUri.toString(), PresentationResponse::class.java)

        val header = HttpHeaders()
        header.contentType = APPLICATION_JSON
        header.setBearerAuth(tokenResponse.body!!.accessToken)

        val entity = HttpEntity<String>(header)

        println("entity: $entity")

        val presentation = restTemplate.exchange(getPresentationUri, HttpMethod.GET, entity, PresentationResponse::class.java)

        println("presentation: ${presentation.body}")
        println("presentation email: ${presentation.body?.claims?.get(0)?.value}")

        println("Fetching presentation: $truidPresentationEndpoint")

        if (request.getHeader(HttpHeaders.ACCEPT).contains(TEXT_HTML_VALUE)) {
            // Redirect to success page in the webapp flow
//            response.setHeader("Location", webSuccess.toString())
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
        saveToDatabase(emailMessage)

        return presentation.body?.claims?.get(0)?.value







//        if (error != null) {
//            throw Forbidden(error, "There was an authorization error")
//        } else if (!verifyOauth2State(session, state)) {
//            throw Forbidden("access_denied", "State does not match the expected value")
//        } else {
//            try {
//                val body = LinkedMultiValueMap<String, String>()
//                body.add("grant_type", "authorization_code")
//                body.add("code", code)
//                body.add("redirect_uri", redirectUri)
//                body.add("client_id", clientId)
//                body.add("client_secret", clientSecret)
//                body.add("code_verifier", getOauth2CodeVerifier(session))
//
//                println("body: $body")
//
//                println("Posting code to: $truidTokenEndpoint")
//                // Exchange code for access token and refresh token
//
////              Grej som kommer från webflux reactive:
//                val tokenResponse = webClient.post()
////                    ----------
//                    .uri(URIBuilder(truidTokenEndpoint).build())
//                    .contentType(APPLICATION_FORM_URLENCODED)
//                    .accept(APPLICATION_JSON)
////                    Grej som kommer från webflux reactive:
//                    .body(fromFormData(body))
////                    ----------
//                    .retrieve()
//                    .awaitBody<TokenResponse>()
//
//                println("tokenResponse: $tokenResponse")
//
//                println("Fetching presentation: $truidPresentationEndpoint")
//                // Get and print user email from Truid
//                val getPresentationUri = URIBuilder(truidPresentationEndpoint)
//                    .addParameter("claims", "truid.app/claim/email/v1")
//                    .build()
//
//                val presentation = webClient
//                    .get()
//                    .uri(getPresentationUri)
//                    .accept(APPLICATION_JSON)
//                    .headers { it.setBearerAuth(tokenResponse.accessToken) }
//                    .retrieve()
//                    .awaitBody<PresentationResponse>()
//
//                println(presentation)
//
//                // Persist token, so it can be accessed via GET "/truid/v1/presentation"
//                // See getAccessToken for an example of refreshing access token
//                persist(tokenResponse)
//            } catch (e: WebClientResponseException.Forbidden) {
//                throw Forbidden("access_denied", e.message)
//            }
//        }
//
//        if (exchange.request.headers.accept.contains(TEXT_HTML)) {
//            // Redirect to success page in the webapp flow
//            exchange.response.headers.location = webSuccess
//            exchange.response.statusCode = FOUND
//            return null
//        } else {
//            // Return a 200 response in case of an AJAX request
//            exchange.response.statusCode = OK
//            return null
//        }
    }

    @GetMapping(
        path = ["/signup/success/"]
    )
    fun getPresentation(): Void? {
        println("kom in på /signup/success sidan")

        return null

//        val accessToken = refreshToken()
//
//        println("accessToken: $accessToken")
//
//        val getPresentationUri = URIBuilder(truidPresentationEndpoint)
//            .addParameter("claims", "truid.app/claim/email/v1")
//            .build()
//
//        println("getPresentationUri: $getPresentationUri")
//
//        return webClient
//            .get()
//            .uri(getPresentationUri)
//            .accept(APPLICATION_JSON)
//            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
//            .retrieve()
//            .awaitBody()


    }

    fun saveToDatabase(message: Message) {
        println("Sparar till databasen")
        db.save(message)
    }

}