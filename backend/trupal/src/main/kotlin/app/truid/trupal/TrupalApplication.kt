package app.truid.trupal


import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class TrupalApplication

fun main(args: Array<String>) {
    runApplication<TrupalApplication>(*args)

    println("Kör program")

}

@RestController
class MessageController {
    @GetMapping("/")
//    fun index(@RequestParam(name = "name", defaultValue = "someone") name: String) = "Hello, $name!"
    fun index() = mutableListOf(
        Message("1", "Hello!"),
        Message("2", "Bojour!"),
        Message("3", "Privet!")
    )
}

data class Message(val id: String?, val text: String)

