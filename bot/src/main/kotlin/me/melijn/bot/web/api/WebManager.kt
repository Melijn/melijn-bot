package me.melijn.bot.web.api

import com.apollographql.apollo.ApolloClient
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import me.melijn.ap.injector.Inject
import me.melijn.gen.Settings
import okhttp3.OkHttpClient
import org.koin.java.KoinJavaComponent.inject
import java.net.InetSocketAddress
import java.net.Proxy

@Inject
class WebManager {

    val settings by inject<Settings>(Settings::class.java)
    val objectMapper by inject<ObjectMapper>(ObjectMapper::class.java)

    val commonClientConfig: HttpClientConfig<OkHttpConfig>.() -> Unit = {
        expectSuccess = false
        install(ContentNegotiation) {
            jackson()
        }
        install(UserAgent) {
            agent = "Melijn / 3.0.0 Discord bot"
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

    val aniListApolloClient: ApolloClient = ApolloClient.builder()
        .serverUrl("https://graphql.anilist.co")
        .okHttpClient(OkHttpClient())
        .build()

    var spotifyApi: MySpotifyApi? = null
    val animalImageApi: AnimalImageApi = AnimalImageApi(httpClient)

    init {
        if (settings.api.spotify.clientId.isNotBlank() && settings.api.spotify.password.isNotBlank()) {
            spotifyApi = MySpotifyApi(settings.api.spotify)
        }
    }
}