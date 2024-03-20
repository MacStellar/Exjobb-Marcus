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
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}

fun main(args: Array<String>) {
    runApplication<TrupalApplication>(*args)

    println("Kör program")
}