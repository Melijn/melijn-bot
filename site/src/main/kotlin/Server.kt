import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import model.AbstractPage
import render.SourceRenderer
import resource.HomePage
import resource.Style

val abstractPages = setOf<AbstractPage>(
    HomePage(),
    Style()
)

fun main() {
    embeddedServer(Netty, 9090) {
        install(ContentNegotiation) {
            json()
        }
        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Delete)
            anyHost()
        }
        install(Compression) {
            gzip()
        }
        routing {
            for (page in abstractPages) {
                route(page.route) {
                    get {
                        val done = SourceRenderer.render(page.src)

                        call.respondText(done, page.contentType, HttpStatusCode.OK)
                    }
                }
            }
        }
    }.start(wait = true)
}