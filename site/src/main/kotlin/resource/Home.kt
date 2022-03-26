package resource

import io.ktor.http.*
import model.AbstractPage
import model.Page
import org.intellij.lang.annotations.Language

@Page
class HomePage : AbstractPage("/", ContentType.Text.Html) {

    override val aliasRoutes = arrayOf("/home")

    @Language("html")
    override val src = """
<html lang="uk">
    <head>
        <title>Melijn Homepage</title>
        {{ head }}
    </head>
    {{ navbar }}
    <body>
        <h1>Fishies</h1>
    </body>
    {{ footer }}
</html>
"""

}


