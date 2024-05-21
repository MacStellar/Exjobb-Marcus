package app.truid.trupal.maninthemiddle

import app.truid.trupal.Forbidden
import app.truid.trupal.PersonNotFound
import app.truid.trupal.PresentationResponse
import app.truid.trupal.PresentationResponseClaims
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import org.apache.http.client.utils.URIBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
class ManInTheMiddleTest() {
    @Autowired
    lateinit var testRestTemplate: TestRestTemplate
    lateinit var trupal: Trupal
    lateinit var personList: MutableList<Person>
    lateinit var alice: Person
    lateinit var malice: Person
    lateinit var bob: Person
    lateinit var p2pSessionDataUri: String
    lateinit var aliceToMaliceChannel: ExternalCommunicationChannel
    lateinit var bobToMaliceChannel: ExternalCommunicationChannel

    // Allt ska gå genom användarna, inget ska gå genom "gud"
    // Flera cases,
    // 1. Alice och Bob pratar med varandra på en extern kommunikationskanal där det finns unika
    //    identifieringsgrejer för båda användare, då ksk man inte behöver omdirigera kommunikationsflödet.
    // 2. Alice och Bob pratar med varandra på en kommunikationskanal där det inte finns unika
    //    identifieringsgrejer, då måste dem ledas in på en ny kommunikationskanal som är säker.
    // 3. Alice och Bob pratar med varandra fysiskt IRL?

    @BeforeEach
    fun `External communication channel between Alice and Bob setup with man-in-the-middle Malice`() {
        personList = mutableListOf<Person>()
        alice = Person("Alice") // Legitimate user
        bob = Person("Bob") // Legitimate user
        malice = Person("Malice") // Man in the middle / threat actor
        personList.add(alice)
        personList.add(bob)
        personList.add(malice)

        trupal = Trupal()

        // Malice has created a fake profile on an insecure external communication channel
        // impersonating Bob. Alice reaches out to who she thinks is bob, but in fact is Malice
        aliceToMaliceChannel =
            ExternalCommunicationChannel(
                personOne = alice,
                personTwo = malice,
                impersonator = Pair(malice, "Bob (Malice)"),
            )

        // Malice has created a fake profile on an insecure external communication channel
        // impersonating Alice. Malice reaches out to bob, who thinks it is Alice reaching out
        bobToMaliceChannel =
            ExternalCommunicationChannel(
                personOne = bob,
                personTwo = malice,
                impersonator = Pair(malice, "Alice (Malice)"),
            )

        // Alice initiates a conversation with Bob online on an insecure communication
        // channel, in this case a second hand web shop. She wants to buy a watch from Bob.
        aliceToMaliceChannel.sendMessage(alice, "\u001B[38;5;120mHello! Saw your watch, i would like to buy it.\u001B[0m")
        bobToMaliceChannel.sendMessage(malice, "\u001B[38;5;214mHello! Saw your watch, i would like to buy it.\u001B[0m")
        bobToMaliceChannel.sendMessage(bob, "\u001B[38;5;120mHello! Great, it will be 100 dollars. How do you want to pay?\u001B[0m")
        aliceToMaliceChannel.sendMessage(
            malice,
            "\u001B[38;5;214mHello! Great, it will be 100 dollars. How do you want to pay?\u001B[0m",
        )
        aliceToMaliceChannel.sendMessage(
            alice,
            "\u001B[38;5;120mActually, could you authenticate yourself first? I can send you a link where you can sign up with the app TruPal.\u001B[0m",
        )
        bobToMaliceChannel.sendMessage(
            malice,
            "\u001B[38;5;214mActually, could you authenticate yourself first? I can send you a link where you can sign up with the app TruPal.\u001B[0m",
        )
        bobToMaliceChannel.sendMessage(bob, "\u001B[38;5;120mOf course!\u001B[0m")
        aliceToMaliceChannel.sendMessage(malice, "\u001B[38;5;214mOf course!\u001B[0m")

        // Alice authenticates herself in the trupal app and creates a P2P session. She then
        // shares the link through the chat with Malice (who she thinks is bob)
        val p2pSessionJoinUri = trupal.createP2PSession(alice)
        aliceToMaliceChannel.sendMessage(alice, "\u001B[38;5;120mHere is the link: $p2pSessionJoinUri\u001B[0m")

        // Malice copies the link and sends it on to Bob, without opening it in the process.
        // Malice keeps impersonating Alice and Bob successfully on the external communication
        // channels.
        bobToMaliceChannel.sendMessage(malice, "\u001B[38;5;214mHere is the link: $p2pSessionJoinUri\u001B[0m")

        // Bob opens the link and authenticates himself.
        p2pSessionDataUri = trupal.joinP2PSession(bob, p2pSessionJoinUri)

        // Both users can now see eachothers information on the data page
        val p2pSessionData = trupal.getP2pSessionData(alice, p2pSessionDataUri)

        // Alice cannot see the data in the p2p session
        assertEquals(
            "{\"ErrorMsg\":\"Cookie session not found, message: Cookie session not found on server-side, cause: null\"}",
            trupal.getP2pSessionData(malice, p2pSessionDataUri).toString(),
        )
    }

