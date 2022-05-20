package me.melijn.bot.commands.music

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.OptionalConverter
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import dev.schlaubi.lavakord.audio.player.*
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.music.MusicManager.getTrackManager
import me.melijn.bot.utils.EnumUtil.lcc
import me.melijn.bot.utils.EnumUtil.ucc
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.inRange
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import reactor.util.function.Tuple4
import reactor.util.function.Tuple8
import reactor.util.function.Tuples
import kotlin.math.roundToInt

@KordExtension
class EffectsExtension : Extension() {

    override val name: String = "effects"

    class TimeScaleEffectArgs(private val uCamelName: String, private val lowerName: String) : Arguments() {

        val percent = optionalInt {
            name = "player${uCamelName}"
            description = "in percentage, 100% is default 1:1"

            validate {
                inRange(name, 1, 1000)
            }
        }

        val reset = optionalBoolean {
            name = "reset"
            description = "Resets the player $lowerName to 100%"
        }
    }

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "effect"
            description = "music player effects"

            for (timeScaleType in TimeScaleType.values()) {
                val lowerName = timeScaleType.lcc()
                val uCamelName = timeScaleType.ucc()
                publicSubCommand({ TimeScaleEffectArgs(uCamelName, lowerName) }) {
                    name = lowerName
                    description = "change the music player $lowerName"

                    action {
                        val guild = guild!!.asGuild()
                        val final = if (arguments.reset.parsed == true) 1.0f else arguments.percent.parsed?.div(100.0f)
                        val trackManager = guild.getTrackManager()

                        if (final == null) { // show current value
                            val currentValue =
                                timeScaleType.get(trackManager.player.filters.timescale) // get current value

                            respond {
                                content = tr("effect.${lowerName}.show", (currentValue * 100).roundToInt())
                            }
                            return@action
                        }

                        trackManager.player.applyFilters {
                            timescale {
                                timeScaleType.set(this, final) // Change the filter
                            }
                        }

                        respond {
                            content = tr("effect.${lowerName}.changed", (final * 100).roundToInt())
                        }
                    }
                }
            }

