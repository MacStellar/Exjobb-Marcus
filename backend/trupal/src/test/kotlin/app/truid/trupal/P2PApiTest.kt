package app.truid.trupal

import com.fasterxml.jackson.databind.JsonNode
import com.github.tomakehurst.wiremock.client.WireMock
import org.apache.http.client.utils.URIBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotEquals
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
import org.springframework.http.ResponseEntity
import org.testcontainers.shaded.com.fasterxml.jackson.databind.node.ArrayNode
import java.net.HttpCookie

@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class P2PApiTest {

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    lateinit var testP2PSessionId: String

    @Test
    fun `It should return a cookie`() {
        val response = testRestTemplate.exchange(
            RequestEntity.get("/truid/v1/peer-to-peer").build(), String::class.java
        )

//            Checks whether the site redirects user
        assertEquals(302, response.statusCode.value())

//            Checks whether the cookie is received and is set to httpOnly
        val testCookie = HttpCookie.parse(response.setCookie()).single()
        assertEquals(true, testCookie.isHttpOnly)
    }

    @Test
    fun `should not be able to get p2p session without truid auth`() {
        val response = testRestTemplate.exchange(
            RequestEntity.get("/peer-to-peer/unknown_id").build(), String::class.java
        )

        assertEquals(404, response.statusCode.value())
    }

    @Nested
    inner class User1Flow {
        lateinit var cookie: HttpCookie

        @BeforeEach
        fun `Setup cookie and session ID`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/peer-to-peer").build(), String::class.java
            )

//            Checks whether the cookie is received and is set to httpOnly
            cookie = HttpCookie.parse(response.setCookie()).single()

            assertEquals(true, cookie.isHttpOnly)
            assertEquals(302, response.statusCode.value())
        }

        @Test
        fun `It should redirect to Truid authorization endpoint`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/peer-to-peer")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                String::class.java
            )
            assertEquals(302, response.statusCode.value())

            val url = URIBuilder(response.headers.location!!)
            assertEquals("https", url.scheme)
            assertEquals("api.truid.app", url.host)
            assertEquals("/oauth2/v1/authorize/confirm-signup", url.path)
            assertEquals("code", url.getParam("response_type"))
            assertEquals("test-client-id", url.getParam("client_id"))
            Assertions.assertNotNull(url.getParam("scope"))
        }

        @Test
        fun `It should not add client_secret to the returned URL`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/peer-to-peer")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                String::class.java
            )
            assertEquals(302, response.statusCode.value())

            val url = URIBuilder(response.location())
            Assertions.assertNull(url.getParam("client_secret"))
        }

        @Test
        fun `It should return 404 if invalid P2P session is provided`() {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/truid/v1/peer-to-peer/invalid_session_id")
                    .header(HttpHeaders.COOKIE, cookie.toString()).build(),
                String::class.java
            )
            assertEquals(404, response.statusCode.value())
        }

        @Nested
        inner class User1CompletedSignup {
            lateinit var state: String

            @BeforeEach
            fun `Setup Truid token endpoint mock`() {
                val response =
                    mapOf(
                        "refresh_token" to "refresh-token-1",
                        "access_token" to "access-token-1",
                        "expires_in" to 3600,
                        "token_type" to "token-type",
                        "scope" to "truid.app/data-point/email truid.app/data-point/birthdate",
                    )
                WireMock.stubFor(
                    WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withJsonBody(response)
                    )
                )
            }

            @BeforeEach
            fun `Setup Authorization for user 1`() {

                val responseOne = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer")
                        .header(HttpHeaders.COOKIE, cookie.toString())
                        .build(),
                    Void::class.java
                )
                assertEquals(302, responseOne.statusCode.value())
                val urlOne = URIBuilder(responseOne.headers.location!!)
                state = urlOne.getParam("state")!!

                val responseTwo = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/create?code=1234&state=$state")
                        .header(HttpHeaders.COOKIE, cookie.toString())
                        .accept(MediaType.TEXT_HTML)
                        .build(),
                    String::class.java
                )

