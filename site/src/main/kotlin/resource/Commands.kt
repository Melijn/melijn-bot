package resource

import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.melijn.siteannotationprocessors.injector.Inject
import model.AbstractPage
import org.intellij.lang.annotations.Language
import snippet.AbstractSnippet

class Commands : AbstractPage("/commands", ContentType.Text.Html) {

    @Language("html")
    override val src: String = """
<html lang="uk">
    <head>
        <title>Melijn Homepage</title>
        {{ head }}
    </head>
    {{ navbar }}
    <body>
        <h1>Melijn Commands</h1>
        <input id='commands-search' type='text' placeholder='Find a command'>
        <div class='categories'>
            {{ commands }}
        </div>
    </body>
    {{ footer }}
</html>
    """.trimIndent()
}

@Inject
class CommandsSnippet : AbstractSnippet<Any>() {
    override val name: String = "commands"
    override val src: String = ""

    override suspend fun render(prop: Any): String {
        val json = httpClient.get<JsonObject>("https://vps2-melijn.bitflow.dev/commands")
        val sb = StringBuilder()
        val extra = json["extra"]
        for (category in json.keys.filterNot { it == "extra" }) {
            val commands = json[category]!!.jsonArray
            for (command in commands) {
                val cmdArr = command.jsonArray
                val name = cmdArr[0].jsonPrimitive.content
                val description = cmdArr[1].jsonPrimitive.content
                val syntax = cmdArr[2].jsonPrimitive.content
                    .replace("%prefix%", ">".escapeHTML())
                val aliases = cmdArr[3].jsonArray.map { it.jsonPrimitive.content }
                val argHelp = cmdArr[4].jsonPrimitive.content
                    .replace("`([^`]*)`".toRegex()) { res ->
                        "<code>${res.groups[1]!!.value}</code>"
                    }

                val entryTitle = name + aliases.joinToString("") { " | $it" }

                @Language("html")
                val value = """
                    <div id='${category} visible'> 
                        <div>
                            <div class='cmd visible accordion'>
                                <div class='accordion-button'>$entryTitle</div>
                                <div class='accordion-content'>
                                    <table>
                                        <tbody>
                                            <tr>
                                                <td class='type'>Description</td>
                                                <td colspan='3'>$description</td>
                                            </tr>
                                            <tr>
                                                <td class='type'>Syntax</td>
                                                <td colspan='3'>$syntax</td>
                                            </tr>
                                            <tr>
                                                <td class='type'>Arghelp</td>
                                                <td colspan='3'>$argHelp</td>
                                            </tr>
                                        </tbody>
                                    </table> 
                                </div>
                            </div>
                        </div>
                    </div>
                """.trimIndent()

                sb.appendLine(value)
            }

        }
        return sb.toString()
    }
}













