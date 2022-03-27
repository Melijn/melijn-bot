import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*

val httpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(JsonFeature)
    install(UserAgent) {
        agent = "Melijn Backend / 1.0.0 Website backend"
    }
    defaultRequest {
        this.timeout {
            this.connectTimeoutMillis = 4000
            this.requestTimeoutMillis = 4000
        }
    }
}