package me.melijn.bot.commands.utils

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.web.api.WebManager
import net.dv8tion.jda.api.utils.AttachedFile
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toBufferedImage
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.vLine
import org.jetbrains.kotlinx.kandy.letsplot.scales.continuousColorGradient2
import org.jetbrains.kotlinx.kandy.letsplot.util.linetype.LineType
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.letsPlot.stat.statSmooth
import org.koin.core.component.inject
import java.time.Duration
import java.time.LocalTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinDuration


@KordExtension
class WeatherExtension : Extension() {
    override val name: String = "weather"
    val httpClient by inject<WebManager>()

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "rain"
            description = "rain again"

            action {
                val lat = System.getenv("latitude")
                val long = System.getenv("longitude")
                val forecast: HttpResponse = httpClient.httpClient.get(
                    "https://cdn-secure.buienalarm.nl/api/3.4/forecast.php?lat=$lat&lon=$long&region=be&unit=mm%2Fu&c=${Random.nextInt()}"
                )
                respond {
                    val json = forecast.body<JsonObject>()

                    val start = json["start_human"]?.jsonPrimitive.toString().split(":")
                    val startHour = start.first().toIntOrNull()
                    val startMinute = start.last().toIntOrNull()

                    val currentTime = LocalTime.now()
                    val startTime = if (startHour != null && startMinute != null) {
                        LocalTime.of(startHour, startMinute)
                    } else {
                        currentTime
                    }
                    val currentTimeLine: Double = Duration.between(startTime, currentTime).toKotlinDuration() / 5.minutes

                    val points = json["precip"]?.jsonArray?.map {
                        (it.jsonPrimitive.float * 10.0) + 10
                    } ?: bail("Couldn't fetch rain data")
                    val xsLabels = List(points.size) { i ->
                        val time =
                            startTime.plusMinutes(((i * 5).toLong()))
                        "${time.hour}:${if (time.minute < 10) "0${time.minute}" else time.minute}"
                    }
                    val xs = List(points.size) { i -> i.toDouble() }
                    val dataFrame = dataFrameOf(
                        "xs" to xs,
                        "ys" to points
                    )
                    val plotImg = plot(dataFrame) {
                        statSmooth(data = dataFrame.toMap()) {
                            area {
                                x(xs, "time") {
                                    axis.breaksLabeled(
                                        xs.filterIndexed { index, _ -> index % 3 == 0 },
                                        xsLabels.filterIndexed { index, _ -> index % 3 == 0 })
                                }
                                y(points, "strength") {
                                    scale = continuous(0.0..25.0)

                                }
                                alpha = 0.5
                                fillColor = Color.BLUE
                                borderLine.color = Color.BLUE
                            }
                        }
                        vLine {
                            xIntercept.constant(currentTimeLine)
                            color = Color.BLACK
                            type = LineType.DASHED
                        }
                    }.toBufferedImage(2)

                    val writer = PngWriter(3)
                    println(points)
                    val bais = ImmutableImage.fromAwt(plotImg).forWriter(writer).stream()
                    files.plusAssign(AttachedFile.fromData(bais, "image.png"))
                }
            }
        }
    }
}