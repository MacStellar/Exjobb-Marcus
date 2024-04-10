package app.truid.trupal

import wiremock.com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType.*
import org.springframework.http.RequestEntity
import java.net.HttpCookie
import org.apache.http.client.utils.URIBuilder
import wiremock.com.fasterxml.jackson.databind.JsonNode

@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class TrupalSignupTest {

    @Value("\${oauth2.truid.signup-endpoint}")
    lateinit var truidSignupEndpoint: String

    @Value("\${oauth2.truid.token-endpoint}")
    lateinit var truidTokenEndpoint: String

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate


    @Nested
    inner class ConfirmSignup {

        @Test
        fun `It should redirect the user to Truid with all parameters set`() {
//        Checks whether the endpoint is redirected
            val response = testRestTemplate.getForEntity<Void>("/truid/v1/confirm-signup")
            assertEquals(302, response.statusCode.value(), "Status code is not 302")


//        Checks whether the location header for the redirect is set correctly
            val location = response.headers.location!!
            assertEquals("/oauth2/v1/authorize/confirm-signup", location.path)

//        Checks whether the state query parameters are set correctly
            val queryParameters = location.query
                .split("&")
                .map { it.split("=") }
                .associate { it[0] to it[1] }
            assertNotNull(queryParameters["state"], "State query parameter is not set")
            assertNotNull(queryParameters["response_type"], "Response type query parameter is not set")
            assertNotNull(queryParameters["client_id"], "Client id query parameter is not set")
            assertNotNull(queryParameters["scope"], "Scope query parameter is not set")
            assertNotNull(queryParameters["redirect_uri"], "Redirect uri query parameter is not set")
            assertNotNull(queryParameters["code_challenge"], "Code challenge query parameter is not set")
            assertNotNull(queryParameters["code_challenge_method"], "Code challenge method query parameter is not set")
            assertNull(queryParameters["client_secret"], "Query parameter is set. This should not be.")
            assertNull(queryParameters["code_verifier"], "Code_verifier parameter is set. This should not be.")
            assertNull(queryParameters["grant_type"], "Grant type is set. This should not be.")
        }


        @Test
        fun `It should return a cookie containing the session ID`() {
            val res = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java
            )

//            Checks whether the site redirects user
            assertEquals(302, res.statusCode.value())

            println("res.session.get")

//            Checks whether the cookie is received and is set to httpOnly
            val cookie = HttpCookie.parse(res.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()).single()
            assertEquals(true, cookie.isHttpOnly)
        }

        @Test
        fun `It should get a 200 response from Truid`() {
//            Mock the Truid endpoint (this usually returns a 302 with a redirect containing the code and state)
            WireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo("/oauth2/v1/authorize/confirm-signup"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"code\": \"1234\", \"state\": \"abcd\", \"error\": null}")
                    )
            )

            val response = testRestTemplate.getForEntity<Void>(truidSignupEndpoint)

            assertEquals(200, response.statusCode.value(), "Status code is not 200")
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/oauth2/v1/authorize/confirm-signup")))
        }
    }

    @Nested
    inner class CompleteSignup {
        lateinit var state: String
        lateinit var cookie: HttpCookie

        @BeforeEach
        fun `Setup Truid token endpoint mock`() {
            WireMock.stubFor(
                WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                            .withBody("{\"refresh_token\":\"refresh-token\",\"access_token\":\"access-token\",\"expires_in\":3600,\"token_type\":\"token-type\",\"scope\":\"truid.app/data-point/email truid.app/data-point/birthdate\"}")
                    )
            )
        }

        @BeforeEach
        fun `Setup Authorization`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java
            )
            assertEquals(302, response.statusCode.value())
            cookie = HttpCookie.parse(response.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()).single()
            val url = URIBuilder(response.headers.location!!)
            state = url.queryParams.firstOrNull { it.name == "state" }!!.value
        }

        @Test
        fun `Complete signup should return 200`() {

//            Creates the response for the mock response from Truid
            val presentationResponse = PresentationResponse(
                sub = "1234567abcdefg",
                claims = listOf(
                    PresentationResponseClaims(
                        type = "truid.app/claim/email/v1",
                        value = "email@example.com"
                    ),
                    PresentationResponseClaims(
                        type = "truid.app/claim/bithdate/v1",
                        value = "1982-11-23"
                    )
                )
            )
            val presentationResponseString = ObjectMapper().writeValueAsString(presentationResponse)
            val presentationResponseJsonNode: JsonNode = ObjectMapper().readTree(presentationResponseString)

//            The mock response from Truid
            WireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1"))
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                            .withStatus(200)
                            .withJsonBody(
                                presentationResponseJsonNode
                            )
                    )
            )

