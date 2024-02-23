package app.truid.trupal

import com.github.tomakehurst.wiremock.client.WireMock
import org.apache.http.client.utils.URIBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.test.context.ActiveProfiles
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.junit.jupiter.api.*
import java.net.HttpCookie

@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrupalP2PSignup {

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    @Autowired
    lateinit var sessionDB: SessionRepository

    @Autowired
    lateinit var userSessionDB: UserSessionRepository

    lateinit var testP2PSessionId: String


    @Test
    fun `It should return a cookie`() {
        val response = testRestTemplate.exchange(
            RequestEntity.get("/peer-to-peer").build(), String::class.java
        )

//            Checks whether the site redirects user
        assertTrue(302 == response.statusCode.value())

//            Checks whether the cookie is received and is set to httpOnly
        val testCookie = HttpCookie.parse(response.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()).single()
        assertEquals(true, testCookie.isHttpOnly)

//            Removes the temporary test session from the database
        val tempSessionId = response.body!!
        sessionDB.deleteById(tempSessionId)
    }


    @Nested
    inner class User1Flow {
        lateinit var cookie: HttpCookie

        @AfterEach
        fun `Clean up database`() {
            userSessionDB.deleteUserSessionBySessionId(testP2PSessionId)
            sessionDB.deleteById(testP2PSessionId)
        }


        @BeforeEach
        fun `Setup cookie and session ID`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/peer-to-peer").build(), String::class.java
            )

//            Checks whether the cookie is received and is set to httpOnly
            cookie = HttpCookie.parse(response.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()).single()
            testP2PSessionId = response.body!!

            assertEquals(true, cookie.isHttpOnly)
//            Checks whether the site redirects user
            assertTrue(302 == response.statusCode.value())

            println("cookie 1: $cookie")
        }

        @Test
        fun `It should redirect to confirm-signup if no P2P session is provided`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/peer-to-peer").header(HttpHeaders.COOKIE, cookie.toString()).build(),
                String::class.java
            )
            assertEquals(302, response.statusCode.value())
            assertEquals("/truid/v1/confirm-signup", response.headers.location!!.path)

            sessionDB.deleteById(response.body!!)
        }

        @Test
        fun `It should return 404 if invalid P2P session is provided`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/peer-to-peer?session=invalid_session_id")
                    .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
            )
            assertEquals(404, response.statusCode.value())
            assertEquals(
                "Session does not exist", response.body
            )
        }

        @Test
        fun `It should redirect the user to confirm-signup if trying to access existing P2P session before authenticating`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/peer-to-peer?session=$testP2PSessionId")
                    .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
            )
            assertEquals(302, response.statusCode.value())
            assertEquals("/truid/v1/confirm-signup", response.headers.location!!.path)
            assertTrue(sessionDB.existsSessionById(testP2PSessionId))

        }


        @Nested
        inner class User2CompletedSignup {
            lateinit var state: String

            @BeforeEach
            fun `Setup Truid token endpoint mock`() {
                WireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("{\"refresh_token\":\"refresh-token-1\",\"access_token\":\"access-token-1\",\"expires_in\":3600,\"token_type\":\"token-type\",\"scope\":\"truid.app/data-point/email truid.app/data-point/birthdate\"}")
                    )
                )
            }

            @BeforeEach
            fun `Setup Authorization for user 1`() {
                val response = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/confirm-signup")
                        .header(HttpHeaders.COOKIE, cookie.toString())
                        .build(),
                    Void::class.java
                )
                assertEquals(302, response.statusCode.value())
                val url = URIBuilder(response.headers.location!!)
                state = url.queryParams.firstOrNull { it.name == "state" }!!.value
            }

            @BeforeEach
            fun `Mock presentation response from Truid for user 1`() {

//            Creates the response for the mock response from Truid
                val presentationResponse = PresentationResponse(
                    sub = "1234567abcdefg", claims = listOf(
                        PresentationResponseClaims(
                            type = "truid.app/claim/email/v1", value = "user-1@example.com"
                        ), PresentationResponseClaims(
                            type = "truid.app/claim/bithdate/v1", value = "1111-11-11"
                        )
                    )
                )
                val presentationResponseString =
                    wiremock.com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(presentationResponse)
                val presentationResponseJsonNode: wiremock.com.fasterxml.jackson.databind.JsonNode =
                    wiremock.com.fasterxml.jackson.databind.ObjectMapper().readTree(presentationResponseString)

                WireMock.stubFor(
                    WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1")
                        .willReturn(
                            WireMock.aResponse().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withStatus(200).withJsonBody(presentationResponseJsonNode)
                        )
                )
            }

            @BeforeEach
            @Test
            fun `Setup user access peer-to-peer after authentication`() {
                val response = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/complete-signup?code=1234&state=$state")
                        .header(HttpHeaders.COOKIE, cookie.toString()).accept(MediaType.TEXT_HTML).build(),
                    String::class.java
                )

                val url = URIBuilder(response.headers[HttpHeaders.LOCATION]?.firstOrNull())
                val queryParameters = response.headers.location!!.query
                    .split("&")
                    .map { it.split("=") }
                    .associate { it[0] to it[1] }

                assertEquals(302, response.statusCode.value(), "Status code is not 302")
                assertEquals("/peer-to-peer", url.path)
                assertEquals(testP2PSessionId, queryParameters["session"])

                testP2PSessionId = queryParameters["session"]!!
            }

            @BeforeEach
            @Test
            fun `It should return 302, add cookie to database and redirect to the same page`() {
                testRestTemplate.exchange(
                    RequestEntity.get("/peer-to-peer?session=$testP2PSessionId")
                        .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                )

                assertTrue(userSessionDB.existsUserSessionBySessionId(testP2PSessionId))

            }

            @Test
            fun `It should return 200, add cookie to database and redirect to the same page`() {
                val response = testRestTemplate.exchange(
                    RequestEntity.get("/peer-to-peer?session=$testP2PSessionId")
                        .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                )

                assertEquals(200, response.statusCode.value())
                assertEquals("Email: user-1@example.com and birthdate: 1111-11-11", response.body)

            }

            @Nested
            inner class User2Flow {

                @BeforeEach
                fun `Setup cookie for user 2`() {

                    println("cookie 2 before : $cookie")
                    val response = testRestTemplate.exchange(
                        RequestEntity.get("/peer-to-peer?session=$testP2PSessionId").build(), String::class.java
                    )

                    //            Checks whether the cookie is received and is set to httpOnly
                    cookie = HttpCookie.parse(response.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()).single()
                    assertEquals(true, cookie.isHttpOnly)
                    //            Checks whether the site redirects user
                    assertTrue(302 == response.statusCode.value())
                }

                // För att få denna att fungera måste jag ha en databas som håller koll på tokens för olika användare
                @Test
                fun `It should redirect user 2 to confirm-signup if trying to access existing P2P session before authenticating`() {
                    val response = testRestTemplate.exchange(
                        RequestEntity.get("/peer-to-peer?session=$testP2PSessionId")
                            .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                    )
                    assertEquals(302, response.statusCode.value())
                    assertEquals("/truid/v1/confirm-signup", response.headers.location!!.path)
                    assertTrue(sessionDB.existsSessionById(testP2PSessionId))

                }

                @Nested
                inner class User2CompletedSignup {

                    @BeforeEach
                    fun `Setup Truid token endpoint mock`() {
                        WireMock.stubFor(
                            WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                                WireMock.aResponse().withStatus(200)
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withBody("{\"refresh_token\":\"refresh-token-2\",\"access_token\":\"access-token-2\",\"expires_in\":3600,\"token_type\":\"token-type\",\"scope\":\"truid.app/data-point/email truid.app/data-point/birthdate\"}")
                            )
                        )
                    }

                    @BeforeEach
                    fun `Setup Authorization for user 1`() {
                        val response = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/confirm-signup")
                                .header(HttpHeaders.COOKIE, cookie.toString())
                                .build(),
                            Void::class.java
                        )
                        assertEquals(302, response.statusCode.value())
                        val url = URIBuilder(response.headers.location!!)
                        state = url.queryParams.firstOrNull { it.name == "state" }!!.value
                    }

                    @BeforeEach
                    fun `Mock presentation response from Truid for user 1`() {

//            Creates the response for the mock response from Truid
                        val presentationResponse = PresentationResponse(
                            sub = "1234567abcdefh", claims = listOf(
                                PresentationResponseClaims(
                                    type = "truid.app/claim/email/v1", value = "user-2@example.com"
                                ), PresentationResponseClaims(
                                    type = "truid.app/claim/bithdate/v1", value = "2222-22-22"
                                )
                            )
                        )
                        val presentationResponseString =
                            wiremock.com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(presentationResponse)
                        val presentationResponseJsonNode: wiremock.com.fasterxml.jackson.databind.JsonNode =
                            wiremock.com.fasterxml.jackson.databind.ObjectMapper().readTree(presentationResponseString)

                        WireMock.stubFor(
                            WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1")
                                .willReturn(
                                    WireMock.aResponse()
                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                        .withStatus(200).withJsonBody(presentationResponseJsonNode)
                                )
                        )
                    }

                    @BeforeEach
                    @Test
                    fun `Setup user access peer-to-peer after authentication`() {
                        val response = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/complete-signup?code=1234&state=$state")
                                .header(HttpHeaders.COOKIE, cookie.toString()).accept(MediaType.TEXT_HTML).build(),
                            String::class.java
                        )

                        val url = URIBuilder(response.headers[HttpHeaders.LOCATION]?.firstOrNull())
                        val queryParameters = response.headers.location!!.query
                            .split("&")
                            .map { it.split("=") }
                            .associate { it[0] to it[1] }

                        assertEquals(302, response.statusCode.value(), "Status code is not 302")
                        assertEquals("/peer-to-peer", url.path)
                        assertEquals(testP2PSessionId, queryParameters["session"])

                        testP2PSessionId = queryParameters["session"]!!
                    }

                    @BeforeEach
                    @Test
                    fun `It should return 302, add cookie to database and redirect to the same page`() {
                        testRestTemplate.exchange(
                            RequestEntity.get("/peer-to-peer?session=$testP2PSessionId")
                                .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                        )

                        assertTrue(userSessionDB.existsUserSessionBySessionId(testP2PSessionId))

                    }

                    @Test
                    fun `It should return 200, add cookie to database and redirect to the same page`() {
                        val response = testRestTemplate.exchange(
                            RequestEntity.get("/peer-to-peer?session=$testP2PSessionId")
                                .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                        )

                        assertEquals(200, response.statusCode.value())
                        assertEquals(
                            "Email: user-1@example.com and birthdate: 1111-11-11Email: user-2@example.com and birthdate: 2222-22-22",
                            response.body
                        )

                    }
                }


            }
        }

    }


    fun ResponseDefinitionBuilder.withJsonBody(body: Any): ResponseDefinitionBuilder =
        this.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .withBody(ObjectMapper().writeValueAsString(body))

}