package app.truid.trupal

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DbTestIT {
    @Autowired
lateinit var restTemplate: TestRestTemplate

    @Test
    fun `should be able to save to database`(): Unit {
        println("unit test executed")
        val result = restTemplate.getForObject("/", String::class.java)
        println(result)
    }
}
