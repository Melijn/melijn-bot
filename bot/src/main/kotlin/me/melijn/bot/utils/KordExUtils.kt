package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.builders.ValidationContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import dev.kord.core.event.message.MessageCreateEvent
import me.melijn.bot.utils.EnumUtil.ucc
import me.melijn.gen.Settings
import org.jetbrains.annotations.PropertyKey
import org.koin.core.component.inject

object KordExUtils {

    suspend fun CheckContext<MessageCreateEvent>.userIsOwner() {
        val botSettings by inject<Settings>()
        failIfNot("bot owner command") {
            botSettings.bot.ownerIds.split(",").any {
                it.trim() == this.event.message.author?.id?.value.toString()
            }
        }
    }

    suspend fun ChatCommandContext<*>.usedPrefix(context: Extension): String {
        return context.chatCommandRegistry.getPrefix(this.event)
    }

    /**
     * Inclusive min max int range validator, adds failIf clauses to the validationContext
     * @param name parameter name to be used in response
     * @param min minimal value for the int
     * @param max maximal value for the int
     *  **/
    fun ValidationContext<Int?>.intRange(name: String, min: Int, max: Int) {
        val length = this.value ?: return
        failIf(length < min, "$name length must be **>= $min** but was `$length`")
        failIf(length > max, "$name length must be **<= $max** but was `$length`")
    }

    /**
     * Inclusive min max string length validator, adds failIf clauses to the validationContext
     * @param name parameter name to be used in response
     * @param min minimal length of a string param
     * @param max maximal length of a string param
     *  **/
    fun ValidationContext<String?>.stringLength(name: String, min: Int, max: Int) {
        val length = this.value?.length ?: return
        failIf(length < min, "$name length must be **>= $min** characters but was `$length`")
        failIf(length > max, "$name length must be **<= $max** characters but was `$length`")
    }

    fun TranslationsProvider.tr(@PropertyKey(resourceBundle = "translations.melijn.strings") key: String, vararg replacements: Any): String =
        translate(key, "melijn.strings", replacements.asList().toTypedArray())
    fun CommandContext.tr(@PropertyKey(resourceBundle = "translations.melijn.strings") key: String, vararg replacements: Any): String =
        translationsProvider.translate(key, "melijn.strings", replacements.asList().toTypedArray())
}

interface InferredChoiceEnum : ChoiceEnum {
    override val readableName: String
        get() = this.toString().ucc()
}