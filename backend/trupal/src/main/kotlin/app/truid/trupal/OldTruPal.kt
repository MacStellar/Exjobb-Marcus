//package app.truid.trupal
//
//
//import org.springframework.boot.autoconfigure.SpringBootApplication
//import org.springframework.boot.runApplication
//import org.springframework.web.bind.annotation.*
//import org.springframework.web.bind.annotation.RequestBody
//import org.springframework.web.bind.annotation.PostMapping
//import org.springframework.stereotype.Service
//import org.springframework.jdbc.core.JdbcTemplate
//import java.util.UUID
//import org.springframework.jdbc.core.query
//import org.springframework.web.bind.annotation.*
//import org.springframework.data.annotation.Id
//import org.springframework.data.relational.core.mapping.Table
//import org.springframework.data.repository.CrudRepository
//import org.springframework.http.HttpStatus
//import org.springframework.http.ResponseEntity
//import java.util.*
//import org.springframework.ui.Model
//import org.springframework.web.client.RestTemplate
//import java.net.URL
//import org.springframework.web.servlet.view.RedirectView
//import kotlin.reflect.typeOf
//
//interface MessageRepository : CrudRepository<Message, String>
//
//@SpringBootApplication
//class TrupalApplication
//
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