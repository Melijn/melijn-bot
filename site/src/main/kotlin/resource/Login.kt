package resource

import io.ktor.application.*
import io.ktor.http.*
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import org.intellij.lang.annotations.Language

@Page
class Login : AbstractPage("/login", ContentType.Text.Html) {


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
        TODO("Not yet implemented")
    }

    private fun validateCookie(cookie: String): Boolean {
        return true
    }
}