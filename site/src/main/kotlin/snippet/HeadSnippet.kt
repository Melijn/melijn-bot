package snippet

import me.melijn.siteannotationprocessors.injector.Inject
import org.intellij.lang.annotations.Language

@Inject
class HeadSnippet : AbstractSnippet<Any>() {

    @Language("html")
    override val src = """
     <link rel="stylesheet" href="style.css"> 
""".trimIndent()

}