    @Test
    fun `Alice and Bob establish secure communication channel (evades malice)`() {
        // CRITICAL STEP! The external communication channels are terminated and exchanged
        // for a secure communication channel between Alice and Bob which is established
        // through the trupal app.

        val secureCommunicationChannel = trupal.createSecureCommunicationChannel(alice.name, personList, p2pSessionDataUri)

        // Malice cannot send messages in the secure communication channel
        assertThrows<Forbidden> { secureCommunicationChannel.sendMessage(malice, "Hi") }

        secureCommunicationChannel.sendMessage(
            alice,
            "\u001B[38;5;120mOk great, now i know our communication is secure. Can you send your account number so i can send the money?\u001B[0m",
        )
        secureCommunicationChannel.sendMessage(
            bob,
            "\u001B[38;5;120mYes, it is 5054330867bob. What address should i ship the watch to?\u001B[0m",
        )
        secureCommunicationChannel.sendMessage(
            alice,
            "\u001B[38;5;120mAlice Road 12, Pittsburg, Pennsylvana.\u001B[0m",
        )

        aliceToMaliceChannel.printChat()
        println("    ")
        bobToMaliceChannel.printChat()
        secureCommunicationChannel.printChat()

        println("\u001B[31mThis is red text.\u001B[0m")
        println("\u001B[38;5;214m...\u001B[0m"); // Orange text
        println("\u001B[38;5;120m...\u001B[0m") // Light Green text
        println("\u001B[34mThis is blue text.\u001B[0m")
    }

    @Test
    fun `Alice and Bob stays on insecure external communication channel (malice succeeds)`() {
        aliceToMaliceChannel.sendMessage(
            alice,
            "\u001B[38;5;120mGreat, now i know you've authenticated! Can you send your account number so i can send the money?\u001B[0m",
        )
        bobToMaliceChannel.sendMessage(
            malice,
            "\u001B[38;5;214mGreat, now i know you've authenticated! Can you send your account number so i can send the money?\u001B[0m",
        )
        bobToMaliceChannel.sendMessage(bob, "\u001B[38;5;120mYes, it is 5054330867bob. What address should i ship the watch to?\u001B[0m")
        aliceToMaliceChannel.sendMessage(malice, "\u001B[31mYes, it is 92039586malice. What address should i ship the watch to?\u001B[0m")
        aliceToMaliceChannel.sendMessage(
            alice,
            "\u001B[38;5;120mAlice Road 12, Pittsburg, Pennsylvana.\u001B[0m",
        )
        bobToMaliceChannel.sendMessage(
            malice,
            "\u001B[31mMalice Road 99, Denver, Vancouver.\u001B[0m",
        )

        aliceToMaliceChannel.printChat()
        println("    ")
        bobToMaliceChannel.printChat()
    }