//            Calls the client backend endpoint and gets truid mock response in return
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/complete-signup?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Void::class.java
            )


//            Checks if the response is 200
            assertEquals(200, response.statusCode.value(), "Status code is not 200")
        }

        @Test
        fun `If parameter error is not null, return access_denied`() {
//            Calls the client backend endpoint and gets truid mock response in return
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/complete-signup?error=access_denied")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )

//            Checks if the reponse is HTTP_OK (200)
            assertEquals(403, response.statusCode.value(), "Status code is not 403")

        }

        @Test
        fun `If state is wrong, return access_denied`() {
//            Calls the client backend endpoint and gets truid mock response in return
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/complete-signup?code=1234&state=wrong_state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )

            assertEquals(403, response.statusCode.value(), "Status code is not 403")

        }

        @Test
        fun `It should get a access token back from Truid`() {
            WireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo("/oauth2/v1/token"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"refresh_token\":\"refresh-token\",\"access_token\":\"access-token\",\"expires_in\":3600,\"token_type\":\"token-type\",\"scope\":\"scope\"}")
                    )
            )


            val response = testRestTemplate.getForEntity<TokenResponse>(truidTokenEndpoint)

            assertEquals("access-token", response.body!!.accessToken)
            assertEquals(200, response.statusCode.value(), "Status code is not 302")
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/oauth2/v1/token")))
        }


    }

    @Nested
    inner class CompleteSignupWebFlow {
        private lateinit var state: String
        private lateinit var cookie: HttpCookie

        @BeforeEach
        fun `Setup Truid token endpoint mock`() {
            WireMock.stubFor(
                WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                            .withBody("{\"refresh_token\":\"refresh-token\",\"access_token\":\"access-token\",\"expires_in\":3600,\"token_type\":\"token-type\",\"scope\":\"truid.app/data-point/email\"}")
                    )
            )
        }

        @BeforeEach
        fun `Setup Authorization`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java
            )
            assertEquals(302, response.statusCode.value())
            cookie = HttpCookie.parse(response.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()).single()
            val url = URIBuilder(response.headers.location!!)
            state = url.queryParams.firstOrNull { it.name == "state" }!!.value
        }

        @BeforeEach
        fun `Mock presentation response from Truid`() {

//            Creates the response for the mock response from Truid
            val presentationResponse = PresentationResponse(
                sub = "1234567abcdefg",
                claims = listOf(
                    PresentationResponseClaims(
                        type = "truid.app/claim/email/v1",
                        value = "email@example.com"
                    ),
                    PresentationResponseClaims(
                        type = "truid.app/claim/bithdate/v1",
                        value = "1982-11-23"
                    )
                )
            )
            val presentationResponseString = ObjectMapper().writeValueAsString(presentationResponse)
            val presentationResponseJsonNode: JsonNode = ObjectMapper().readTree(presentationResponseString)

            WireMock.stubFor(
                WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1").willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(presentationResponseJsonNode)
                )
            )
        }

        @Test
        fun `Complete signup should return 302`() {
//
////            Creates the response for the mock response from Truid
//            val presentationResponse = PresentationResponse(
//                sub = "1234567abcdefg",
//                claims = listOf(
//                    PresentationResponseClaims(
//                        type = "truid.app/claim/email/v1",
//                        value = "email@example.com"
//                    )
//                )
//            )
//            val presentationResponseString = ObjectMapper().writeValueAsString(presentationResponse)
//            val presentationResponseJsonNode: JsonNode = ObjectMapper().readTree(presentationResponseString)
//
////            The mock response from Truid
//            WireMock.stubFor(
//                WireMock.get(WireMock.urlEqualTo("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1")).willReturn(
//                    WireMock.aResponse()
//                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                        .withStatus(200)
//                        .withJsonBody(
//                            presentationResponseJsonNode
//                        )
//                )
//            )

//            Calls the client backend endpoint and gets truid mock response in return
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/complete-signup?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(TEXT_HTML)
                    .build(),
                Void::class.java
            )

