package resource

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import model.CustomResponseException
import org.intellij.lang.annotations.Language
import util.KtorUtil.clearMelijnSession
import util.KtorUtil.getMelijnSession

@Page
class Logout : AbstractPage("/logout", ContentType.Text.Html) {

    @Language("html")
    override val src: String = """
        <html lang="uk">
            <head>
                <title>Melijn Login</title>
                {{ head }}
            </head>
            {{ navbar }}
            <body>
                <h1>Logged out :)</h1>
            </body>
            {{ footer }}
        </html>
    """.trimIndent()

    override suspend fun render(call: ApplicationCall): String {
        if (call.request.getMelijnSession() != null) {
            call.response.clearMelijnSession()
            call.respondRedirect("/logout", false)
            throw CustomResponseException()
        }
        return super.render(call)
    }

}