//                val urlTwo = URIBuilder(responseTwo.headers[HttpHeaders.LOCATION]?.firstOrNull())
                val pathSegments = responseTwo.headers.location!!.path
                    .split("/")
                testP2PSessionId = pathSegments[pathSegments.size - 2]
            }

            @BeforeEach
            fun `Mock presentation response from Truid for user 1`() {

//            Creates the response for the mock response from Truid
                val presentationResponse = PresentationResponse(
                    sub = "1234567userone", claims = listOf(
                        PresentationResponseClaims(
                            type = "truid.app/claim/email/v1", value = "user-1@example.com"
                        ),
                        PresentationResponseClaims(
                            type = "truid.app/claim/bithdate/v1", value = "1111-11-11"
                        )
                    )
                )

                WireMock.stubFor(
                    WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1")
                        .willReturn(
                            WireMock.aResponse().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withStatus(200).withJsonBody(presentationResponse)
                        )
                )

            }

            @Test
            fun `Should return shareable link with the P2P session id`() {
                val response = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/share")
                        .header(HttpHeaders.COOKIE, cookie.toString())
                        .build(), String::class.java

                )

                assertEquals(200, response.statusCode.value())
                assertTrue(response.body!!.contains(testP2PSessionId))
            }

            @Test
            fun `It should return the status INITIATED`() {
                // Same as the test below but the title is wrong?
                val response = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId")
                        .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                )
                assertEquals(200, response.statusCode.value())
                assertEquals("INITIATED", response.body)
            }

            @Test
            fun `It should return status 200 and user 1 data`() {
                val response = testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/data")
                        .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                )

                val responseInJson = response.toJsonNode()
                assertEquals(200, response.statusCode.value())
                assertEquals("\"truid.app/claim/email/v1\"", responseInJson[0][0].get("type").toString())
                assertEquals("\"user-1@example.com\"", responseInJson[0][0].get("value").toString())
                assertEquals("\"truid.app/claim/bithdate/v1\"", responseInJson[0][1].get("type").toString())
                assertEquals("\"1111-11-11\"", responseInJson[0][1].get("value").toString())
            }


            @Nested
            inner class User2Flow {

                // Ändra så att det blir PPID?
                @BeforeEach
                fun `Setup cookie for user 2`() {
                    val response = testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/init-join").build(),
                        String::class.java
                    )

                    //            Checks whether the cookie is received and is set to httpOnly
                    cookie = HttpCookie.parse(response.setCookie()).single()
                    assertEquals(true, cookie.isHttpOnly)
                    //            Checks whether the site redirects user
                    assertEquals(302, response.statusCode.value())
                }

                @Test
                fun `It should redirect the user to Truid authorization endpoint if trying to access existing P2P session before authenticating`() {
                    val response = testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/init-join")
                            .header(HttpHeaders.COOKIE, cookie.toString())
                            .build(),
                        String::class.java
                    )
                    assertEquals(302, response.statusCode.value())

                    val url = URIBuilder(response.headers.location!!)
                    assertEquals("https", url.scheme)
                    assertEquals("api.truid.app", url.host)
                    assertEquals("/oauth2/v1/authorize/confirm-signup", url.path)
                    assertEquals("code", url.getParam("response_type"))
                    assertEquals("test-client-id", url.getParam("client_id"))
                    Assertions.assertNotNull(url.getParam("scope"))
                }

                @Test
                fun `It should not add client_secret to the returned URL`() {
                    val response = testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/init-join")
                            .header(HttpHeaders.COOKIE, cookie.toString())
                            .build(),
                        String::class.java
                    )
                    assertEquals(302, response.statusCode.value())

                    val url = URIBuilder(response.location())
                    Assertions.assertNull(url.getParam("client_secret"))
                }

                @Test
                fun `It should return 404 if invalid P2P session is provided`() {
                    val response = testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/invalid_session_id/init-join")
                            .header(HttpHeaders.COOKIE, cookie.toString()).build(),
                        String::class.java
                    )
                    assertEquals(404, response.statusCode.value())
                }


                @Nested
                inner class User2CompletedSignup {

                    @BeforeEach
                    fun `Setup Truid token endpoint mock`() {
                        val response = mapOf(
                            "refresh_token" to "refresh-token-2",
                            "access_token" to "access-token-2",
                            "expires_in" to 3600,
                            "token_type" to "token-type",
                            "scope" to "truid.app/data-point/email truid.app/data-point/birthdate"
                        )

                        WireMock.stubFor(
                            WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                                WireMock.aResponse().withStatus(200)
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withJsonBody(response)
                            )
                        )
                    }

                    @BeforeEach
                    fun `Setup Authorization for user 2`() {
                        val response = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/init-join")
                                .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                        )
                        assertEquals(302, response.statusCode.value())
                        assertEquals("/oauth2/v1/authorize/confirm-signup", response.headers.location!!.path)
                        val url = URIBuilder(response.headers.location!!)
                        state = url.getParam("state")!!
                    }

                    @BeforeEach
                    fun `Mock presentation response from Truid for user 2`() {

                        // Creates the response for the mock response from Truid
                        val presentationResponse = PresentationResponse(
                            sub = "1234567usertwo", claims = listOf(
                                PresentationResponseClaims(
                                    type = "truid.app/claim/email/v1", value = "user-2@example.com"
                                ), PresentationResponseClaims(
                                    type = "truid.app/claim/bithdate/v1", value = "2222-22-22"
                                )
                            )
                        )
                        WireMock.stubFor(
                            WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1")
                                .willReturn(
                                    WireMock.aResponse()
                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                        .withStatus(200).withJsonBody(presentationResponse)
                                )
                        )
                    }

                    @BeforeEach
                    fun `Setup user access peer-to-peer after authentication`() {
                        val response = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/join?code=1234&state=$state")
                                .header(HttpHeaders.COOKIE, cookie.toString()).accept(MediaType.TEXT_HTML).build(),
                            String::class.java
                        )

                        val url = URIBuilder(response.headers[HttpHeaders.LOCATION]?.firstOrNull())

                        assertEquals(302, response.statusCode.value(), "Status code is not 302")
                        assertEquals("/truid/v1/peer-to-peer/${testP2PSessionId}/data", url.path)
                    }

                    @Test
                    fun `It should return 302, add redirect to the page with user data`() {
                        val response = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/init-join")
                                .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                        )

                        assertEquals(302, response.statusCode.value())
                    }

                    @Test
                    fun `It should return 200, add return the data of the two users`() {
                        val response = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/${testP2PSessionId}/data")
                                .header(HttpHeaders.COOKIE, cookie.toString()).build(), String::class.java
                        )

                        println("response: $response")

                        val responseInJson = response.toJsonNode()

                        assertEquals(200, response.statusCode.value())
                        assertEquals("\"truid.app/claim/email/v1\"", responseInJson[0][0].get("type").toString())
                        assertEquals("\"user-1@example.com\"", responseInJson[0][0].get("value").toString())
                        assertEquals("\"truid.app/claim/bithdate/v1\"", responseInJson[0][1].get("type").toString())
                        assertEquals("\"1111-11-11\"", responseInJson[0][1].get("value").toString())
                        assertEquals("\"truid.app/claim/email/v1\"", responseInJson[1][0].get("type").toString())
                        assertEquals("\"user-2@example.com\"", responseInJson[1][0].get("value").toString())
                        assertEquals("\"truid.app/claim/bithdate/v1\"", responseInJson[1][1].get("type").toString())
                        assertEquals("\"2222-22-22\"", responseInJson[1][1].get("value").toString())

