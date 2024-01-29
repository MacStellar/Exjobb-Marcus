package app.truid.trupal

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.junit.jupiter.api.Assertions
import org.springframework.http.*
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForEntity
import java.io.Serializable
import java.net.URI


class Foo : Serializable {
    val id: Long = 0L
    val name: String? = null


}
//@SpringBootTest
class TrupalApplicationTests {

    @Test
    fun contextLoads() {

    }

    @Test
    fun testPost(): Unit {
        println("testPost")
        val restTemplate = RestTemplate()



    }

    @Test
    fun testGet() {
        println("kommmer hit")
        val restTemplate = RestTemplate()
        val fooResourceUrl = "http://localhost:8080"
        val response: ResponseEntity<String> = restTemplate.getForEntity(fooResourceUrl + "/858f94f0-7484-4f62-a8f0-50f597dd2b88", String::class.java)

        Assertions.assertEquals(response.statusCode, HttpStatus.OK)

//        Från tutorial

//        val mapper = ObjectMapper()
//        val root: JsonNode = mapper.readTree(response.body)
//        val name: JsonNode = root.path("name")
//        Assertions.assertNotNull(name)

//        Funkar med min databas:
//
        val mapper = ObjectMapper()
        val root: JsonNode = mapper.readTree(response.body)
        val name: JsonNode = root.path("text")
//        val nametwo: String? = root[0]?.get("text")?.asText()
        println("name:" + name)
        Assertions.assertNotNull(name)

//        Skippar denna

//        val foo: Foo? = restTemplate.getForObject(fooResourceUrl + "/e7936785-76f3-4a43-a0bb-e4985a76b100", Foo::class.java)
//        println(foo)
//        Assertions.assertNotNull(foo?.name)
//        Assertions.assertEquals(foo?.id, 1L)

        val httpHeaders: HttpHeaders = restTemplate.headForHeaders(fooResourceUrl)
        println(httpHeaders)
        Assertions.assertTrue(httpHeaders.contentType!!.includes(MediaType.APPLICATION_JSON))

//        println("Efter detta blir det svårt")
//        val request = HttpEntity("Bonjour!")
//        val location: URI? = restTemplate.postForLocation(fooResourceUrl, request)


        // Create a HttpHeaders object to set the content type
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        // Create a JSON object to be posted
        val requestJson = """{"text": "hej"}"""

        // Create an HttpEntity with the JSON object and headers
        val requestEntity = HttpEntity(requestJson, headers)

        // Perform the POST request and get the response entity
        val responsetwo: ResponseEntity<String> = restTemplate.exchange(fooResourceUrl, HttpMethod.POST, requestEntity, String::class.java)

        // Assert the response status code
        Assertions.assertEquals(HttpStatus.OK, responsetwo.statusCode)

        // You can print or assert on the response body if needed
        println("Response Body: ${responsetwo.body}")

//        -------------------------------------------







    }

}


