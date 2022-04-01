package snippet

import database.manager.UserCookieManager
import io.ktor.application.*
import me.melijn.siteannotationprocessors.snippet.Snippet
import org.intellij.lang.annotations.Language
import org.koin.core.component.inject
import util.CookieUtil
import util.KtorUtil.getMelijnSession

@Snippet
class NavbarSnippet : AbstractSnippet<Any>() {

    val userCookieManager: UserCookieManager by inject()

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
         <div>
             <a href='/login'>
                 Login
             </a>
         </div>
     </div>
""".trimIndent()

    override val name: String = "navbar"

    override suspend fun render(call: ApplicationCall, prop: Any): String {
        return src.replace("%loginDependent%", getLoginDependent(call))
    }

    private fun getLoginDependent(call: ApplicationCall): String {
        val valid = call.request.getMelijnSession()?.let { cookie ->
            userCookieManager.getByIndex0(cookie)?.let { userCookieData ->
                CookieUtil.isValid(userCookieData)
            }
        } ?: false
        return if (valid) {
            @Language("html")
            val loggedIn = """
                 <div>
                     <a href='/logout'>
                         Logout
                     </a>
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
