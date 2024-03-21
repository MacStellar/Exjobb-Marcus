package app.truid.trupal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity

@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class P2PApiTest {

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    fun `should not be able to get p2p session without truid auth`(): Unit {
        val response = testRestTemplate.exchange(
            RequestEntity.get("/peer-to-peer/unknown_id").build(), String::class.java
        )
    }

    @Nested
    inner class User1Flow {
        lateinit var p2pSessionId: String

        @BeforeEach
        fun `create p2p session`(): Unit {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/peer-to-peer/").build(), String::class.java
            )

            p2pSessionId = response.body!!
        }

        @Test
        fun `p2p session should have state created`(): Unit {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/peer-to-peer/$p2pSessionId").build(), String::class.java
            )
        }

        @Test
        fun `p2p session user data should be empty`(): Unit {
            val response = testRestTemplate.exchange(
                RequestEntity.get("/peer-to-peer/$p2pSessionId/result").build(), String::class.java
            )

            val toJsonNode = response.toJsonNode()
            val dataList = toJsonNode.get("dataList") as ArrayNode
            assertEquals(0, dataList.size())
        }

    }

    fun <T> ResponseEntity<T>.toJsonNode() : JsonNode {
        return ObjectMapper().readTree(this.body!! as String)
    }
}