//                      Gammal kod, såhär såg det ut innan, läs todoist för mer info
//                        assertEquals(
//                            " [{\"type\":\"truid.app/claim/email/v1\",\"value\":\"user-1@example.com\"},{\"type\":\"truid.app/claim/bithdate/v1\",\"value\":\"1111-11-11\"}] </br> [{\"type\":\"truid.app/claim/email/v1\",\"value\":\"user-2@example.com\"},{\"type\":\"truid.app/claim/bithdate/v1\",\"value\":\"2222-22-22\"}] </br>",
//                            response.body
//                        )
                    }

                    @Test
                    fun `Should be able to access same data if user logs in again after session is lost, and should not be able to get data with old cookie afterwards`() {
                        val responseOne = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/data")
                                .build(), String::class.java
                        )

                        val newCookie = HttpCookie.parse(responseOne.setCookie()).single()
                        assertEquals(true, newCookie.isHttpOnly, "Cookie is not httpOnly")
                        assertNotEquals(newCookie.toString(), cookie.toString(), "Cookies are the same")
                        assertEquals(302, responseOne.statusCode.value())

                        val responseTwo = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/init-join")
                                .header(HttpHeaders.COOKIE, newCookie.toString())
                                .build(), String::class.java
                        )

                        val urlOne = URIBuilder(responseTwo.headers.location!!)
                        val newState = urlOne.getParam("state")!!


                        val responseThree = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/join?code=1234&state=$newState")
                                .header(HttpHeaders.COOKIE, newCookie.toString())
                                .accept(MediaType.TEXT_HTML)
                                .build(),
                            String::class.java
                        )

                        val responseFour = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/data")
                                .header(HttpHeaders.COOKIE, newCookie.toString())
                                .build(), String::class.java
                        )


                        val finalCookie = HttpCookie.parse(responseFour.setCookie()).single()

                        assertEquals(
                            finalCookie.toString(),
                            newCookie.toString(),
                            "New cookie is not used to access the data"
                        )
                        assertEquals(200, responseFour.statusCode.value())
                        val responseInJson = responseFour.toJsonNode()

                        assertEquals("\"truid.app/claim/email/v1\"", responseInJson[0][0].get("type").toString())
                        assertEquals("\"user-1@example.com\"", responseInJson[0][0].get("value").toString())
                        assertEquals("\"truid.app/claim/bithdate/v1\"", responseInJson[0][1].get("type").toString())
                        assertEquals("\"1111-11-11\"", responseInJson[0][1].get("value").toString())
                        assertEquals("\"truid.app/claim/email/v1\"", responseInJson[1][0].get("type").toString())
                        assertEquals("\"user-2@example.com\"", responseInJson[1][0].get("value").toString())
                        assertEquals("\"truid.app/claim/bithdate/v1\"", responseInJson[1][1].get("type").toString())
                        assertEquals("\"2222-22-22\"", responseInJson[1][1].get("value").toString())

