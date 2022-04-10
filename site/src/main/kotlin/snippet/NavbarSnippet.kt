package snippet

import database.manager.UserCookieManager
import database.manager.UserDataManager
import io.ktor.server.application.*
import me.melijn.gen.UserDataData
import me.melijn.siteannotationprocessors.snippet.Snippet
import model.Oauth2Token
import model.PartialDiscordUser
import model.TokenOwner
import org.intellij.lang.annotations.Language
import org.koin.core.component.inject
import util.CookieUtil
import util.KtorUtil.getMelijnSession

@Snippet
class NavbarSnippet : AbstractSnippet<Any>() {

    private val userCookieManager: UserCookieManager by inject()
    private val userDataManager: UserDataManager by inject()

    @Language("html")
    override val src = """
     <div class='navbar'>
         <div>
             <a href='/'>
                 Home
             </a>
         </div>
         <div>
             <a href='/commands'>
                 Commands
             </a>
         </div>
         %loginDependent%
     </div>
""".trimIndent()

    override val name: String = "navbar"

    override suspend fun render(call: ApplicationCall, prop: Any): String {
        return src.replace("%loginDependent%", getLoginDependent(call))
    }

    private fun getLoginDependent(call: ApplicationCall): String {
        val cookieData = call.request.getMelijnSession()?.let { cookie ->
            userCookieManager.getByIndex0(cookie)?.let { userCookieData ->
                val cookieData = userCookieData.takeIf { CookieUtil.isValid(userCookieData) }
                cookieData?.run {
                    val userData = userDataManager.getById(userId)
                        ?: UserDataData(userId, "missing", "0000", null)
                    TokenOwner(
                        Oauth2Token(token, tokenType, expiresInSeconds, refreshToken, scope),
                        PartialDiscordUser(userId, userData.username, userData.discriminator, userData.avatarUrl)
                    )
                }
            }
        }
        return if (cookieData != null) {
            @Language("html")
            val loggedIn = """
                <div>
                    <a href='/dashboard'>Dashboard</a>
                </div>
                <div>
                    <a href='/logout'>Logout</a>
                </div>
            """.trimIndent()
            loggedIn
        } else {
            @Language("html")
            val notLoggedIn = """
                 <div>
                     <a href='/login'>
                         Login
                     </a>
                 </div>
            """.trimIndent()
            notLoggedIn
        }
    }
}
