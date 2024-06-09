package app.truid.trupal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import jakarta.servlet.http.HttpServletResponse
import org.apache.http.client.utils.URIBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.net.HttpCookie

@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class P2PApiTest {
    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    @Nested
    inner class User1BeforeSignup {
        @Test
        fun `It should add a state parameter to the returned URL`() {
            val res =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer")
                        .build(),
                    Void::class.java,
                )
            assertEquals(HttpServletResponse.SC_FOUND, res.statusCode.value())

            val url = URIBuilder(res.location())
            assertNotNull(url.getParam("state"))
        }

        @Test
        fun `It should use PKCE with S256`() {
            val res =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer")
                        .build(),
                    Void::class.java,
                )
            assertEquals(302, res.statusCode.value())

            val url = URIBuilder(res.location())
            assertNotNull(url.getParam("code_challenge"))
            assertEquals(43, url.getParam("code_challenge")?.length)
            assertEquals("S256", url.getParam("code_challenge_method"))
        }
    }

    @Nested
    inner class User1AfterSignup {
        lateinit var userOneCookie: HttpCookie
        lateinit var userOneState: String

        @BeforeEach
        fun `Setup User 1 Truid token endpoint mock`() {
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
                        .withJsonBody(response),
                ),
            )
        }

        @BeforeEach
        fun `Setup User 1 Authorization`() {
            // Simulate user 1 completing the signup flow
            val response =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer").build(),
                    Void::class.java,
                )

            // Set cookie and state
            userOneCookie = HttpCookie.parse(response.setCookie()).single()
            val urlOne = URIBuilder(response.headers.location!!)
            userOneState = urlOne.getParam("state")!!
        }

        @Test
        fun `It should return 403 if no parameters are provided`() {
            val response =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/create").build(),
                    Void::class.java,
                )

            assertEquals(403, response.statusCode.value())
        }

        @Test
        fun `Should return 403 if wrong state is provided`() {
            val response =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/create?code=1234&state=wrong_state")
                        .header(HttpHeaders.COOKIE, userOneCookie.toString())
                        .build(),
                    Map::class.java,
                )

            assertEquals(403, response.statusCode.value())
        }

        @Test
        fun `Should return 403 if correct parameters but no cookie is provided`() {
            val response =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/create?code=1234&state=$userOneState")
                        .build(),
                    Void::class.java,
                )

            assertEquals(403, response.statusCode.value())
        }

        @Test
        fun `It should return 403 on error authorization response`() {
            val res =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/create?error=access_denied")
                        .header(HttpHeaders.COOKIE, userOneCookie.toString())
                        .build(),
                    Map::class.java,
                )
            assertEquals(403, res.statusCode.value())
        }

        @Nested
        inner class User2BeforeSignup {
            lateinit var testP2PSessionId: String
            lateinit var p2pSessionJoinUri: String
            lateinit var userOnePresentationResponse: PresentationResponse

            @BeforeEach
            fun `User 1 creates p2p session and sends link with P2P session id to user 2`() {
                // Mock successful presentation response from Truid
                userOnePresentationResponse =
                    PresentationResponse(
                        sub = "1234567userone",
                        claims =
                            listOf(
                                PresentationResponseClaims(
                                    type = "truid.app/claim/email/v1",
                                    value = "user-1@example.com",
                                ),
                                PresentationResponseClaims(
                                    type = "truid.app/claim/birthdate/v1",
                                    value = "1111-11-11",
                                ),
                            ),
                    )

                WireMock.stubFor(
                    WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1")
                        .willReturn(
                            WireMock.aResponse()
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withStatus(200).withJsonBody(userOnePresentationResponse),
                        ),
                )
                // Set the P2P session id
                val shareLink =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/create?code=1234&state=$userOneState")
                            .header(HttpHeaders.COOKIE, userOneCookie.toString())
                            .accept(MediaType.TEXT_HTML)
                            .build(),
                        Void::class.java,
                    ).let {
                        assertEquals(302, it.statusCode.value())
                        it.headers.location!!.path
                    }

                p2pSessionJoinUri =
                    testRestTemplate.exchange(
                        RequestEntity.get(shareLink)
                            .header(HttpHeaders.COOKIE, userOneCookie.toString())
                            .build(),
                        Map::class.java,
                    ).let {
                        val link = it.body!!.get("link") as String?
                        URIBuilder(link).path
                    }

                testP2PSessionId = p2pSessionJoinUri.split("/").dropLast(1).last()
            }

            @Test
            fun `It should return session data when user 1 access p2p session data`() {
                val presentationResponseJson = mapper.writeValueAsString(userOnePresentationResponse)
                val presentationResponseJsonCleaned =
                    presentationResponseJson.replace("\"", "").replace(",", ", ").replace(":", "=")

                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/data")
                            .header(HttpHeaders.COOKIE, userOneCookie.toString())
                            .build(),
                        List::class.java,
                    )

                assertEquals(presentationResponseJsonCleaned, response.body!![0].toString())
                assertEquals(200, response.statusCode.value())
            }

            @Test
            fun `It should return session object when user 1 access p2p session endpoint`() {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId")
                            .header(HttpHeaders.COOKIE, userOneCookie.toString())
                            .build(),
                        Session::class.java,
                    )
                assertEquals(200, response.statusCode.value())
                assertEquals(testP2PSessionId, response.body?.id)
            }

            @Test
            fun `It should return 400 if invalid P2P session is provided`() {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/invalid_session_id").build(),
                        Void::class.java,
                    )
                assertEquals(400, response.statusCode.value())
            }

            @Test
            fun `It should return 400 if invalid P2P session is trying to be joined`() {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/invalid_session_id/init-join")
                            .build(),
                        Void::class.java,
                    )
                assertEquals(400, response.statusCode.value())
            }

            @Test
            fun `It should return 400 if unauthenticated users try to access p2p session endpoint`() {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId")
                            .build(),
                        Map::class.java,
                    )
                assertEquals(400, response.statusCode.value())
            }

            @Test
            fun `It should return 400 if unauthenticated users try to access p2p session data endpoint`() {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId/data")
                            .build(),
                        Map::class.java,
                    )
                assertEquals(400, response.statusCode.value())
            }

            @Test
            fun `It should return the status INITIALIZED`() {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId")
                            .header(HttpHeaders.COOKIE, userOneCookie.toString())
                            .build(),
                        Session::class.java,
                    )

                assertEquals(200, response.statusCode.value())
                assertEquals(SessionStatus.INITIALIZED, response.body!!.status)
            }

            @Test
            fun `It should return a cookie containing the session ID`() {
                val res =
                    testRestTemplate.exchange(
                        RequestEntity.get("/truid/v1/peer-to-peer")
                            .build(),
                        Void::class.java,
                    )
                assertEquals(302, res.statusCode.value())
                val cookie = HttpCookie.parse(res.setCookie()).single()
                assertEquals(true, cookie.isHttpOnly)
                assertEquals("JSESSIONID", cookie.name)
                assertNotNull(cookie.value)
            }

            @Test
            fun `It should redirect to Truid authorization endpoint`() {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get(p2pSessionJoinUri).build(),
                        Void::class.java,
                    )
                assertEquals(302, response.statusCode.value())

                val url = URIBuilder(response.headers.location!!)
                assertEquals("https", url.scheme)
                assertEquals("api.truid.app", url.host)
                assertEquals("/oauth2/v1/authorize/confirm-signup", url.path)
                assertEquals("code", url.getParam("response_type"))
                assertEquals("test-client-id", url.getParam("client_id"))
                assertNotNull(url.getParam("scope"))
            }

            @Nested
            inner class User2AfterSignup {
                lateinit var userTwoCookie: HttpCookie
                lateinit var userTwoState: String
                lateinit var p2pSessionDataUri: String
                lateinit var userTwoPresentationResponse: PresentationResponse

                @BeforeEach
                fun `Setup User 2 Truid token endpoint mock`() {
                    val response =
                        mapOf(
                            "refresh_token" to "refresh-token-2",
                            "access_token" to "access-token-2",
                            "expires_in" to 3600,
                            "token_type" to "token-type",
                            "scope" to "truid.app/data-point/email truid.app/data-point/birthdate",
                        )

                    WireMock.stubFor(
                        WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                            WireMock.aResponse().withStatus(200)
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withJsonBody(response),
                        ),
                    )
                }

                @BeforeEach
                fun `Setup User 2 Authorization`() {
                    // Creates the response for the mock response from Truid
                    userTwoPresentationResponse =
                        PresentationResponse(
                            sub = "1234567usertwo",
                            claims =
                                listOf(
                                    PresentationResponseClaims(
                                        type = "truid.app/claim/email/v1",
                                        value = "user-2@example.com",
                                    ),
                                    PresentationResponseClaims(
                                        type = "truid.app/claim/birthdate/v1",
                                        value = "2222-22-22",
                                    ),
                                ),
                        )
                    WireMock.stubFor(
                        WireMock.get(
                            "/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1",
                        )
                            .willReturn(
                                WireMock.aResponse()
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withStatus(200).withJsonBody(userTwoPresentationResponse),
                            ),
                    )

                    val response =
                        testRestTemplate.exchange(
                            RequestEntity.get(p2pSessionJoinUri).build(),
                            Void::class.java,
                        )

                    userTwoCookie = HttpCookie.parse(response.setCookie()).single()
                    val url = URIBuilder(response.headers.location!!)
                    userTwoState = url.getParam("state")!!

                    p2pSessionDataUri =
                        testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/join?code=1234&state=$userTwoState")
                                .header(HttpHeaders.COOKIE, userTwoCookie.toString()).accept(MediaType.TEXT_HTML)
                                .build(),
                            Void::class.java,
                        ).let {
                            val url = URIBuilder(it.headers[HttpHeaders.LOCATION]?.firstOrNull())
                            url.path
                        }
                }

                @Test
                fun `It should return the status COMPLETED`() {
                    val response =
                        testRestTemplate.exchange(
                            RequestEntity.get("/truid/v1/peer-to-peer/$testP2PSessionId")
                                .header(HttpHeaders.COOKIE, userTwoCookie.toString()).build(),
                            Session::class.java,
                        )
                    assertEquals(200, response.statusCode.value())
                    assertEquals(SessionStatus.COMPLETED, response.body!!.status)
                }

                @Test
                fun `It should return 200, add return the data of the two users`() {
                    val response =
                        testRestTemplate.exchange(
                            RequestEntity.get(p2pSessionDataUri)
                                .header(HttpHeaders.COOKIE, userTwoCookie.toString()).build(),
                            String::class.java,
                        )

                    val responseInJson = response.toJsonNode()

                    // Checks whether the claims data in response matches the predefined format in the test
                    assertEquals(200, response.statusCode.value())
                    assertEquals(
                        "truid.app/claim/email/v1",
                        responseInJson[0].get("claims")[0].get("type").asText(),
                    )
                    assertEquals(
                        "user-1@example.com",
                        responseInJson[0].get("claims")[0].get("value").asText(),
                    )
                    assertEquals(
                        "truid.app/claim/birthdate/v1",
                        responseInJson[0].get("claims")[1].get("type").asText(),
                    )
                    assertEquals(
                        "1111-11-11",
                        responseInJson[0].get("claims")[1].get("value").asText(),
                    )
                    assertEquals(
                        "truid.app/claim/email/v1",
                        responseInJson[1].get("claims")[0].get("type").asText(),
                    )
                    assertEquals(
                        "user-2@example.com",
                        responseInJson[1].get("claims")[0].get("value").asText(),
                    )
                    assertEquals(
                        "truid.app/claim/birthdate/v1",
                        responseInJson[1].get("claims")[1].get("type").asText(),
                    )
                    assertEquals(
                        "2222-22-22",
                        responseInJson[1].get("claims")[1].get("value").asText(),
                    )
                }

                @Test
                fun `Should not be able to get data with invalid cookie`() {
                    val invalidCookie = HttpCookie("Set-Cookie", "JSESSIONID=invalid")

                    // Should not be able to access the data with the old cookie
                    val response =
                        testRestTemplate.exchange(
                            RequestEntity.get(p2pSessionDataUri)
                                .header(HttpHeaders.COOKIE, invalidCookie.toString()).build(),
                            Void::class.java,
                        )
                    assertEquals(400, response.statusCode.value())
                }

                @Nested
                inner class User3AfterSignup {
                    lateinit var userThreeCookie: HttpCookie
                    lateinit var userThreeState: String

                    @BeforeEach
                    fun `Setup User 3 Truid token endpoint mock`() {
                        val response =
                            mapOf(
                                "refresh_token" to "refresh-token-3",
                                "access_token" to "access-token-3",
                                "expires_in" to 3600,
                                "token_type" to "token-type",
                                "scope" to "truid.app/data-point/email truid.app/data-point/birthdate",
                            )

                        WireMock.stubFor(
                            WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                                WireMock.aResponse().withStatus(200)
                                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .withJsonBody(response),
                            ),
                        )
                    }

                    @BeforeEach
                    fun `Setup User 3 Authorization`() {
                        // Creates the response for the mock response from Truid
                        val presentationResponse =
                            PresentationResponse(
                                sub = "1234567userthree",
                                claims =
                                    listOf(
                                        PresentationResponseClaims(
                                            type = "truid.app/claim/email/v1",
                                            value = "user-3@example.com",
                                        ),
                                        PresentationResponseClaims(
                                            type = "truid.app/claim/birthdate/v1",
                                            value = "3333-33-33",
                                        ),
                                    ),
                            )
                        WireMock.stubFor(
                            WireMock.get(
                                "/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1%2Ctruid.app%2Fclaim%2Fbirthdate%2Fv1",
                            )
                                .willReturn(
                                    WireMock.aResponse()
                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                        .withStatus(200).withJsonBody(presentationResponse),
                                ),
                        )

                        val response =
                            testRestTemplate.exchange(
                                RequestEntity.get("/truid/v1/peer-to-peer").build(),
                                Void::class.java,
                            )

                        // Set cookie and state
                        userThreeCookie = HttpCookie.parse(response.setCookie()).single()
                        val urlOne = URIBuilder(response.headers.location!!)
                        userThreeState = urlOne.getParam("state")!!

                        val responseTwo =
                            testRestTemplate.exchange(
                                RequestEntity.get("/truid/v1/peer-to-peer/create?code=1234&state=$userThreeState")
                                    .header(HttpHeaders.COOKIE, userThreeCookie.toString())
                                    .accept(MediaType.TEXT_HTML)
                                    .build(),
                                Void::class.java,
                            )

                        assertEquals(302, responseTwo.statusCode.value())
                        responseTwo.headers.location!!.path
                    }

                    @Test
                    fun `User 3 should not be able to join the session after user 1 and 2 joined`() {
                        val response =
                            testRestTemplate.exchange(
                                RequestEntity.get(p2pSessionJoinUri)
                                    .build(),
                                Map::class.java,
                            )

                        assertEquals(400, response.statusCode.value())
                        assertNotNull(response.body?.get("ErrorMsg"))
                    }

                    @Test
                    fun `User 3 should not be able to access data of the session`() {
                        val response =
                            testRestTemplate.exchange(
                                RequestEntity.get(p2pSessionDataUri)
                                    .header(HttpHeaders.COOKIE, userThreeCookie.toString())
                                    .build(),
                                Map::class.java,
                            )

                        assertEquals(403, response.statusCode.value())
                        assertNotNull(response.body?.get("ErrorMsg"))
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
