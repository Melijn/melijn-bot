package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.rest.builder.message.create.actionRow
import me.melijn.apkordex.command.KordExtension
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow


@KordExtension
class MathExtension : Extension() {

    override val name: String = "math"

    override suspend fun setup() {
        chatCommand(::TwoNumberArgs) {
            name = "gcd"

            action {
                val gcd = gcd(arguments.a.parsed, arguments.b.parsed)
                channel.createMessage("The greatest common denominator is: **${gcd}**")
            }
        }

        chatCommand(::TwoNumberArgs) {
            name = "scm"

            action {
                val scm = scm(arguments.a.parsed, arguments.b.parsed)
                channel.createMessage("The smallest common multiple is: **${scm}**")
            }
        }

        chatCommand(::VkVglArgs) {
            name = "vkvgl"

            action {
                val b = arguments.b.parsed.toFloat()
                val c = arguments.c.parsed.toFloat()

                val d = kotlin.math.sqrt((b / 2).pow(2.0f) - c)
                val x1v1 = -b / 2 + d
                var x1v2 = abs(b / 2) + d
                if (b > 0) x1v2 = -x1v2
                channel.createEmbed {
                    description = "D: $d\n"

                    description += "\n**V1**\n"
                    description += "x1: ${x1v1}\n"
                    description += "x2: ${-b / 2 - d}\n"

                    description += "\n**V2**\n"

                    description += "x1: $x1v2\n"
                    description += "x2: ${c / x1v2}\n"
                }
            }
        }

        chatCommand(::LaTeXArgs) {
            name = "latex"
            description = "https://en.wikibooks.org/wiki/LaTeX/Mathematics"
            allowKeywordArguments = false

            action {
                val latex = arguments.latex.parsed
                val formula = TeXFormula(latex)
                val img: BufferedImage = formula.createBufferedImage(
                    TeXConstants.STYLE_DISPLAY,
                    20f,
                    Color.BLACK,
                    Color.WHITE
                ) as BufferedImage
                val baos = ByteArrayOutputStream()
                kotlin.runCatching { ImageIO.write(img, "jpeg", baos) }
                val bis = ByteArrayInputStream(baos.toByteArray())

                channel.createMessage {
                    addFile("img.png", bis)
                    actionRow {
                        interactionButton(ButtonStyle.Danger, "DESTROYY") {
                            label = "Destroy"
                        }
                    }
                    components {
                        publicButton {
                            label = "Destroy"
                            style = ButtonStyle.Danger
                            this.action {
                                this.message
                            }
                        }
                    }
                }
            }
        }
    }

    /** finds the greatest common denominator **/
    private fun gcd(a: Long, b: Long): Long {
        var a2 = a
        var b2 = b
        var rest = a2 % b2
        while (rest != 0L) {
            a2 = b2
            b2 = rest
            rest = a2 % b2
        }
        return b2
    }

    /** finds smallest common multiple **/
    private fun scm(a: Long, b: Long): Long {
        return a / gcd(a, b) * b
    }

    inner class LaTeXArgs : Arguments() {

        val latex = string {
            name = "mathTex"
            description = "put math here to render"
        }
    }

    inner class VkVglArgs : Arguments() {

        val b = long {
            name = "b"
            description = "number 2"
        }
        val c = long {
            name = "c"
            description = "number 3"
        }
    }

    inner class TwoNumberArgs : Arguments() {

        val a = long {
            name = "a"
            description = "number 1"
        }
        val b = long {
            name = "b"
            description = "number 2"
        }
    }
}