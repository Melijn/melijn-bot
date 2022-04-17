package resource

import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.serialization.json.*
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import org.intellij.lang.annotations.Language

@Page
class Commands : AbstractPage("/commands", ContentType.Text.Html) {

    @Language("html")
    override val src: String = """
<!DOCTYPE html>
<html lang="uk">
    <head>
        <title>Melijn Homepage</title>
        {{ head }}
    </head>
    {{ navbar }}
    <body>
        <h1>Melijn Commands</h1>
        <form action='/commands' target='_self' method='get' autocomplete='off'>
            <fieldset>
                <legend>Filters</legend>
                 <label for="category">Category:</label>
                <select id="category" name="c">
                    <option value=""></option>
                    %options%
                </select> 
                <br>
                <label for="commands-search">Command:</label><br>
                <input id='commands-search' type='text' name='q' placeholder='Find a command' value='%q%'>
                <input type='submit' value='Filter'><a href='/commands'><button type='button'>Reset</button></a>
            </fieldset>
        </form>
        <div class='categories'>
            {{ commands }}
        </div>
    </body>
    {{ footer }}
</html>
    """.trimIndent()

    override suspend fun render(call: ApplicationCall): String {
        val json = httpClient.get("https://vps2-melijn.bitflow.dev/commands").body<JsonObject>()
        val query = call.request.queryParameters["q"]?.escapeHTML()?.takeIf { it.isNotBlank() }
        val categoryQuery = call.request.queryParameters["c"]?.escapeHTML()?.takeIf { it.isNotBlank() }
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
            Sequence { perms.iterator() }.map { it.jsonPrimitive.content }.toList(),
            query,
            categoryQuery
        )

        val random = System.currentTimeMillis()
        sb.appendLine("""<div>%count$random% commands</div>""")

        val optionBuilder = StringBuilder()
        for (category in json.keys.filterNot { it == "extra" }) {
            val commands = json[category]!!.jsonArray
            for (command in commands) {
                addChild("", command, extraInfo, category, sb)
            }
            optionBuilder.appendLine("<option value='${category}' ${if (category.equals(extraInfo.categoryQuery, true)) "selected='selected'" else ""}>${category.lowercase()}</option>")
        }

        val commands = sb.toString().replaceFirst("%count$random%", "${extraInfo.counter}")

        return src
            .replace("{{ commands }}", commands)
            .replace("%options%", optionBuilder.toString())
            .replace("%q%", extraInfo.query ?: "")
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

        val fieldMap = mapOf(
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

        if (extraInfo.categoryQuery == null || category.equals(extraInfo.categoryQuery, true)) {
            if (extraInfo.query == null
                || name.contains(extraInfo.query, true)
                || aliases.any { it.contains(extraInfo.query, true) }
                || fieldMap.entries.any { it.value.contains(extraInfo.query, true) }
            ) {
                sb.appendLine(value)
                extraInfo.counter++
            }
        }

        for (cmd in children) {
            addChild("$name ", cmd, extraInfo, category, sb)
        }
    }
}

data class ExtraInfo(
    val runConditions: List<RunCondition>,
    val discordPermissions: List<String>,
    val query: String?,
    val categoryQuery: String?,
    var counter: Int = 0
) {
    data class RunCondition(
        val name: String,
        val description: String
    )
}









