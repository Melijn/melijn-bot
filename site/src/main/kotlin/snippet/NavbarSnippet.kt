package snippet

import me.melijn.siteannotationprocessors.snippet.Snippet
import org.intellij.lang.annotations.Language

@Snippet
class NavbarSnippet : AbstractSnippet<Any>() {

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
     </div>
""".trimIndent()

    override val name: String = "navbar"
}
