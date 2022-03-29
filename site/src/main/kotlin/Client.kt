import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*

val httpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(JsonFeature) {
        serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.INFO
    }
    install(UserAgent) {
        agent = "Melijn Backend / 1.0.0 Website backend"
    }
    defaultRequest {
        timeout {
            connectTimeoutMillis = 4000
            requestTimeoutMillis = 4000
        }
    }
}