    inner class Trupal {
        fun createSecureCommunicationChannel(
            personName: String,
            personList: MutableList<Person>,
            p2pSessionDataUri: String,
        ): SecureCommunicationChannel {
            val person = personList.find { it.name == personName } ?: throw PersonNotFound()
            val p2pSessionData = getP2pSessionData(person, p2pSessionDataUri)
//            val personOneName =
//                p2pSessionData[0]
//                    .get("claims")
//                    .single { it["\"type\""].toString() == "\"truid.app/claim/email/v1\"" }
//                    .get("value").toString()
//                    .split("@").first()
//            val personTwoName =
//                p2pSessionData[1]
//                    .get("claims")
//                    .single { it["type"].toString() == "truid.app/claim/email/v1" }
//                    .get("value").toString()
//                    .split("@").first()

//            val personOneClaims = p2pSessionData[0].get("claims") as List<Map<String, String>>
//            val personOneEmailClaim = personOneClaims.find { it["type"] == "truid.app/claim/email/v1" }
//            val personOneName = personOneEmailClaim?.get("value")?.toString()?.split("@")?.first()
//
//            val personTwoClaims = p2pSessionData[1].get("claims") as List<Map<String, String>>
//            val personTwoEmailClaim = personTwoClaims.find { it["type"] == "truid.app/claim/email/v1" }
//            val personTwoName = personTwoEmailClaim?.get("value")?.toString()?.split("@")?.first()

            val (email1, email2) =
                p2pSessionData
                    .flatMap { it.get("claims") }
                    .filter { it.get("type").asText() == "truid.app/claim/email/v1" }
                    .map { it.get("value").asText() }

            val personOneName = p2pSessionData[0].get("claims")[0].get("value").toString().split("@").first().replace("\"", "")
            val personTwoName = p2pSessionData[1].get("claims")[0].get("value").toString().split("@").first().replace("\"", "")
            val personOne = personList.find { it.name == personOneName } ?: throw PersonNotFound()
            val personTwo = personList.find { it.name == personTwoName } ?: throw PersonNotFound()

            return SecureCommunicationChannel(personOne, personTwo)
        }

        fun getP2pSessionData(
            person: Person,
            p2pSessionDataUri: String,
        ): JsonNode {
            try {
                val response =
                    testRestTemplate.exchange(
                        RequestEntity.get(p2pSessionDataUri)
                            .header(HttpHeaders.COOKIE, person.memory["cookie"]).build(),
                        String::class.java,
                    )

                val responseInJson = response.toJsonNode()

                return responseInJson
            } catch (e: NullPointerException) {
                throw Forbidden("access_denied", null)
            }
        }

        fun createP2PSession(person: Person): String {
            // Mock successful presentation response from Truid
            val tokenResponse =
                mapOf(
                    "refresh_token" to "refresh-token-${person.name}",
                    "access_token" to "access-token-${person.name}",
                    "expires_in" to 3600,
                    "token_type" to "token-type",
                    "scope" to "truid.app/data-point/email truid.app/data-point/birthdate",
                )
            WireMock.stubFor(
                WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                    WireMock.aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withJsonBody(tokenResponse),
                ),
            )

            val userOnePresentationResponse =
                PresentationResponse(
                    sub = "1234567${person.name}",
                    claims =
                        listOf(
                            PresentationResponseClaims(
                                type = "truid.app/claim/email/v1",
                                value = "${person.name}@example.com",
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

            // Simulate user 1 completing the signup flow
            val response =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer").build(),
                    Void::class.java,
                )

            // Set cookie and state
            val userOneCookie = HttpCookie.parse(response.setCookie()).single()
            val urlOne = URIBuilder(response.headers.location!!)
            val userOneState = urlOne.getParam("state")!!

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

            val p2pSessionJoinUri =
                testRestTemplate.exchange(
                    RequestEntity.get(shareLink)
                        .header(HttpHeaders.COOKIE, userOneCookie.toString())
                        .build(),
                    Map::class.java,
                ).let {
                    val link = it.body!!.get("link") as String?
                    URIBuilder(link).path
                }

            person.save("cookie", userOneCookie.toString())
            person.save("link", p2pSessionJoinUri)
            return p2pSessionJoinUri
        }

        fun joinP2PSession(
            person: Person,
            p2pSessionJoinUri: String,
        ): String {
            val tokenResponse =
                mapOf(
                    "refresh_token" to "refresh-token-${person.name}",
                    "access_token" to "access-token-${person.name}",
                    "expires_in" to 3600,
                    "token_type" to "token-type",
                    "scope" to "truid.app/data-point/email truid.app/data-point/birthdate",
                )

            WireMock.stubFor(
                WireMock.post(WireMock.urlEqualTo("/oauth2/v1/token")).willReturn(
                    WireMock.aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withJsonBody(tokenResponse),
                ),
            )

            // Creates the response for the mock response from Truid
            val userTwoPresentationResponse =
                PresentationResponse(
                    sub = "1234567usertwo",
                    claims =
                        listOf(
                            PresentationResponseClaims(
                                type = "truid.app/claim/email/v1",
                                value = "${person.name}@example.com",
                            ),
                            PresentationResponseClaims(
                                type = "truid.app/claim/bithdate/v1",
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

            val userTwoCookie = HttpCookie.parse(response.setCookie()).single()
            val url = URIBuilder(response.headers.location!!)
            val userTwoState = url.getParam("state")!!

            val p2pSessionDataUri =
                testRestTemplate.exchange(
                    RequestEntity.get("/truid/v1/peer-to-peer/join?code=1234&state=$userTwoState")
                        .header(HttpHeaders.COOKIE, userTwoCookie.toString()).accept(MediaType.TEXT_HTML)
                        .build(),
                    Void::class.java,
                ).let {
                    val url = URIBuilder(it.headers[HttpHeaders.LOCATION]?.firstOrNull())
                    url.path
                }

            person.save("cookie", userTwoCookie.toString())
            return p2pSessionDataUri
        }

        fun <T> ResponseEntity<T>.setCookie() = this.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()

        fun <T> ResponseEntity<T>.toJsonNode(): JsonNode {
            return ObjectMapper().readTree(this.body!! as String)
        }

        fun URIBuilder.getParam(name: String) = this.queryParams.firstOrNull { it.name == name }?.value

        fun ResponseDefinitionBuilder.withJsonBody(body: Any): ResponseDefinitionBuilder =
            this.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(ObjectMapper().writeValueAsString(body))
    }
}

class ExternalCommunicationChannel(
    val personOne: Person,
    val personTwo: Person,
    val impersonator: Pair<Person, String>,
) {
    val chat = mutableListOf<Pair<String, String>>()

    fun sendMessage(
        person: Person,
        message: String,
    ) {
        if (person.name == personOne.name || person.name == personTwo.name) {
            var chatName = "\u001B[38;5;120m${person.name}\u001B[0m"
            if (impersonator.first.name.isNotEmpty() && person.name == impersonator.first.name) {
                chatName = "\u001B[31m${impersonator.second}\u001B[0m"
            }
            chat.add(Pair(chatName, message))
        } else {
            throw Forbidden("User is not part of the chat", null)
        }
    }

    fun printChat() {
        println("-----------------------------")
        println("External insecure communication channel: ")
        for (message in chat) {
            println(message.first + ": " + message.second)
        }
        println("-----------------------------")
    }
}

class SecureCommunicationChannel(val personOne: Person, val personTwo: Person) {
    val chat = mutableListOf<Pair<String, String>>()

    fun sendMessage(
        person: Person,
        message: String,
    ) {
        if (person.name == personOne.name || person.name == personTwo.name) {
            chat.add(Pair("\u001B[38;5;120m${person.name}\u001B[0m", message))
        } else {
            throw Forbidden("User is not part of the chat", null)
        }
    }

    fun printChat() {
        println("-----------------------------")
        println("Secure communication channel: ")
        println("")
        for (message in chat) {
            println(message.first + ": " + message.second)
        }
        println("-----------------------------")
    }
}

class Person(val name: String) {
    val memory = mutableMapOf<String, String>()

    fun save(
        s: String,
        link: String,
    ) {
        memory[s] = link
    }
}

// TODO
// Skapa en dataklass för person, kanske personData, kanske mappa till Person memory ovan

// Todo
// Skapa inner classes så att det är failcase är som test och happy case är som beforeeach
// Ha ett fall där man chattar genom TruPal och ett där man connectas via en tredje part
// som är säker och där man har kunnat bekräfta att ens email är kopplad till kontot.

// Todo
// Gör om tester

// Todo
// Lägg till så att man får med bilder också i trupal
