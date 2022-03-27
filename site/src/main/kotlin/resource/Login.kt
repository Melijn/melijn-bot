package resource

import io.ktor.http.*
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import org.intellij.lang.annotations.Language

@Page
class Login : AbstractPage("/login", ContentType.Text.Html) {


    @Language("js")
    val js: String = """
        localStorage.setItem("lastname", "Smith");
        console.log(localStorage.getItem("lastname"));
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
                <h2 id='login-state'>Checking session...</h2>
            </body>
            <script>
            $js
            </script>
            {{ footer }}
        </html>
    """.trimIndent()

}