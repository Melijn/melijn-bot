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

    class TimeScaleEffectArgs(val uCamelName: String, val lowerName: String) : Arguments() {

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
                            var currentValue = 1.0f
                            trackManager.player.applyFilters {
                                currentValue = timeScaleType.get(this) // get current value
                            }
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
                                this.channelMix {
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
                        applyFilters {
                            channelMix {
                                tupple = Tuples.of(rightToLeft, leftToRight, leftToLeft, rightToRight)
                            }
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
                        this.channelMix {
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
                            applyFilters {
                                distortion {
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
                        var array = 1f
                        applyFilters {
                            array = rotation?.rotationHz ?: 1f
                        }
                        array
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
                        applyFilters {
                            array[0] = karaoke?.level ?: 1f
                            array[1] = karaoke?.monoLevel ?: 1f
                            array[2] = karaoke?.filterBand ?: 220f
                            array[3] = karaoke?.filterWidth ?: 100f
                        }
                        array
                    }

                    if (arguments.args.isEmpty()) {
                        val current = currentKaraoke(trackManager.player)
                        respond {
                            content = tr("effect.karaoke.show", current[0] * 100, current[1] * 100, current[2], current[3])
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
                        var tupple = 1f
                        applyFilters {
                            tupple = lowPass?.smoothing ?: 1f
                        }
                        tupple
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
                    name = common.lcc()
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
                            var tupple: Pair<Float, Float> = 1f to 1f
                            applyFilters {
                                tupple = common.get(this)
                            }
                            tupple
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
            var v1 = 1.0f
            var v2 = 1.0f; tremolo { v1 = frequency; v2 = depth }; v1 to v2
        }),
        VIBRATO({ frequency, depth ->
            vibrato {
                this.frequency = frequency
                this.depth = depth
            }
        }, {
            var v1 = 1.0f
            var v2 = 1.0f; vibrato { v1 = frequency; v2 = depth }; v1 to v2
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

enum class TimeScaleType(val set: Filters.Timescale.(Float) -> Unit, val get: Filters.() -> Float) {
    PITCH({ this.pitch = it }, { var pitch = 1.0f;this.timescale { pitch = this.pitch }; pitch }),
    SPEED({ this.speed = it }, { var speed = 1.0f;this.timescale { speed = this.speed }; speed }),
    RATE({ this.rate = it }, { var rate = 1.0f; this.timescale { rate = this.rate }; rate })
}