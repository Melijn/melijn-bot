package render

object SourceRenderer {

    val registeredSnippets = HashMap<String, String>()

    fun render(src: String): String {
        return src.replace("\\{\\{\\s* (\\w+) \\s*}}".toRegex()) { match ->
            val snippetName = match.groups[1]!!.value
            registeredSnippets[snippetName] ?: "snippet \"$snippetName\" is unregistered"
        }
    }

}