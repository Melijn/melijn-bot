package resource

import api.discord.DiscordApi
import database.manager.UserCookieManager
import database.manager.UserDataManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.melijn.gen.UserCookieData
import me.melijn.gen.UserDataData
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import model.CustomResponseException
import model.TokenOwner
import org.intellij.lang.annotations.Language
import org.koin.core.component.inject
import util.CookieUtil
import util.KtorUtil.getMelijnSession
import util.KtorUtil.setMelijnSession

@Page
class Login : AbstractPage("/callback", ContentType.Text.Html) {

    private val userCookieManager by inject<UserCookieManager>()
    private val userDataManager by inject<UserDataManager>()
    private val discordApi by inject<DiscordApi>()

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
            {{ footer }}
        </html>
    """.trimIndent()


    override suspend fun render(call: ApplicationCall): String {
        var response = src
        val code = call.request.queryParameters["code"]

        if (code != null) {
            val tokenOwner = discordApi.retrieveDiscordTokenOwner(code)
            val cookie = CookieUtil.generateRandomCookie()

            linkCookieToOwner(tokenOwner, cookie)
            call.response.setMelijnSession(cookie)
            call.respondRedirect("/commands", false)
            throw CustomResponseException()
        } else {
            val cookie = call.request.getMelijnSession()

            val loginStatus = if (cookie != null && userCookieManager.isValidCookie(cookie)) {
                val userData = userCookieManager.getByIndex0(cookie)

                "Logged in :) you are: $userData"
            } else {
                "You are not logged in currently: <a href='/login'>login</a> :)"
            }

            response = response.replace("%login-state%", loginStatus)
        }

        return response
    }

    private fun linkCookieToOwner(tokenOwner: TokenOwner, cookie: String) {
        tokenOwner.oauthToken.run {
            userCookieManager.store(
                UserCookieData(
                    tokenOwner.partialDiscordUser.id, cookie, Clock.System.now().toLocalDateTime(TimeZone.UTC),
                    accessToken, tokenType, expiresIn, refreshToken, scope
                )
            )
        }
        tokenOwner.partialDiscordUser.run {
            userDataManager.store(UserDataData(id, username, discriminator, avatar))
        }
    }
}