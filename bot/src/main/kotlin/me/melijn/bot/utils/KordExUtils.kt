package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.annotations.ExtensionDSL
import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.Argument
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommand
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommand
import com.kotlindiscord.kord.extensions.commands.application.slash.SlashGroup
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommand
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.SingleConverter
import com.kotlindiscord.kord.extensions.commands.converters.Validator
import com.kotlindiscord.kord.extensions.commands.converters.builders.ConverterBuilder
import com.kotlindiscord.kord.extensions.commands.converters.builders.ValidationContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.LongConverterBuilder
import com.kotlindiscord.kord.extensions.commands.converters.impl.OptionalLongConverterBuilder
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalLong
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.modules.annotations.converters.Converter
import com.kotlindiscord.kord.extensions.modules.annotations.converters.ConverterType
import com.kotlindiscord.kord.extensions.parser.StringParser
import me.melijn.bot.database.manager.BalanceManager
import me.melijn.bot.database.manager.PlaylistManager
import me.melijn.bot.utils.EnumUtil.ucc
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.normalDate
import me.melijn.bot.utils.TimeUtil.parseOrNull
import me.melijn.gen.PlaylistData
import me.melijn.gen.Settings
import me.melijn.kordkommons.utils.SPACE_PATTERN
import me.melijn.kordkommons.utils.escapeMarkdown
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.jetbrains.annotations.PropertyKey
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.*

object KordExUtils {

    suspend fun CheckContext<MessageReceivedEvent>.userIsOwner() {
        val botSettings by inject<Settings>()
        failIfNot("bot owner command") {
            botSettings.bot.ownerIds.split(",").any {
                it.trim() == this.event.author.id
            }
        }
    }

    suspend fun ValidationContext<*>.userIsOwner() {
        val botSettings by inject<Settings>()
        failIfNot("Only devs may supply this argument") {
            botSettings.bot.ownerIds.split(",").any {
                it.trim() == this.context.user.id
            }
        }
    }

