package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.annotations.ExtensionDSL
import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.Argument
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommand
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.SingleConverter
import com.kotlindiscord.kord.extensions.commands.converters.Validator
import com.kotlindiscord.kord.extensions.commands.converters.builders.ValidationContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.modules.annotations.converters.Converter
import com.kotlindiscord.kord.extensions.modules.annotations.converters.ConverterType
import com.kotlindiscord.kord.extensions.parser.StringParser
import dev.kord.core.entity.interaction.OptionValue
import dev.kord.core.entity.interaction.StringOptionValue
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.interaction.OptionsBuilder
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import me.melijn.bot.utils.EnumUtil.ucc
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.gen.Settings
import me.melijn.kordkommons.utils.SPACE_PATTERN
import me.melijn.kordkommons.utils.escapeMarkdown
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

    @JvmName("userIsOwnerChatInputCommandInteractionCreateEvent")
    suspend fun CheckContext<ChatInputCommandInteractionCreateEvent>.userIsOwner() {
        val botSettings by inject<Settings>()
        failIfNot("bot owner command") {
            botSettings.bot.ownerIds.split(",").any {
                it.trim() == this.event.interaction.user.id.value.toString()
            }
        }
    }

    suspend fun ChatCommandContext<*>.usedPrefix(context: Extension): String {
        return context.chatCommandRegistry.getPrefix(this.event)
    }

    /**
     * Int value validator, adds failIf clauses to the validationContext
     *
     * @param name parameter name to be used in response
     * @param min minimum value for the int
     *  **/
    fun ValidationContext<Int?>.atLeast(name: String, min: Int) {
        val size = this.value ?: return
        failIf(size < min, "$name must be **>= $min** but was `$size`")
    }

    /**
     * Int value validator, adds failIf clauses to the validationContext
     *
     * @param name parameter name to be used in response
     * @param max maximum value for the int
     *  **/
    fun ValidationContext<Int?>.atMost(name: String, max: Int) {
        val size = this.value ?: return
        failIf(size > max, "$name must be **<= $max** but was `$size`")
    }

    /**
     * Inclusive min max int range validator, adds failIf clauses to the validationContext
     * @param name parameter name to be used in response
     * @param min minimal value for the int
     * @param max maximal value for the int
     *  **/
    fun ValidationContext<Int?>.inRange(name: String, min: Int, max: Int) {
        atLeast(name, min)
        atMost(name, max)
    }

    /**
     * Inclusive min max string length validator, adds failIf clauses to the validationContext
     * @param name parameter name to be used in response
     * @param min minimal length of a string param
     * @param max maximal length of a string param
     *  **/
    fun ValidationContext<String?>.lengthBetween(name: String, min: Int, max: Int) {
        val length = this.value?.length ?: return
        failIf(length < min, "$name length must be **>= $min** characters but was `$length`")
        failIf(length > max, "$name length must be **<= $max** characters but was `$length`")
    }

    fun TranslationsProvider.tr(
        @PropertyKey(resourceBundle = "translations.melijn.strings") key: String,
        vararg replacements: Any
    ): String =
        translate(key, "melijn.strings", replacements.asList().toTypedArray())

    fun CommandContext.tr(
        @PropertyKey(resourceBundle = "translations.melijn.strings") key: String,
        vararg replacements: Any
    ): String =
        translationsProvider.translate(key, "melijn.strings", replacements.asList().toTypedArray())


    /**
     * DSL function for easily registering a public slash command, with arguments.
     * Includes a check for anyGuild
     *
     * Use this in your setup function to register a slash command that may be executed on Discord.
     *
     * @param arguments Arguments builder (probably a reference to the class constructor).
     * @param body Builder lambda used for setting up the slash command object.
     */
    @ExtensionDSL
    public suspend fun <T : Arguments> Extension.publicGuildSlashCommand(
        arguments: () -> T,
        body: suspend PublicSlashCommand<T>.() -> Unit
    ): PublicSlashCommand<T> = publicSlashCommand(arguments) {
        check {
            anyGuild()
        }
        body()
    }

    /**
     * DSL function for easily registering a public slash command, without arguments.
     * Includes a check for anyGuild
     *
     * Use this in your setup function to register a slash command that may be executed on Discord.
     *
     * @param body Builder lambda used for setting up the slash command object.
     */
    @ExtensionDSL
    public suspend fun Extension.publicGuildSlashCommand(
        body: suspend PublicSlashCommand<Arguments>.() -> Unit
    ): PublicSlashCommand<Arguments> = publicSlashCommand {
        check {
            anyGuild()
        }
        body()
    }
}

interface InferredChoiceEnum : ChoiceEnum {
    override val readableName: String
        get() = this.toString().ucc()
}

@Converter(
    "shortTime",
    types = [ConverterType.DEFAULTING, ConverterType.LIST, ConverterType.OPTIONAL, ConverterType.SINGLE]
)
class ShortTimeConverter(
    override var validator: Validator<Long> = null
) : SingleConverter<Long>() {
    override val signatureTypeString: String = "converters.string.signatureType"
    override val showTypeInSignature: Boolean = false

    override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
        val arg: String = named ?: parser?.parseNext()?.data ?: return false

        parsed = parse(context, arg)

        return true
    }

    override suspend fun toSlashOption(arg: Argument<*>): OptionsBuilder =
        StringChoiceBuilder(arg.displayName, arg.description).apply { required = true }

    override suspend fun parseOption(context: CommandContext, option: OptionValue<*>): Boolean {
        val optionValue = (option as? StringOptionValue)?.value ?: return false

        parsed = parse(context, optionValue)

        return true
    }

    private fun parse(context: CommandContext, arg: String): Long {
        if (!arg.matches("(?:\\d{1,2}:)?\\d{1,2}:\\d{1,2}".toRegex())) {
            throw DiscordRelayedException(context.tr("shortTimeConverter.badFormatDetected", arg.escapeMarkdown()))
        }

        val parts = arg
            .replace(":", " ")
            .split(SPACE_PATTERN).toMutableList()

        var time: Long = 0

        for ((index, part) in parts.reversed().withIndex()) {
            time += part.toShort() * when (index) {
                0 -> 1000
                1 -> 60_000
                2 -> 3_600_000
                else -> 0
            }
        }
        return time
    }
}
