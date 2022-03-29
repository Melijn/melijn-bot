package resource

import database.manager.UserCookieManager
import httpClient
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.impl.DefaultJwtBuilder
import io.jsonwebtoken.security.Keys
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import me.melijn.gen.Settings
import me.melijn.gen.UserCookieData
import me.melijn.kordkommons.logger.Log
import me.melijn.siteannotationprocessors.page.Page
import model.*
import org.intellij.lang.annotations.Language
import org.koin.core.component.inject
import kotlin.random.Random

@Page
class Login : AbstractPage("/callback", ContentType.Text.Html) {

    val settings by inject<Settings>()
    val userCookieManager by inject<UserCookieManager>()
    val logger by Log

    @Language("js")
    val js: String = """
        console.log(item);
    """.trimIndent()

    @Language("html")
    override val src: String = """
        <html lang="uk">
            <head>
                <title>Melijn Login</title>
                {{ head }}
            </head>
            {{ navbar }}
            <body>
                <h1>Login Status</h1>
                <h2 id='login-state'>%login-state%</h2>
            </body>
            <script>
            %js%
            </script>
            {{ footer }}
        </html>
    """.trimIndent()


    override suspend fun render(call: ApplicationCall): String {
        val extraJs = StringBuilder()
        var source = src
        val code = call.request.queryParameters["code"]

        val filledJs = if (code != null) {
            val tokenOwner = getOwner(code)
            val cookie = generateRandomCookie()

            val expireDays = tokenOwner.oauthToken.expiresIn / 60 / 60 / 24
            linkCookieToOwner(tokenOwner, cookie)
            extraJs.appendLine("setCookie('jwt', '${cookie}', $expireDays)")
            extraJs.appendLine("window.location.replace('/commands');")
            extraJs.toString()
        } else {
            val cookie = call.request.cookies["jwt", CookieEncoding.RAW]
            val loginStatus = if (cookie != null && validateCookie(cookie)) {
                val userData = userCookieManager.getByIndex0(cookie).firstOrNull()
                "Logged in :) you are: $userData"
            } else {
                extraJs.appendLine("document.cookie = '';")

                "Uh oh stinky, your cookie was invalid or expired, please <a href='/login'>login</a> again :)"
            }
            source = source.replace("%login-state%", loginStatus)
            extraJs.toString()
        }

        source = source.replace("%js%", filledJs)
        return source
    }

    private fun linkCookieToOwner(tokenOwner: TokenOwner, cookie: String) {
        userCookieManager.store(
            UserCookieData(
                tokenOwner.partialDiscordUser.id,
                cookie,
                Clock.System.now().toLocalDateTime(TimeZone.UTC),
                tokenOwner.oauthToken.accessToken,
                tokenOwner.oauthToken.tokenType,
                tokenOwner.oauthToken.expiresIn,
                tokenOwner.oauthToken.refreshToken,
                tokenOwner.oauthToken.scope,
            )
        )
    }

    private fun generateRandomCookie(): String {
        val key = Keys.hmacShaKeyFor(settings.service.jwtKey.toByteArray())
        val prevUid = lastSubId++
        val newUid = (prevUid + 1).toString() + Random.nextLong()

        return DefaultJwtBuilder()
            .setPayload(newUid)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    companion object {
        var lastSubId = System.currentTimeMillis()
    }

    private suspend fun getOwner(code: String): TokenOwner {
        val oauth = settings.discordOauth
        val encodedUrlParams = Parameters.build {
            append("client_id", oauth.botId)
            append("client_secret", oauth.botSecret)
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", oauth.redirectUrl)
        }.formUrlEncode()


        val tokenResponse = try {
            httpClient.post<Oauth2Token>("${oauth.discordApiHost}/oauth2/token") {
                this.body = encodedUrlParams
                headers {
                    append("Content-Type", "application/x-www-form-urlencoded")
                }
            }
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

        val user = httpClient.get<PartialDiscordUser>("${oauth.discordApiHost}/users/@me") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }

        return TokenOwner(
            Oauth2Token(token, tokenResponse.tokenType, tokenResponse.expiresIn, tokenResponse.refreshToken, scope),
            user
        )
    }

    private fun validateCookie(cookie: String): Boolean {
        val userCookie = userCookieManager.getByIndex0(cookie).firstOrNull()
        if (userCookie != null) return true
        return false
    }
}