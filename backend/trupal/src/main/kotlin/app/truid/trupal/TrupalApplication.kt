package app.truid.trupal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TrupalApplication

fun main(args: Array<String>) {
    runApplication<TrupalApplication>(*args)
}
