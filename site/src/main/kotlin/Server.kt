import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import me.melijn.ap.createtable.CreateTableInterface
import me.melijn.ap.injector.InjectorInterface
import me.melijn.gen.Settings
import me.melijn.kordkommons.database.ConfigUtil
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.logger.Log
import me.melijn.kordkommons.logger.logger
import me.melijn.kordkommons.redis.RedisConfig
import me.melijn.kordkommons.utils.ReflectUtil
import model.AbstractPage
import model.PageInterface
import model.SnippetsInterface
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import render.SourceRenderer
import render.SourceRenderer.render
import util.JavaResourcesUtil

val logger = Log.logger()

fun main() {
    initKoin()

    registerSnippets()
    val abstractPages = fetchPages()

    initializeDatabase()

    val port = Settings.service.port
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
        install(Authentication) {
            oauth("auth-oauth-discord") {
                urlProvider = { Settings.discordOauth.redirectUrl }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "discord",
                        authorizeUrl = "https://discord.com/oauth2/authorize",
                        accessTokenUrl = "https://discord.com/api/v8/oauth2/token",
                        requestMethod = HttpMethod.Post,
                        clientId = Settings.discordOauth.botId,
                        clientSecret = Settings.discordOauth.botSecret,
                        defaultScopes = listOf("identify", "guilds")
                    )
                }
                client = httpClient
            }
        }
        install(StatusPages) {

            status(HttpStatusCode.NotFound) {
                call.respond(
                    TextContent(
                        "${it.value} ${it.description}",
                        ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                        it
                    )
                )
            }
        }
        install(Compression) {
            gzip()
        }
        routing {
            for (page in abstractPages) {
                route(page.route) {
                    get {
                        render(page)
                    }
                }
            }
            authenticate("auth-oauth-discord") {
                get("/login") {
                    // Redirects to 'authorizeUrl' automatically
                }
            }
            route("static") {
                var resourceCount = 0
                JavaResourcesUtil.onEachResource("/static") { relPath, file ->
                    if (file.isFile) {
                        resourceCount++
                        get(relPath.replace("\\", "/")) {
                            call.respondFile(file)
                        }
                    }
                }
                logger.info { "Registered $resourceCount resources" }
            }
        }

    }.start(wait = true)
}

fun initKoin() {
    startKoin {
        loadKoinModules(module {
            single { Settings } bind Settings::class
        })
        val injected = ReflectUtil.getInstanceOfKspClass<InjectorInterface>("me.melijn.gen", "InjectionKoinModule")
        loadKoinModules(injected.module)
    }
}

fun initializeDatabase() {
    val hikariConfig = Settings.database.run {
        ConfigUtil.generateDefaultHikariConfig(host, port, name, user, pass)
    }
    val redisConfig = Settings.redis.run { RedisConfig(enabled, host, port, user, pass) }
    val createTableInterface = ReflectUtil.getInstanceOfKspClass<CreateTableInterface>(
        "me.melijn.gen", "CreateTablesModule"
    )
    val driverManager = DriverManager(hikariConfig, redisConfig) { createTableInterface.createTables() }
    loadKoinModules(module {
        single { driverManager } bind DriverManager::class
    })
}

private fun fetchPages(): List<AbstractPage> {
    val abstractPages = ReflectUtil.getInstanceOfKspClass<PageInterface>("me.melijn.gen", "Pages").pages
    logger.info { "Registered ${abstractPages.size} pages" }
    return abstractPages
}

private fun registerSnippets() {
    val snippets = ReflectUtil.getInstanceOfKspClass<SnippetsInterface>("me.melijn.gen", "Snippets").snippets
    for (snippet in snippets) {
        SourceRenderer.registeredSnippets[snippet.name] = snippet
    }
    logger.info { "Registered ${snippets.size} snippets" }
}