package api.discord

import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerializationException
import me.melijn.ap.injector.Inject
import me.melijn.gen.Settings
import model.FriendlyHttpException
import model.Oauth2Token
import model.PartialDiscordUser
import model.TokenOwner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Inject
class DiscordApi : KoinComponent {

    private val settings by inject<Settings>()

    /**
     * Fetches oauth2 tokens, then fetches the users/@me response
     *
     * @param code the oauth2 authorization GET code param value
     * @return TokenOwner instance
     *
     * @throws FriendlyHttpException unable to fetch discord oauth2 tokens from the code
     * @throws FriendlyHttpException the code does not contain the scopes: (identify, guilds)
     */
    suspend fun retrieveDiscordTokenOwner(code: String): TokenOwner {
        val oauth = settings.discordOauth
        val encodedUrlParams = Parameters.build {
            append("client_id", oauth.botId)
            append("client_secret", oauth.botSecret)
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", oauth.redirectUrl)
        }.formUrlEncode()


        val tokenResponse = try {
            httpClient.post("${oauth.discordApiHost}/oauth2/token") {
                setBody(encodedUrlParams)
                headers {
                    append("Content-Type", "application/x-www-form-urlencoded")
                }
            }.body<Oauth2Token>()
        } catch (t: SerializationException) {
            throw FriendlyHttpException(HttpStatusCode.BadRequest, "Your oauth code was probably invalid")
        }

        val token = tokenResponse.accessToken

        val scope = tokenResponse.scope
        val required = listOf("identify", "guilds")
        if (!required.all { scope.contains(it) }) {
            throw FriendlyHttpException(
                HttpStatusCode.BadRequest,
                "Missing one or more of the following discord scopes in the code parameter you supplied." +
                    "\nRequired scopes: $required"
            )
        }

        val user = httpClient.get("${oauth.discordApiHost}/users/@me") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
            .body<PartialDiscordUser>()

        return TokenOwner(
            Oauth2Token(token, tokenResponse.tokenType, tokenResponse.expiresIn, tokenResponse.refreshToken, scope),
            user
        )
    }

}