            publicSubCommand(::BandsArgs) {
                name = "bands"
                description = "configures audio bands"

                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()
                    if (arguments.args.isEmpty()) {
                        respond {
                            val filters = trackManager.player.filters
                            content = "band0: ${filters.bands[0].gain}\n" +
                                "band1: ${filters.bands[1].gain}\n" +
                                "band2: ${filters.bands[2].gain}\n" +
                                "band3: ${filters.bands[3].gain}\n" +
                                "band4: ${filters.bands[4].gain}\n" +
                                "band5: ${filters.bands[5].gain}\n" +
                                "band6: ${filters.bands[6].gain}\n" +
                                "band7: ${filters.bands[7].gain}\n" +
                                "band8: ${filters.bands[8].gain}\n" +
                                "band9: ${filters.bands[9].gain}\n" +
                                "band10: ${filters.bands[10].gain}\n" +
                                "band11: ${filters.bands[11].gain}\n" +
                                "band12: ${filters.bands[12].gain}\n" +
                                "band13: ${filters.bands[13].gain}\n" +
                                "band14: ${filters.bands[14].gain}\n"
                        }
                        return@action
                    }

                    if (arguments.reset.parsed == true) {
                        trackManager.player.applyFilters {
                            bands.indices.forEach { band(it).reset() }
                        }
                        respond { content = "reset all bands" }
                        return@action
                    }

                    trackManager.player.applyFilters {
                        arguments.band0.parsed?.let { band(0).gain((it - 100) / 400.0f) }
                        arguments.band1.parsed?.let { band(1).gain((it - 100) / 400.0f) }
                        arguments.band2.parsed?.let { band(2).gain((it - 100) / 400.0f) }
                        arguments.band3.parsed?.let { band(3).gain((it - 100) / 400.0f) }
                        arguments.band4.parsed?.let { band(4).gain((it - 100) / 400.0f) }
                        arguments.band5.parsed?.let { band(5).gain((it - 100) / 400.0f) }
                        arguments.band6.parsed?.let { band(6).gain((it - 100) / 400.0f) }
                        arguments.band7.parsed?.let { band(7).gain((it - 100) / 400.0f) }
                        arguments.band8.parsed?.let { band(8).gain((it - 100) / 400.0f) }
                        arguments.band9.parsed?.let { band(9).gain((it - 100) / 400.0f) }
                        arguments.band10.parsed?.let { band(10).gain((it - 100) / 400.0f) }
                        arguments.band11.parsed?.let { band(11).gain((it - 100) / 400.0f) }
                        arguments.band12.parsed?.let { band(12).gain((it - 100) / 400.0f) }
                        arguments.band13.parsed?.let { band(13).gain((it - 100) / 400.0f) }
                        arguments.band14.parsed?.let { band(14).gain((it - 100) / 400.0f) }
                    }
                    respond {
                        val filters = trackManager.player.filters
                        content = "band0: ${filters.bands[0].gain}\n" +
                            "band1: ${filters.bands[1].gain}\n" +
                            "band2: ${filters.bands[2].gain}\n" +
                            "band3: ${filters.bands[3].gain}\n" +
                            "band4: ${filters.bands[4].gain}\n" +
                            "band5: ${filters.bands[5].gain}\n" +
                            "band6: ${filters.bands[6].gain}\n" +
                            "band7: ${filters.bands[7].gain}\n" +
                            "band8: ${filters.bands[8].gain}\n" +
                            "band9: ${filters.bands[9].gain}\n" +
                            "band10: ${filters.bands[10].gain}\n" +
                            "band11: ${filters.bands[11].gain}\n" +
                            "band12: ${filters.bands[12].gain}\n" +
                            "band13: ${filters.bands[13].gain}\n" +
                            "band14: ${filters.bands[14].gain}\n"
                    }
                }
            }

            publicSubCommand(::ChannelMixArgs) {
                name = "channelmix"
                description = "change the music player channel mix"

                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()

                    when (arguments.preset.parsed) {
                        ChannelMixPreset.RESET -> {
                            trackManager.player.applyFilters {
                                unsetChannelMix()
                            }
                            respond {
                                content = tr("effect.channelMix.reset")
                            }
                            return@action
                        }
                        ChannelMixPreset.MONO -> {
                            trackManager.player.applyFilters {
                                channelMix {
                                    leftToRight = 0.5f
                                    rightToLeft = 0.5f
                                    leftToLeft = 0.5f
                                    rightToRight = 0.5f
                                }
                            }
                            respond {
                                content = tr("effect.channelMix.mono")
                            }
                            return@action
                        }
                        else -> {}
                    }

                    val currentChannelMix: suspend Player.() -> Tuple4<Float, Float, Float, Float> = {
                        var tupple: Tuple4<Float, Float, Float, Float> = Tuples.of(0f, 0f, 0f, 0f)
                        filters.channelMix?.run {
                            tupple = Tuples.of(rightToLeft, leftToRight, leftToLeft, rightToRight)
                        }
                        tupple
                    }
                    if (arguments.args.isEmpty()) {
                        val current = currentChannelMix(trackManager.player)
                        respond {
                            content = tr(
                                "effect.channelMix.show", current.t1 * 100,
                                current.t2 * 100,
                                current.t3 * 100,
                                current.t4 * 100
                            )
                        }
                        return@action
                    }

                    trackManager.player.applyFilters {
                        channelMix {
                            arguments.rightToLeft.parsed?.let { rightToLeft = it / 100.0f }
                            arguments.leftToRight.parsed?.let { leftToRight = it / 100.0f }
                            arguments.leftToLeft.parsed?.let { leftToLeft = it / 100.0f }
                            arguments.rightToRight.parsed?.let { rightToRight = it / 100.0f }
                        }
                    }

                    val current = currentChannelMix(trackManager.player)

                    respond {
                        content = tr(
                            "effect.channelMix.changed",
                            current.t1 * 100,
                            current.t2 * 100,
                            current.t3 * 100,
                            current.t4 * 100
                        )
                    }
                }
            }

            publicSubCommand(::DistortionArgs) {
                name = "distortion"
                description = "change the music player distortion"

                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()

                    if (arguments.reset.parsed == true) {
                        trackManager.player.applyFilters {
                            unsetDistortion()
                        }
                        respond {
                            content = tr("effect.distortion.reset")
                        }
                        return@action
                    }

                    val currentDistortion: suspend Player.() -> Tuple8<Float, Float, Float, Float, Float, Float, Float, Float> =
                        {
                            var tupple: Tuple8<Float, Float, Float, Float, Float, Float, Float, Float> =
                                Tuples.of(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                            filters.distortion?.run {
                                tupple = Tuples.of(
                                    scale,
                                    offset,
                                    sinScale,
                                    sinOffset,
                                    cosScale,
                                    cosOffset,
                                    tanScale,
                                    tanOffset
                                )
                            }
                            tupple
                        }
                    if (arguments.args.isEmpty()) {
                        val current = currentDistortion(trackManager.player)
                        respond {
                            content = tr(
                                "effect.distortion.show", current.t1 * 100,
                                current.t2 * 100,
                                current.t3 * 100,
                                current.t4 * 100,
                                current.t5 * 100,
                                current.t6 * 100,
                                current.t7 * 100,
                                current.t8 * 100
                            )
                        }
                        return@action
                    }

                    trackManager.player.applyFilters {
                        distortion {
                            arguments.scale.parsed?.let { scale = it / 100.0f }
                            arguments.offset.parsed?.let { offset = it / 100.0f }
                            arguments.sinScale.parsed?.let { sinScale = it / 100.0f }
                            arguments.sinOffset.parsed?.let { sinOffset = it / 100.0f }
                            arguments.cosScale.parsed?.let { cosScale = it / 100.0f }
                            arguments.cosOffset.parsed?.let { cosOffset = it / 100.0f }
                            arguments.tanScale.parsed?.let { tanScale = it / 100.0f }
                            arguments.tanOffset.parsed?.let { tanOffset = it / 100.0f }
                        }
                    }

                    val current = currentDistortion(trackManager.player)

                    respond {
                        content = tr(
                            "effect.distortion.changed",
                            current.t1 * 100,
                            current.t2 * 100,
                            current.t3 * 100,
                            current.t4 * 100,
                            current.t5 * 100,
                            current.t6 * 100,
                            current.t7 * 100,
                            current.t8 * 100
                        )
                    }
                }
            }

            publicSubCommand(::RotationArgs) {
                name = "rotation"
                description = "stereo rotation effect"

                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()

                    if (arguments.reset.parsed == true) {
                        trackManager.player.applyFilters { unsetRotation() }
                        respond { content = tr("effect.rotation.reset") }
                        return@action
                    }

                    val currentRotation: suspend Player.() -> Float = {
                        filters.rotation?.run { rotationHz } ?: 1f
                    }

                    if (arguments.args.isEmpty()) {
                        val current = currentRotation(trackManager.player)
                        respond {
                            content = tr("effect.rotation.show", current * 1000)
                        }
                        return@action
                    }

                    trackManager.player.applyFilters {
                        rotation {
                            rotationHz = arguments.hertz.parsed?.toFloat()?.div(1000) ?: 0.2f
                        }
                    }
                    val new = currentRotation(trackManager.player)

                    respond {
                        content = tr("effect.rotation.changed", new * 1000)
                    }
                }
            }

            publicSubCommand(::KaraokeArgs) {
                name = "karaoke"
                description = "muffles vocal frequencies to make it easier to sing along"

                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()

                    if (arguments.reset.parsed == true) {
                        trackManager.player.applyFilters { unsetKaraoke() }
                        respond { content = tr("effect.karaoke.reset") }
                        return@action
                    }

                    val currentKaraoke: suspend Player.() -> Array<Float> = {
                        val array = Array(4) { 0f }
                        filters.karaoke?.run {
                            array[0] = level
                            array[1] = monoLevel
                            array[2] = filterBand
                            array[3] = filterWidth
                        }
                        array
                    }

                    if (arguments.args.isEmpty()) {
                        val current = currentKaraoke(trackManager.player)
                        respond {
                            content =
                                tr("effect.karaoke.show", current[0] * 100, current[1] * 100, current[2], current[3])
                        }
                        return@action
                    }

                    trackManager.player.applyFilters {
                        karaoke {
                            level = arguments.level.parsed?.toFloat()?.div(100) ?: 1f
                            monoLevel = arguments.monoLevel.parsed?.toFloat()?.div(100) ?: 1f
                            filterBand = arguments.filterBand.parsed?.toFloat() ?: 220f
                            filterWidth = arguments.filterWidth.parsed?.toFloat() ?: 100f
                        }
                    }
                    val new = currentKaraoke(trackManager.player)

                    respond {
                        content = tr("effect.karaoke.changed", new[0] * 100, new[1] * 100, new[2], new[3])
                    }
                }
            }

            publicSubCommand(::LowPassArgs) {
                name = "lowpass"
                description = "suppresses higher frequencies"

                action {
                    val guild = guild!!.asGuild()
                    val trackManager = guild.getTrackManager()

                    if (arguments.reset.parsed == true) {
                        trackManager.player.applyFilters { unsetLowPass() }
                        respond { content = tr("effect.lowpass.reset") }
                        return@action
                    }

                    val currentSmoothing: suspend Player.() -> Float = {
                        filters.lowPass?.run { smoothing } ?: 1f
                    }

                    if (arguments.args.isEmpty()) {
                        val current = currentSmoothing(trackManager.player)
                        respond {
                            content = tr("effect.lowpass.show", current * 100)
                        }
                        return@action
                    }

                    arguments.smoothing.parsed?.let {
                        trackManager.player.applyFilters {
                            lowPass {
                                smoothing = it / 100.0f
                            }
                        }
                    }
                    val new = currentSmoothing(trackManager.player)

                    respond {
                        content = tr("effect.lowpass.changed", new * 100)
                    }
                }
            }

            for (common in FreqDepthFilters.values()) {
                publicSubCommand({ TremoloVibratoCommonArgs(common.ucc()) }) {
                    name = common.lcc().lowercase()
                    description = "musicPlayer $name effects"

                    action {
                        val guild = guild!!.asGuild()
                        val trackManager = guild.getTrackManager()

                        if (arguments.reset.parsed == true) {
                            trackManager.player.applyFilters { unsetTremolo() }
                            respond { content = tr("effect.${common.lcc()}.reset") }
                            return@action
                        }

                        val currentFreqDepth: suspend Player.() -> Pair<Float, Float> = {
                            common.get(filters)
                        }

                        if (arguments.args.isEmpty()) {
                            val current = currentFreqDepth(trackManager.player)
                            respond {
                                content = tr(
                                    "effect.${common.lcc()}.show",
                                    current.first * 100,
                                    current.second * 100
                                )
                            }
                            return@action
                        }

                        var new = currentFreqDepth(trackManager.player)
                        arguments.frequency.parsed?.let { new = it / 100.0f to new.second }
                        arguments.depth.parsed?.let { new = new.first to it / 100.0f }
                        trackManager.player.applyFilters {
                            common.set(this, new.first, new.second)
                        }

                        respond {
                            content = tr(
                                "effect.${common.lcc()}.changed",
                                new.first * 100,
                                new.second * 100
                            )
                        }
                    }
                }
            }
        }
    }

    class RotationArgs : Arguments() {

        val hertz = optionalInt {
            name = "hertz"
            description = "1000% = 1hz"
        }
        val reset = optionalBoolean {
            name = "reset"
            description = "Resets the rotation effect"
        }
    }

    class KaraokeArgs : Arguments() {

        val level = optionalInt {
            name = "level"
            description = "karaoke level"
        }
        val monoLevel = optionalInt {
            name = "monolevel"
            description = "mono karaoke level"
        }
        val filterBand = optionalInt {
            name = "filterband"
            description = "frequency band"
        }

        val filterWidth = optionalInt {
            name = "filterwidth"
            description = "frequency band width"
        }
        val reset = optionalBoolean {
            name = "reset"
            description = "Resets the karaoke effect"
        }
    }

    class LowPassArgs : Arguments() {

        val smoothing = optionalInt {
            name = "smoothing"
            description = "smoothing value idk"
        }
        val reset = optionalBoolean {
            name = "reset"
            description = "Resets the lowpass effect"
        }
    }

    enum class ChannelMixPreset : InferredChoiceEnum {
        RESET,
        MONO
    }

    enum class FreqDepthFilters(val set: Filters.(Float, Float) -> Unit, val get: Filters.() -> Pair<Float, Float>) {
        TREMOLO({ frequency, depth ->
            tremolo {
                this.frequency = frequency
                this.depth = depth
            }
        }, {
            (tremolo?.frequency ?: 1.0f) to (tremolo?.depth ?: 1.0f)
        }),
        VIBRATO({ frequency, depth ->
            vibrato {
                this.frequency = frequency
                this.depth = depth
            }
        }, {
            (vibrato?.frequency ?: 1.0f) to (vibrato?.depth ?: 1.0f)
        }),
    }

    open inner class TremoloVibratoCommonArgs(val name: String) : Arguments() {

        val frequency = optionalInt {
            name = "frequency"
            description = "$name frequency"
            validate { atLeast(name, 1) }
        }
        val depth = optionalInt {
            name = "depth"
            description = "$name depth"
            validate { inRange(name, 1, 100) }
        }
        val reset = optionalBoolean {
            name = "reset"
            description = "Resets the ${name.lowercase()} effect"
        }
    }

    inner class DistortionArgs : Arguments() {

        private val options = listOf("scale", "offset")
        private val function = listOf("", "cos", "sin", "tan")

        val directionalArg: (String, String) -> OptionalConverter<Int> = { opt, funct ->
            optionalInt {
                name = "${opt}${funct.replaceFirstChar { c -> c.uppercaseChar() }}"
                description = "$name percentage"
                validate { inRange(name, -500, 500) }
            }
        }

        val scale = directionalArg(options[0], function[0])
        val offset = directionalArg(options[1], function[0])

        val sinScale = directionalArg(options[0], function[1])
        val sinOffset = directionalArg(options[1], function[1])

        val cosScale = directionalArg(options[0], function[2])
        val cosOffset = directionalArg(options[1], function[2])

        val tanScale = directionalArg(options[0], function[3])
        val tanOffset = directionalArg(options[1], function[3])

        val reset = optionalBoolean {
            name = "reset"
            description = "Resets the distortion effect"
        }
    }

    inner class BandsArgs : Arguments() {

        val band0 = optionalInt {
            name = "band0"
            description = "band0"
        }
        val band1 = optionalInt {
            name = "band1"
            description = "band1"
        }
        val band2 = optionalInt {
            name = "band2"
            description = "band2"
        }
        val band3 = optionalInt {
            name = "band3"
            description = "band3"
        }
        val band4 = optionalInt {
            name = "band4"
            description = "band4"
        }
        val band5 = optionalInt {
            name = "band5"
            description = "band5"
        }
        val band6 = optionalInt {
            name = "band6"
            description = "band6"
        }
        val band7 = optionalInt {
            name = "band7"
            description = "band7"
        }
        val band8 = optionalInt {
            name = "band8"
            description = "band8"
        }
        val band9 = optionalInt {
            name = "band9"
            description = "band9"
        }
        val band10 = optionalInt {
            name = "band10"
            description = "band10"
        }
        val band11 = optionalInt {
            name = "band11"
            description = "band11"
        }
        val band12 = optionalInt {
            name = "band12"
            description = "band12"
        }
        val band13 = optionalInt {
            name = "band13"
            description = "band13"
        }
        val band14 = optionalInt {
            name = "band14"
            description = "band14"
        }
        val reset = optionalBoolean {
            name = "reset"
            description = "Resets the distortion effect"
        }
    }

    inner class ChannelMixArgs : Arguments() {

        private val directions = listOf(
            "left" to "right",
            "right" to "left",
            "left" to "left",
            "right" to "right"
        )
        val directionalArg: (Pair<String, String>) -> OptionalConverter<Int> = { (channel1, channel2) ->
            optionalInt {
                name = "${channel1}To${channel2.replaceFirstChar { c -> c.uppercaseChar() }}"
                description = "percentage of mixing the $channel1 channel into the $channel2 channel"
                validate { inRange(name, -500, 500) }
            }
        }

        val leftToRight = directionalArg(directions[0])
        val rightToLeft = directionalArg(directions[1])
        val leftToLeft = directionalArg(directions[2])
        val rightToRight = directionalArg(directions[3])
        val preset = optionalEnumChoice<ChannelMixPreset> {
            name = "preset"
            description = "ChannelMix preset, will override other arguments"
            typeName = "preset"
        }
    }
}

enum class TimeScaleType(val set: Filters.Timescale.(Float) -> Unit, val get: (Filters.Timescale?) -> Float) {
    PITCH({ this.pitch = it }, { it?.pitch ?: 1.0f }),
    SPEED({ this.speed = it }, { it?.speed ?: 1.0f }),
    RATE({ this.rate = it }, { it?.rate ?: 1.0f })
}