//                     Gammal kod, såhär såg det ut innan, läs todoist för mer info
//                        assertEquals(
//                            " [{\"type\":\"truid.app/claim/email/v1\",\"value\":\"user-1@example.com\"},{\"type\":\"truid.app/claim/bithdate/v1\",\"value\":\"1111-11-11\"}] </br> [{\"type\":\"truid.app/claim/email/v1\",\"value\":\"user-2@example.com\"},{\"type\":\"truid.app/claim/bithdate/v1\",\"value\":\"2222-22-22\"}] </br>",
//                            responseFour.body
//                        )

                        val invalidCookie = HttpCookie("Set-Cookie", "JSESSIONID=invalid")

                        // Should not be able to access the data with the old cookie
                        val responseFive = testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/${testP2PSessionId}/data")
                                .header(HttpHeaders.COOKIE, invalidCookie.toString()).build(), String::class.java
                        )
                        assertEquals(302, responseFive.statusCode.value())
                    }
                }
            }
        }
    }

    fun <T> ResponseEntity<T>.location() = this.headers[HttpHeaders.LOCATION]?.firstOrNull()
    fun <T> ResponseEntity<T>.setCookie() = this.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()
    fun <T> ResponseEntity<T>.toJsonNode(): JsonNode {
        return ObjectMapper().readTree(this.body!! as String)
    }

    fun URIBuilder.getParam(name: String) = this.queryParams.firstOrNull { it.name == name }?.value

    fun ResponseDefinitionBuilder.withJsonBody(body: Any): ResponseDefinitionBuilder =
        this.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .withBody(ObjectMapper().writeValueAsString(body))


}