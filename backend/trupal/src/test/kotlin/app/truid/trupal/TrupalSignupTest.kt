package app.truid.trupal

import com.github.tomakehurst.wiremock.client.WireMock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class TrupalSignupTest {

    @Value("\${oauth2.truid.signup-endpoint}")
    lateinit var truidSignupEndpoint: String

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate


    @Test
    fun `test local truid v1 confirm-signup`() {
        println("Test Signup works")


        val response = testRestTemplate.getForEntity<Void>("/truid/v1/confirm-signup")
        assertEquals(302, response.statusCode.value(), "Status code is not 302")
        val location = response.headers.location!!
        assertEquals("/oauth2/v1/authorize/confirm-signup", location.path)
        println(location.query)

        val queryParameters = location.query
            .split("&")
            .map { it.split("=") }
            .associate { it[0] to it[1] }

        println(queryParameters["state"])
        assertNotNull(queryParameters["state"])


    }

    @Test
    fun `test complete signup`() {
        println("Test Signup works")
        println("truidSignupEndpoint: $truidSignupEndpoint")

        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/oauth2/v1/authorize/confirm-signup"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"123\", \"name\": \"John\"}")
                )
        )
        val response = testRestTemplate.getForEntity<Void>(truidSignupEndpoint)
        println(response)
        assertEquals(200, response.statusCode.value(), "Status code is not 200")
        WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/oauth2/v1/authorize/confirm-signup")))

    }



    @Test
    fun `test again`() {
//        User 1
//        Confirm signup
//        Complete signup
//          Backend hämtar tokens
//          Backend hämtar användarinfo
//          Backend skapar sessionsID (P2P session)
//        Användare skickar länk med sessionsID
//        Användare använder polling mot backend med sessionsID (behöver ha mer säkerhet, i anslutning till backend t.ex)
//        User 2
//        Användare klickar på länk
//        Användare (get(!)/post mot endpoint som jag bygger som tar sessionsID och kollar om det finns i Truid (gammal,giltig?),
//        i övrigt gör den som confirm-signup)
//        Användare får tillbaka state/cookies från Truid

//        Confirm signup
    }

}