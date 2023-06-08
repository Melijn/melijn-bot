package snippet

import me.melijn.siteannotationprocessors.snippet.Snippet
import org.intellij.lang.annotations.Language
import java.time.ZonedDateTime
import java.util.*

@Snippet
class FooterSnippet : AbstractSnippet<Any>() {

    @Language("html")
    override val src = """
     <div class='footer'>
         <div>
             © ${ZonedDateTime.now().year}, ToxicMushroom#2068. All rights reserved.
         </div>
         <div>
             <a href='https://github.com/ToxicMushroom' target='_blank'>
                 github
             </a>
         </div>
         
     </div>
""".trimIndent()

    override val name: String = "footer"
}