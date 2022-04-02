package resource

import httpClient
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.*
import me.melijn.siteannotationprocessors.page.Page
import me.melijn.siteannotationprocessors.snippet.Snippet
import model.AbstractPage
import org.intellij.lang.annotations.Language
import snippet.AbstractSnippet

@Page
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

@Snippet
class CommandsSnippet : AbstractSnippet<Any>() {

    override val name: String = "commands"
    override val src: String = ""

    override suspend fun render(call: ApplicationCall, prop: Any): String {
        val json = httpClient.get<JsonObject>("https://vps2-melijn.bitflow.dev/commands")
        val sb = StringBuilder()
        val extraObject = json["extra"]?.jsonObject
        val runConditions = extraObject?.get("runconditions")?.jsonArray ?: JsonArray(emptyList())
        val perms = extraObject?.get("discordpermissions")?.jsonArray ?: JsonArray(emptyList())
        val extraInfo = ExtraInfo(
            Sequence { runConditions.iterator() }.map {
                val runConditionInfoArr = it.jsonArray
                ExtraInfo.RunCondition(
                    runConditionInfoArr[0].jsonPrimitive.content,
                    runConditionInfoArr[1].jsonPrimitive.content
                )
            }.toList(),
            Sequence { perms.iterator() }.map { it.jsonPrimitive.content }.toList()
        )

        for (category in json.keys.filterNot { it == "extra" }) {
            val commands = json[category]!!.jsonArray
            for (command in commands) {
                addChild("", command, extraInfo, category, sb)
            }
        }
        return sb.toString()
    }

    private fun addChild(
        namePrefix: String,
        command: JsonElement,
        extraInfo: ExtraInfo,
        category: String,
        sb: StringBuilder
    ) {
        val cmdArr = command.jsonArray
        val name = namePrefix + cmdArr[0].jsonPrimitive.content
            .escapeHTML()
        val description = cmdArr[1].jsonPrimitive.content
            .escapeHTML()
        val syntax = cmdArr[2].jsonPrimitive.content
            .escapeHTML()
            .replace("%prefix%", ">".escapeHTML())
        val aliases = cmdArr[3].jsonArray.map { it.jsonPrimitive.content.escapeHTML() }
        val argHelp = cmdArr[4].jsonPrimitive.content
            .replace("%prefix%", ">")
            .escapeHTML()
            .replace("`([^`]*)`".toRegex()) { res ->
                "<code>${res.groups[1]!!.value}</code>"
            }.replace("\n", "<br>")
        val discordChannelPermissions = cmdArr[5].jsonArray.joinToString {
            extraInfo.discordPermissions[it.jsonPrimitive.content.toInt()]
        }
        val discordPermissions = cmdArr[6].jsonArray.joinToString {
            extraInfo.discordPermissions[it.jsonPrimitive.content.toInt()]
        }
        val requirements = cmdArr[7].jsonArray.joinToString {
            extraInfo.runConditions[it.jsonPrimitive.content.toInt()].name
        }
        val children = cmdArr[8].jsonArray
        val help = cmdArr[9].jsonPrimitive.content
        val examples = cmdArr[10].jsonPrimitive.content
            .replace("%prefix%", ">")
            .escapeHTML()
            .replace("`([^`]*)`".toRegex()) { res ->
                "<code>${res.groups[1]!!.value}</code>"
            }
            .replace("\n", "<br>")

        val fieldMap = mapOf<String, String>(
            "Description" to description,
            "Syntax" to syntax,
            "Argument Info" to argHelp,
            "Requirements" to requirements,
            "Discord Permissions" to discordPermissions,
            "Discord Channel Permissions" to discordChannelPermissions,
            "Help" to help,
            "Examples" to examples
        )

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
                                ${
            fieldMap.filter { it.value.isNotBlank() }.entries.joinToString("") {
                @Language("html")
                val fieldEntry =
                    """
                        <tr>
                            <td class='type'>${it.key}</td>
                            <td colspan='3'>${it.value}</td>
                        </tr>
                    """.trimIndent()
                fieldEntry
            }
        }
                                </tbody>
                            </table> 
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()

        sb.appendLine(value)

        for (cmd in children) {
            addChild("$name ", cmd, extraInfo, category, sb)
        }
    }
}

data class ExtraInfo(
    val runConditions: List<RunCondition>,
    val discordPermissions: List<String>
) {
    data class RunCondition(
        val name: String,
        val description: String
    )
}









