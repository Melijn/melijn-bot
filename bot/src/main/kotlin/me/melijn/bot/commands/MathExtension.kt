package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.rest.builder.message.create.actionRow
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.cache.ButtonCache
import me.melijn.bot.events.LATEX_DESTROY_BUTTON_ID
import me.melijn.bot.model.AbstractOwnedMessage
import me.melijn.bot.utils.ImageUtil
import me.melijn.bot.utils.ImageUtil.toInputStream
import org.koin.core.component.inject
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.*


@KordExtension
class MathExtension : Extension() {

    override val name: String = "math"

    override suspend fun setup() {
        chatCommand {
            name = "newtonSqrt"

            action {
                val function = ::findSquareNewtonsMethod
                val findSquareNewtonsMethod = function(1.0, 1e-10, 50_000)
                channel.createMessage {
                    val canvasImg = ImageUtil.createSquare(401, Color.decode("#ffffff"))
                    var canvas = canvasImg.createGraphics()
                    canvas.paint = Color.BLACK
                    canvas.drawLine(0, 200, 400, 200)
                    canvas.drawLine(200, 0, 200, 400)
                    canvas.dispose()

                    var lastY = -1
                    for (i in -200 until 200) {
                        val x = i / 20.0
                        val y = f(x)
                        val drawX = i + 200
                        val drawY = (y * 20.0).roundToInt() + 200

                        if (lastY != -1) {
                            for (j in min(drawY+1, lastY) until max(drawY, lastY)) {
                                if (j in 1..400 && drawX > 0)
                                    canvasImg.setRGB(drawX - 1, j, Color.decode("#FF0000").rgb)
                            }
                        }
                        lastY = drawY
                        if (drawY in 1..400)
                            canvasImg.setRGB(drawX, drawY, Color.decode("#FF0000").rgb)
                    }

                    val drawX = (findSquareNewtonsMethod.nextX * 20.0).roundToInt() + 200
                    canvas = canvasImg.createGraphics()
                    canvas.paint = Color.MAGENTA
                    canvas.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    canvas.fillOval(drawX-2, 200-2, 5, 5)
                    canvas.dispose()

                    addFile("grid.png", canvasImg.toInputStream())
                    content = "Result ${function.name}: $findSquareNewtonsMethod"
                }
            }
        }

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

                val d = sqrt((b / 2).pow(2.0f) - c)
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

        val buttonCache by inject<ButtonCache>()
        chatCommand(::LaTeXArgs) {
            name = "latex"
            description = "https://en.wikibooks.org/wiki/LaTeX/Mathematics"
            allowKeywordArguments = false

            action {
                val latex = argString
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

                val sent = channel.createMessage {
                    addFile("img.png", ChannelProvider { bis.toByteReadChannel() })
                    actionRow {
                        interactionButton(ButtonStyle.Danger, LATEX_DESTROY_BUTTON_ID) {
                            label = "Destroy"
                        }
                    }
                }

                buttonCache.latexButtonOwners[AbstractOwnedMessage.from(guild, user!!, sent)] = true
            }
        }

        chatCommand {
            name = "double"
            description = "Finds the base and mantise of kotlin doubles"

            action {
                var a = 1.0
                while ((a + 1.0) - a == 1.0) {
                    a *= 2.0
                }
                var i = 1.0
                while ((a + i) == a) {
                    i++
                }
                val b = (a + i) - a

                var p = 1
                var z = b
                while ((z + 1.0) - z == 1.0) {
                    p++
                    z *= b
                }

                channel.createEmbed {
                    description = "base: ${b}, mantise: $z"
                }
            }
        }


    }

    fun f(x: Double): Double {
        return if (x == 0.0) 0.0
        else sin(1.0 / (x/10)) * 10
    }

    fun fd1(x: Double): Double {
        return x * 120 % 11
    }


    data class NewtonData(val nextX: Double, val y: Double, val iterations: Int)

    private fun findSquareRootSecant(
        a: Double,
        b: Double,
        error: Double,
        iterationLimit: Int
    ): NewtonData {
        var (prevX, nextX, previousF, nextF) = listOf(a, b, f(a), f(b))
        var iter = 0

        while (abs(nextF) > error) {
            val partial = (nextF - previousF) / (nextX - prevX)
            prevX = nextX
            nextX -= nextF / partial
            previousF = nextF
            nextF = f(nextX)
            if (++iter > iterationLimit) return NewtonData(nextX, nextF, iter)
        }
        return NewtonData(nextX, nextF, iter)
    }

    /**
     * Can diverge
     * Requires knowing the derivative
     * Convergence alpha is 2
     * @param a starting point
     * @param error acceptable error
     * @param iterationLimit catch divergence by putting a limit on the amount of iterations
     * @return the first root we find of set function, or undefined values when the iterationLimit is hit
     */
    private fun findSquareNewtonsMethod(a: Double, error: Double, iterationLimit: Int): NewtonData {
        var nextX = a
        var iter = 0
        var nextF = f(nextX)
        while (abs(nextF) > error) {
            nextX -= nextF / fd1(nextX)
            nextF = f(nextX)
            iter++
            if (iter > iterationLimit) return NewtonData(nextX, nextF, iter)
        }
        return NewtonData(nextX, nextF, iter)
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