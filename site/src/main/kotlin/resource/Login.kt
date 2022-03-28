package resource

import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithm
import httpClient
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.http.*
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import okhttp3.internal.http2.Settings
import org.intellij.lang.annotations.Language
import org.koin.core.component.inject
import java.util.*
import kotlin.random.Random.Default.nextLong

@Page
class Login : AbstractPage("/login", ContentType.Text.Html) {

    val settings by inject<Settings>()

    @Language("js")
    val js: String = """
        if (!loggedIn) {
            
        }
        
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
        var extraJs = StringBuilder()
        var source = src
        val code = call.request.queryParameters["code"]
        if (code != null) {
            val cookie = codeToCookie(code)
            if (cookie == null) {
                source = source.replace("%login-state%", "Invalid code parameter, try again")
            }
        }

        val cookie = call.request.cookies["jwt", CookieEncoding.RAW]
        val filledJs = if (cookie == null || !validateCookie(cookie)) {
            "let loggedIn = false\n$js"
        } else "let loggedIn = true\n$js"
        source = source.replace("%js%", filledJs)
        return source
    }

    private fun codeToCookie(code: String): String? {

        val oauth = settings.discordOauth
        val encodedUrlParams = Parameters.build {
            append("client_id", oauth.botId)
            append("client_secret", oauth.botSecret)
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", oauth.redirectUrl + routePart)
        }.formUrlEncode()


        try {
            val tokenResponse = httpClient.post<String>("${context.discordApi}/oauth2/token") {
                this.body = encodedUrlParams
                headers {
                    append("Content-Type", "application/x-www-form-urlencoded")
                }
            }.json()

            val token = tokenResponse.get("access_token")?.asText()
            if (token == null) {
                logger.info("unsuccessful login, discord response: " + tokenResponse.toPrettyString())
                context.replyError(HttpStatusCode.BadRequest, "Unsuccessful login, contact support if this keeps occurring")
                return
            }
            val refreshToken = tokenResponse.get("refresh_token").asText()
            val lifeTime = tokenResponse.get("expires_in").asLong()

            val scope = tokenResponse.get("scope").asText()
            val required = listOf("identify", "guilds")
            if (required.any { !scope.contains(it) }) {
                context.replyError(
                    HttpStatusCode.BadRequest,
                    "Missing one or more of the following discord scopes in the code parameter you supplied." +
                        "\nRequired scopes: $required"
                )
                return
            }

            val user = httpClient.get<String>("${context.discordApi}/users/@me") {
                this.headers {
                    append("Authorization", "Bearer $token")
                }
            }.json()

            val avatar = user.get("avatar").asText()
            val userName = user.get("username").asText()
            val discriminator = user.get("discriminator").asText()
            val userId = user.get("id").asLong()
            val tag = "$userName#$discriminator"

            val key = Keys.hmacShaKeyFor(context.settings.restServer.jwtKey)
            val prevUid = lastSubId++
            val newUid = (prevUid + 1).toString() + Random.nextLong()

            val jwt = DefaultJwtBuilder()
                .setPayload(newUid)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact()

            val json = objectMapper.createObjectNode()
                .put("jwt", jwt)
                .put("lifeTime", lifeTime)
                .put("avatar", avatar)
                .put("tag", tag)

            val buffer = ByteBuffer.allocate(Long.SIZE_BYTES * 2)
                .putLong(prevUid)
                .putLong(Random.nextLong())

            context.daoManager.sessionWrapper.setSessionInfo(
                jwt, SessionInfo(
                    Base58.encode(buffer.array()),
                    context.now + lifeTime,
                    userId,
                    token,
                    refreshToken
                )
            )

            context.daoManager.userWrapper.setUserInfo(
                jwt, UserInfo(
                    userId,
                    userName,
                    discriminator,
                    avatar
                ),
                lifeTime
            )

            context.replyJson(json.toString())

        } catch (t: Throwable) {
            t.printStackTrace()
            context.replyError(HttpStatusCode.InternalServerError)
        }

    }

    private fun validateCookie(cookie: String): Boolean {
        return true
    }
}