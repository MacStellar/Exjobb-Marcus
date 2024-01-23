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


}

@RestController
class MessageController {
    @GetMapping("/")
    fun index() = "Hello world!"
    fun index(@RequestParam(name = "name", defaultValue = "someone") name: String) = "Hello, $name!"
}
