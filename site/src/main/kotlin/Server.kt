import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import me.melijn.kordkommons.logger.Log
import me.melijn.kordkommons.logger.logger
import me.melijn.kordkommons.utils.ReflectUtil
import model.AbstractPage
import model.SnippetsInterface
import render.SourceRenderer
import resource.Commands
import resource.HomePage
import resource.Style

val abstractPages = setOf<AbstractPage>(
    HomePage(),
    Commands(),
    Style()
)

val port = 9090
val logger = Log.logger()

fun main() {
    val snippets = ReflectUtil.getInstanceOfKspClass<SnippetsInterface>("me.melijn.gen", "Snippets").snippets
    for (snippet in snippets) {
        SourceRenderer.registeredSnippets[snippet.name] = snippet
    }


    logger.info { "http://localhost:$port" }

    embeddedServer(Netty, port) {
        install(ContentNegotiation) {
            json()
        }
        install(AutoHeadResponse)
        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Delete)
            anyHost()
        }
        install(StatusPages){

            status(HttpStatusCode.NotFound) {
                call.respond(TextContent("${it.value} ${it.description}", ContentType.Text.Plain.withCharset(Charsets.UTF_8), it))
            }
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