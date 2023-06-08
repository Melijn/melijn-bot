package me.melijn.bot.web.api

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.kotlindiscord.kord.extensions.utils.getKoin
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import me.melijn.ap.injector.Inject
import me.melijn.gen.Settings
import me.melijn.kordkommons.logger.logger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy

@Inject
class WebManager {

    val settings by getKoin().inject<Settings>()
    private val logger = logger()

    val commonClientConfig: HttpClientConfig<OkHttpConfig>.() -> Unit = {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                coerceInputValues = true
            })
        }
        install(UserAgent) {
            agent = "Melijn#0111 / 3.0.0 Discord bot"
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    val httpClient = HttpClient(OkHttp, commonClientConfig)
    val proxiedHttpClient = HttpClient(OkHttp) {
        commonClientConfig(this)
        this.engine {
            val clientBuilder = OkHttpClient.Builder()
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(settings.httpProxy.host, settings.httpProxy.port))
            val client = clientBuilder.proxy(proxy)
                .build()
            this.preconfigured = client
        }
    }

    val okHttpClient = OkHttpClient.Builder().apply {
        addInterceptor(
            HttpLoggingInterceptor { logger.debug(it) }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
    }.build()

    val aniListApolloClient: ApolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .okHttpClient(okHttpClient)
        .build()

    var spotifyApi: MySpotifyApi? = null
    val animalImageApi: AnimalImageApi = AnimalImageApi(httpClient)

    init {
        if (settings.api.spotify.clientId.isNotBlank() && settings.api.spotify.password.isNotBlank()) {
            spotifyApi = MySpotifyApi(settings.api.spotify)
        }
    }
}