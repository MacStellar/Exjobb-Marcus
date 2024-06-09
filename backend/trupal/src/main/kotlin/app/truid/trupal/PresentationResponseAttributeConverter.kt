package app.truid.trupal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.stereotype.Component


val mapper = jacksonObjectMapper()

@Configuration
class DBConfig : AbstractJdbcConfiguration() {
    override fun userConverters(): List<*> {
        return listOf(
            PresentationResponseAttributeConverter().presentationWriteConverter(),
            PresentationResponseAttributeConverter().presentationReadConverter()
        )
    }
}

@Component
class PresentationResponseAttributeConverter {


    fun presentationWriteConverter() = Writer()


    fun presentationReadConverter() = Reader()


    class Writer : Converter<PresentationResponse, String> {


        override fun convert(source: PresentationResponse): String {
            return mapper.writeValueAsString(source)
        }
    }

    class Reader : Converter<String, PresentationResponse> {
        override fun convert(source: String): PresentationResponse? {
            return try {
                mapper.readValue(source, PresentationResponse::class.java)
            } catch (e: RuntimeException) {

                null
            }
        }
    }
}

