package app.truid.trupal

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles


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


@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrupalPeerToPeerTest {

    @Value("\${oauth2.truid.signup-endpoint}")
    lateinit var truidSignupEndpoint: String

    @Value("\${oauth2.truid.token-endpoint}")
    lateinit var truidTokenEndpoint: String

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate



}