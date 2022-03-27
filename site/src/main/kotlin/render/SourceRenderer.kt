package render

import kotlinx.coroutines.runBlocking
import snippet.AbstractSnippet

object SourceRenderer {

    val registeredSnippets = HashMap<String, AbstractSnippet<Any>>()

    suspend fun render(src: String): String {
        return src.replace("\\{\\{\\s* (\\w+) \\s*}}".toRegex()) { match ->
            val snippetName = match.groups[1]!!.value
            runBlocking {
                registeredSnippets[snippetName]?.render(src) ?: "snippet \"$snippetName\" is unregistered"
            }
        }
    }

}