//            Checks if the response is 302 and goes to the presentation page
            val url = URIBuilder(response.headers[HttpHeaders.LOCATION]?.firstOrNull())
            assertEquals(302, response.statusCode.value(), "Status code is not 200")
            assertEquals("/truid/v1/presentation", url.path)
        }

        @Test
        fun `If parameter error is not null, return access_denied`() {
//            Calls the client backend endpoint and gets truid mock response in return
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/complete-signup?error=access_denied")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(TEXT_HTML)
                    .build(),
                Void::class.java
            )

//            Checks if the reponse is 302 and goes to the failure page
            assertEquals(302, response.statusCode.value(), "Status code is not 302")
            val url = URIBuilder(response.headers[HttpHeaders.LOCATION]?.firstOrNull())
            assertEquals("/signup/failure.html", url.path)
            assertEquals("access_denied", url.queryParams.firstOrNull { it.name == "error" }?.value)
        }

        @Test
        fun `If state is wrong, return access_denied`() {
//            Calls the client backend endpoint and gets truid mock response in return
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/complete-signup?code=1234&state=wrong_state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(TEXT_HTML)
                    .build(),
                Void::class.java
            )

            assertEquals(302, response.statusCode.value(), "Status code is not 403")
            val url = URIBuilder(response.headers[HttpHeaders.LOCATION]?.firstOrNull())
            assertEquals("/signup/failure.html", url.path)
            assertEquals("access_denied", url.queryParams.firstOrNull { it.name == "error" }?.value)
        }

        @Test
        fun `It should get a access token back from Truid`() {
            WireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo("/oauth2/v1/token"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"refresh_token\":\"refresh-token\",\"access_token\":\"access-token\",\"expires_in\":3600,\"token_type\":\"token-type\",\"scope\":\"scope\"}")
                    )
            )


            val response = testRestTemplate.getForEntity<TokenResponse>(truidTokenEndpoint)

            assertEquals("access-token", response.body!!.accessToken)
            assertEquals(200, response.statusCode.value(), "Status code is not 302")
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/oauth2/v1/token")))
        }


        @Nested
        inner class WithPersistedTokens {


            @BeforeEach
            fun `Complete login`() {
//
////            Creates the response for the mock response from Truid
//                val presentationResponse = PresentationResponse(
//                    sub = "1234567abcdefg",
//                    claims = listOf(
//                        PresentationResponseClaims(
//                            type = "truid.app/claim/email/v1",
//                            value = "email@example.com"
//                        )
//                    )
//                )
//                val presentationResponseString = ObjectMapper().writeValueAsString(presentationResponse)
//                val presentationResponseJsonNode: JsonNode = ObjectMapper().readTree(presentationResponseString)
//
//                WireMock.stubFor(
//                    WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1").willReturn(
//                        WireMock.aResponse()
//                            .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                            .withStatus(200)
//                            .withJsonBody(presentationResponseJsonNode)
//                    )
//                )



                val response = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/complete-signup?code=1234&state=$state")
                        .header(HttpHeaders.COOKIE, cookie.toString())
                        .accept(TEXT_HTML)
                        .build(),
                    Void::class.java
                )

                val url = URIBuilder(response.headers[HttpHeaders.LOCATION]?.firstOrNull())
                assertEquals(302, response.statusCode.value(), "Status code is not 302")
                assertEquals("/truid/v1/presentation", url.path)

                println("hela before each körs klart med assertions giltiga")
            }

            @Test
            fun `It should get presentation data with the access token`() {

                val response = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1")
                        .header(HttpHeaders.COOKIE, cookie.toString())
//                        .accept(TEXT_HTML)
                        .build(),
                    String::class.java
                )

                assertEquals(200, response.statusCode.value(), "Status code is not 200")
                assertEquals("Email: email@example.com and birthdate: 1982-11-23", response.body)

            }
        }

    }

}

