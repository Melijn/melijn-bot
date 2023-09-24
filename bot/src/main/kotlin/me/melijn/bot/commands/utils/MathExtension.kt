package me.melijn.bot.commands.utils

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import dev.minn.jda.ktx.interactions.components.danger
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.cache.ButtonCache
import me.melijn.bot.events.buttons.LATEX_DESTROY_BUTTON_ID
import me.melijn.bot.model.AbstractOwnedMessage
import me.melijn.bot.utils.JDAUtil.createEmbed
import me.melijn.bot.utils.KordExUtils.atLeast
import me.melijn.bot.utils.KordExUtils.respond
import me.melijn.bot.utils.image.ImageUtil
import me.melijn.bot.utils.image.ImageUtil.toInputStream
import net.dv8tion.jda.api.utils.AttachedFile
import org.koin.core.component.inject
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.math.*
import kotlin.random.Random

@KordExtension
class MathExtension : Extension() {

    override val name: String = "math"

    override suspend fun setup() {
        chatCommand(::LineIntersectionsArgs) {
            name = "lineIntersections"

            action {
                val size = 401
                fun rand() = Random.nextInt(size)
                val lines: List<Line> = buildList {
                    repeat(arguments.lineCount) { add((rand() to rand()) to (rand() to rand())) }
                }

                // run-through with line top to bottom.
                val sorted = lines.sortedBy { (p1, p2) -> max(p1.second, p2.second) }
                val pq = PriorityQueue<Pair<Int, EventInfo>> { o1, o2 -> o1.first.compareTo(o2.first) }
                for (line in lines) {
                    val (p1, p2) = line
                    pq.add(p1.second to EventInfo(EventInfo.EventType.START, setOf(line)))
                    pq.add(p2.second to EventInfo(EventInfo.EventType.END, setOf(line)))
                }

                val active = TreeMap<Double, Line>()

//                for ((ey, info) in pq) {
//                    if (info.eventType == EventInfo.EventType.START) {
//                        for (line in lines)
//                        active.put(line)
//                    }
//                }
            }
        }

        chatCommand(::ConvexHullArgs) {
            name = "convexHull"

            action {
                val size = 401
                val points: List<Point> = buildList {
                    repeat(arguments.pointCount) { add(Random.nextInt(size) to Random.nextInt(size)) }
                }
                val canvasImg = ImageUtil.createSquare(size, Color.decode("#ffffff"))
                val canvas = canvasImg.createGraphics()

                // make convex top hull
                drawConvexHalve(points, canvas, false)
                drawConvexHalve(points, canvas, true)

                canvas.dispose()

                for ((x, y) in points) { canvasImg.setRGB(x, y, Color.RED.rgb) }
                respond {
                    this.files += AttachedFile.fromData(canvasImg.toInputStream(), "grid.png")
                }
            }
        }

        chatCommand {
            name = "newtonSqrt"

            action {
                val function = ::findSquareNewtonsMethod
                val findSquareNewtonsMethod = function(1.0, 1e-10, 50_000)
                respond {
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

                    this.files += AttachedFile.fromData(canvasImg.toInputStream(), "grid.png")
                    content = "Result ${function.name}: $findSquareNewtonsMethod"
                }
            }
        }

        chatCommand(::TwoNumberArgs) {
            name = "gcd"

            action {
                val gcd = gcd(arguments.a.parsed, arguments.b.parsed)
                respond("The greatest common denominator is: **${gcd}**")
            }
        }

        chatCommand(::TwoNumberArgs) {
            name = "scm"

            action {
                val scm = scm(arguments.a.parsed, arguments.b.parsed)
                respond("The smallest common multiple is: **${scm}**")
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
                kotlin.runCatching { ImageUtil.writeSafe(img, "jpeg", baos) }
                val bis = ByteArrayInputStream(baos.toByteArray())

                val sent = respond {
                    files += AttachedFile.fromData(bis, "img.png")

                    danger(LATEX_DESTROY_BUTTON_ID, "Destroy")
                }

                buttonCache.latexButtonOwners[AbstractOwnedMessage.from(guild, user, sent)] = true
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

    data class EventInfo(
        val eventType: EventType,
        val lines: Set<Line>
    ) {
        enum class EventType {
            START,
            END,
            INTERSECTION
        }
    }

    fun drawConnectedPoints(canvas: Graphics2D, active: List<Point>) {
        var prevPoint = active.first()
        for (point in active.drop(1)) {
            canvas.paint = Color.BLACK
            canvas.drawLine(prevPoint.first, prevPoint.second, point.first, point.second)
            prevPoint = point
        }
    }

    private fun drawConvexHalve(points: List<Point>, canvas: Graphics2D, inverseRico: Boolean) {
        val sortedX = points.sortedBy { it.first }
        val active = mutableListOf(sortedX.first(), sortedX[1])
        fun rico(left: Point, right: Point): Double {
            val res = (right.second - left.second).toDouble() / (right.first - left.first).toDouble()
            return if (inverseRico) -res else res
        }
        fun prevRico(): Double {
            val last = active.last()
            val secondToLast = active.dropLast(1).lastOrNull() ?: return (if (inverseRico) Double.MAX_VALUE else Double.MAX_VALUE)
            return rico(secondToLast, last)
        }

        for (new in sortedX.drop(2)) {
            // naar rechts draaien = rico goes down.
            while (rico(active.last(), new) > prevRico()) { // LEFT TURN START PRUNING !!!
                active.removeLast()
            }
            active.add(new)
        }

        drawConnectedPoints(canvas, active)
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
    inner class LineIntersectionsArgs : Arguments() {
        val lineCount by int {
            name = "lines"
            description = "amount of lines"
        }
    }
    inner class ConvexHullArgs : Arguments() {
        val pointCount by int {
            name = "points"
            description = "amount of points"
            validate {
                atLeast(name, 3)
            }
        }
        val upper by defaultingBoolean {
            name = "upper-half"
            description = "True by default"
            defaultValue = true
        }
        val lower = defaultingBoolean {
            name = "lower-half"
            description = "True by default"
            defaultValue = true
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

typealias Point = Pair<Int, Int>
typealias Line = Pair<Point, Point>