    @JvmName("userIsOwnerChatInputCommandInteractionCreateEvent")
    suspend fun CheckContext<SlashCommandInteractionEvent>.userIsOwner() {
        val botSettings by inject<Settings>()
        failIfNot("bot owner command") {
            botSettings.bot.ownerIds.split(",").any {
                it.trim() == this.event.interaction.user.id
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
        @PropertyKey(resourceBundle = MELIJN_RESOURCE_BUNDLE) key: String,
        locale: Locale,
        vararg replacements: Any?
    ): String =
        translate(key, locale, MELIJN_RESOURCE_BUNDLE_KORDEX, replacements.asList().toTypedArray())

    suspend fun CommandContext.tr(
        @PropertyKey(resourceBundle = MELIJN_RESOURCE_BUNDLE) key: String,
        vararg replacements: Any?
    ): String =
        translationsProvider.translate(
            key,
            resolvedLocale.await(),
            MELIJN_RESOURCE_BUNDLE_KORDEX,
            replacements.asList().toTypedArray()
        )

    suspend fun ValidationContext<*>.tr(
        @PropertyKey(resourceBundle = MELIJN_RESOURCE_BUNDLE) key: String,
        vararg replacements: Any?
    ): String =
        translations.translate(
            key,
            context.resolvedLocale.await(),
            MELIJN_RESOURCE_BUNDLE_KORDEX,
            replacements.asList().toTypedArray()
        )

    fun CheckContext<*>.tr(
        @PropertyKey(resourceBundle = MELIJN_RESOURCE_BUNDLE) key: String,
        vararg replacements: Any?
    ) =
        translations.translate(
            key,
            locale,
            MELIJN_RESOURCE_BUNDLE_KORDEX,
            replacements.asList().toTypedArray()
        )

    /**
     * DSL function for easily registering a command.
     *
     * Use this in your setup function to register a command that may be executed on Discord.
     *
     * @param body Builder lambda used for setting up the command object.
     */
    @ExtensionDSL
    suspend fun <T : Arguments> Extension.guildChatCommand(
        arguments: () -> T,
        body: suspend ChatCommand<T>.() -> Unit
    ): ChatCommand<T> {
        val commandObj = ChatCommand(this, arguments).apply {
            check {
                anyGuild()
            }
        }
        body.invoke(commandObj)
        return chatCommand(commandObj)
    }

    /**
     * DSL function for easily registering a command, without arguments.
     *
     * Use this in your setup function to register a command that may be executed on Discord.
     *
     * @param body Builder lambda used for setting up the command object.
     */
    @ExtensionDSL
    suspend fun Extension.guildChatCommand(
        body: suspend ChatCommand<Arguments>.() -> Unit
    ): ChatCommand<Arguments> {
        val commandObj = ChatCommand<Arguments>(this).apply {
            check {
                anyGuild()
            }
        }
        body.invoke(commandObj)
        return chatCommand(commandObj)
    }

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
    suspend fun <T : Arguments> Extension.publicGuildSlashCommand(
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
    suspend fun Extension.publicGuildSlashCommand(
        body: suspend PublicSlashCommand<Arguments>.() -> Unit
    ): PublicSlashCommand<Arguments> = publicSlashCommand {
        check {
            anyGuild()
        }
        body()
    }

    /**
     * DSL function for easily registering a public slash sub command, without arguments.
     * Includes a check for anyGuild
     *
     * Use this in your setup function to register a slash sub command that may be executed on Discord.
     *
     * @param body Builder lambda used for setting up the slash command object.
     */
    @ExtensionDSL
    suspend fun <T : Arguments> SlashCommand<*, *>.publicGuildSubCommand(
        arguments: () -> T,
        body: suspend PublicSlashCommand<T>.() -> Unit
    ): PublicSlashCommand<T> = publicSubCommand(arguments) {
        check {
            anyGuild()
        }
        body()
    }

    /**
     * DSL function for easily registering a public slash sub command, without arguments.
     * Includes a check for anyGuild
     *
     * Use this in your setup function to register a slash sub command that may be executed on Discord.
     *
     * @param body Builder lambda used for setting up the slash command object.
     */
    @ExtensionDSL
    suspend fun <T : Arguments> SlashGroup.publicGuildSubCommand(
        arguments: () -> T,
        body: suspend PublicSlashCommand<T>.() -> Unit
    ): PublicSlashCommand<T> = publicSubCommand(arguments) {
        check {
            anyGuild()
        }
        body()
    }

    /**
     * DSL function for easily registering a public slash sub command, without arguments.
     * Includes a check for anyGuild
     *
     * Use this in your setup function to register a slash sub command that may be executed on Discord.
     *
     * @param body Builder lambda used for setting up the slash command object.
     */
    @ExtensionDSL
    suspend fun SlashCommand<*, *>.publicGuildSubCommand(
        body: suspend PublicSlashCommand<Arguments>.() -> Unit
    ) = publicSubCommand {
        check {
            anyGuild()
        }
        body()
    }

    /**
     * DSL function for easily registering a public slash sub command, without arguments.
     * Includes a check for anyGuild
     *
     * Use this in your setup function to register a slash sub command that may be executed on Discord.
     *
     * @param body Builder lambda used for setting up the slash command object.
     */
    @ExtensionDSL
    suspend fun SlashGroup.publicGuildSubCommand(
        body: suspend PublicSlashCommand<Arguments>.() -> Unit
    ) = publicSubCommand {
        check {
            anyGuild()
        }
        body()
    }

    /**
     * @throws DiscordRelayedException
     */
    fun bail(reason: String): Nothing = throw DiscordRelayedException(reason)

    private val balanceManager by inject<BalanceManager>()

    fun Arguments.optionalAvailableCurrency(
        @PropertyKey(resourceBundle = "translations.melijn.strings") negativeOrZeroAmount: String,
        @PropertyKey(resourceBundle = "translations.melijn.strings") tooLittleBalance: String,
        func: OptionalLongConverterBuilder.() -> Unit
    ) = optionalLong {
        func()
        validate {
            val valueVal = value ?: return@validate
            validateBalanceAmount(negativeOrZeroAmount, valueVal, tooLittleBalance)
        }
    }

    fun Arguments.availableCurrency(
        @PropertyKey(resourceBundle = "translations.melijn.strings") negativeOrZeroAmount: String,
        @PropertyKey(resourceBundle = "translations.melijn.strings") tooLittleBalance: String,
        func: LongConverterBuilder.() -> Unit
    ) = long {
        func()
        validate {
            validateBalanceAmount(negativeOrZeroAmount, value, tooLittleBalance)
        }
    }

    suspend fun ValidationContext<Long?>.validateBalanceAmount(
        negativeOrZeroAmount: String,
        valueVal: Long,
        tooLittleBalance: String,
        multiplier: Int = 1
    ) {
        val balance = balanceManager.get(context.user).balance

        failIf(tr(negativeOrZeroAmount)) { valueVal <= 0 }
        try {
            failIf(tr(tooLittleBalance, multiplier * valueVal, balance)) { multiplier * valueVal > balance }
        } catch (e: ArithmeticException) {
            fail(tr("flip.timesTooMuch"))
        }
    }

    context(ConverterBuilder<*>)
    fun addDateTimeAutocompletion() {
        this@ConverterBuilder.autoComplete {
            val given = this.getOption("moment")?.asString
                ?.removeSuffix("-")
                ?.removeSuffix(":")
                ?.trim()
            val date = Date()
            val calendar = Calendar.getInstance()
            val simpleFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
            val simpleFormaty = SimpleDateFormat("yyyy")
            val simpleFormatm = SimpleDateFormat("yyyy-MM")
            val simpleFormatd = SimpleDateFormat("yyyy-MM-dd")
            val simpleFormath = SimpleDateFormat("yyyy-MM-dd HH")
            val now = simpleFormat.format(date)

            if (given.isNullOrBlank()) {
                this.replyChoice(now, "yyyy-MM-dd HH:mm").queue()

            } else {
                val parsedh = simpleFormath.parseOrNull(given)
                if (parsedh != null) {
                    this.replyChoiceStrings(buildList<String> {
                        repeat(6) {
                            add(simpleFormat.format(parsedh.plusMinutes(10L * it).normalDate))
                        }
                    }).queue()
                    return@autoComplete
                }
                val parsedd = simpleFormatd.parseOrNull(given)
                if (parsedd != null) {
                    this.replyChoiceStrings(buildList<String> {
                        repeat(24) {
                            val plussed = parsedd.plusHours(it.toLong())
                            add(simpleFormat.format(plussed.normalDate))
                        }
                    }).queue()
                    return@autoComplete
                }
                val parsedm = simpleFormatm.parseOrNull(given)
                if (parsedm != null) {
                    this.replyChoiceStrings(buildList<String> {
                        repeat(25) {
                            add(simpleFormat.format(parsedm.plusDays(calendar.get(Calendar.DAY_OF_MONTH) - 1 + it.toLong()).normalDate))
                        }
                    }).queue()
                    return@autoComplete
                }
                val parsedy = simpleFormaty.parseOrNull(given)
                if (parsedy != null) {
                    this.replyChoiceStrings(buildList<String> {
                        repeat(12) {
                            add(simpleFormat.format(parsedy.plusMonths(calendar.get(Calendar.MONTH) + it.toLong()).normalDate))
                        }
                    }).queue()
                    return@autoComplete
                }

                this.replyChoice("yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm").queue()
            }
        }
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

    override val signatureTypeString: String = "converters.shortTime.signatureType"
    override val showTypeInSignature: Boolean = false

    override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
        val arg: String = named ?: parser?.parseNext()?.data ?: return false

        parsed = parse(context, arg)

        return true
    }

    override suspend fun toSlashOption(arg: Argument<*>): OptionData =
        OptionData(OptionType.STRING, arg.displayName, arg.description, required)

    override suspend fun parseOption(context: CommandContext, option: OptionMapping): Boolean {
        val optionValue = if (option.type == OptionType.STRING) option.asString else return false

        parsed = parse(context, optionValue)

        return true
    }

    private suspend fun parse(context: CommandContext, arg: String): Long {
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


@Converter(
    "playlist",
    types = [ConverterType.DEFAULTING, ConverterType.LIST, ConverterType.OPTIONAL, ConverterType.SINGLE]
)
class PlaylistConverter(
    override var validator: Validator<PlaylistData> = null
) : SingleConverter<PlaylistData>() {

    override val signatureTypeString: String = "converters.playlist.signatureType"
    override val showTypeInSignature: Boolean = false
    private val playlistManager by inject<PlaylistManager>()

    override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
        val arg: String = named ?: parser?.parseNext()?.data ?: return false

        parsed = parse(context, arg)

        return true
    }

    override suspend fun toSlashOption(arg: Argument<*>): OptionData =
        OptionData(OptionType.STRING, arg.displayName, arg.description, required)

    override suspend fun parseOption(context: CommandContext, option: OptionMapping): Boolean {
        val optionValue = if (option.type == OptionType.STRING) option.asString else return false

        parsed = parse(context, optionValue)

        return true
    }

    private suspend fun parse(context: CommandContext, arg: String): PlaylistData {
        val playlists = playlistManager.getPlaylistsOfUser(context.user)

        return playlists.firstOrNull { it.name == arg }
            ?: throw DiscordRelayedException(
                context.translate(
                    "converters.playlist.unknownPlaylistName",
                    arrayOf(arg.escapeMarkdown())
                )
            )
    }
}

@Converter(
    "intRanges",
    types = [ConverterType.DEFAULTING, ConverterType.OPTIONAL, ConverterType.SINGLE]
)
class IntRangesConverter(
    override var validator: Validator<IntRanges> = null
) : SingleConverter<IntRanges>() {

    override val signatureTypeString: String = "converters.ints.signatureType"
    override val showTypeInSignature: Boolean = false

    override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
        val arg: String = named ?: parser?.parseNext()?.data ?: return false

        parsed = parse(context, arg)

        return true
    }

    override suspend fun toSlashOption(arg: Argument<*>): OptionData =
        OptionData(OptionType.STRING, arg.displayName, arg.description, required)

    override suspend fun parseOption(context: CommandContext, option: OptionMapping): Boolean {
        val optionValue = if (option.type == OptionType.STRING) option.asString else return false

        parsed = parse(context, optionValue)

        return true
    }

    private suspend fun parse(context: CommandContext, arg: String): IntRanges {
        try {
            val intRanges = mutableListOf<IntRange>()
            val parts = arg.split("\\s*,\\s*".toRegex())
            for (part in parts) {
                if (part.isNumber()) {
                    val one = part.toInt()
                    intRanges.add(IntRange(one, one))
                    continue
                }
                val partParts = part.split("-|\\.\\.".toRegex())
                val from = partParts[0].toInt()
                val to = partParts[1].toInt()
                intRanges.add(IntRange(from, to))
            }
            return IntRanges(intRanges)
        } catch (ex: Throwable) {
            throw DiscordRelayedException(context.tr("intRangeConverter.badFormat", arg.escapeMarkdown()))
        }
    }
}

class IntRanges(val list: List<IntRange>)

private fun String.isNumber(): Boolean {
    return toIntOrNull() != null
}
