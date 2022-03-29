package snippet

import me.melijn.siteannotationprocessors.snippet.Snippet
import org.intellij.lang.annotations.Language

@Snippet
class HeadSnippet : AbstractSnippet<Any>() {

    @Language("html")
    override val src = """
        
<link rel="stylesheet" href="style.css"> 
<link rel="icon" type="image/png" href="/static/fabicon.png">
<script type="text/javascript" src="/static/cookies.js"></script>

""".trimIndent()

    override val name: String = "head"

}

