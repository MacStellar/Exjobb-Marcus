package app.truid.trupal


import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import org.springframework.jdbc.core.query
import org.springframework.web.bind.annotation.*
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.*
import org.springframework.ui.Model
import org.springframework.web.client.RestTemplate
//import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.session.CookieWebSessionIdResolver
import org.springframework.web.server.session.WebSessionIdResolver
import java.net.URL



// Från Application.kt
@SpringBootApplication
class TrupalApplication {
    @Bean
    fun webSessionIdResolver(
        @Value("\${app.domain}")
        publicDomain: String
    ): WebSessionIdResolver {
        return CookieWebSessionIdResolver().apply {
            addCookieInitializer {
                // Disable secure cookies if on http or the session cookie will not be saved
                // Note: A production service in Truid cannot use a http redirect, for test only
                if (URL(publicDomain).protocol != "http") {
                    it.secure(true)
                }
                it.sameSite("Lax")
            }
        }
    }

//    @Bean
//    fun webClient(): WebClient {
//        return WebClient.create()
//    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}

fun main(args: Array<String>) {
    runApplication<TrupalApplication>(*args)

    println("Kör program")
}

// Avskiljare ----------------------------------------------------------

//fun main(args: Array<String>) {
//    runApplication<TrupalApplication>(*args)
//
//    println("Kör program")
//}
//
//
//@RestController
//class MessageController(val service: MessageService) {
//    @GetMapping("/")
//    fun index(): List<Message> = service.findMessages()
//
//    @GetMapping("/{id}", "/id/{id}")
//    fun index(@PathVariable id: String): List<Message> =
//        service.findMessageById(id)
//
//    @RequestMapping("/redirect")
//    fun redirect(): RedirectView = service.redirect()
//
//    @PostMapping("/")
//    fun post(@RequestBody message: Message) {
//        service.save(message)
//    }
//}
//
//@Table("messages")
//data class Message(@Id var id: String?, val text: String)
//
//data class Brick(var number: Int, var text: String?)
//
//@Table("objects")
//data class Objects(@Id var id: String?, val data: Brick)
//
//@Service
//class MessageService(val db: MessageRepository) {
//    fun findMessages(): List<Message> = db.findAll().toList()
//
//    fun findMessageById(id: String): List<Message> = db.findById(id).toList()
//
//    fun save(message: Message) {
//        db.save(message)
//    }
//
//    fun redirect(): RedirectView {
//        val redirectUrl = "http://www.google.com"
//
//
//
//        try{
//            val response: ResponseEntity<String> = RestTemplate().getForEntity(redirectUrl, String::class.java)
//
//            if (response.statusCode == HttpStatus.OK) {
//                return RedirectView(redirectUrl)
//            }
//            else {
//                return RedirectView("http://localhost:8080")
//            }
//        } catch (e: Exception) {
//            return RedirectView("http://localhost:8080")
//        }
//
//    }
//
//    fun <T : Any> Optional<out T>.toList(): List<T> =
//        if (isPresent) listOf(get()) else emptyList()
//}