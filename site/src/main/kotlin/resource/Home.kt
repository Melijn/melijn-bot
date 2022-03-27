package resource

import io.ktor.http.*
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import org.intellij.lang.annotations.Language
import util.BOT_GITHUB_LINK
import util.BOT_GITHUB_SELFHOST_GUIDE
import util.BOT_INVITE_LINK

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
        <h1>Melijn</h1>
        <h2>An opensource multipurpose <a href='https://discord.com'>discord</a> bot</h2>
        <p>
            Here is an <a href='${BOT_INVITE_LINK}'>invite</a> for the bot. 
            If you're interested you can <a href='${BOT_GITHUB_SELFHOST_GUIDE}'>selfhost</a> it with docker 
            or look at the code on <a href='${BOT_GITHUB_LINK}'>github</a>.
        </p>
    </body>
    {{ footer }}
</html>
"""

}


