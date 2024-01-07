package me.melijn.bot.commands.utils

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.web.api.WebManager
import net.dv8tion.jda.api.utils.AttachedFile
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.internal.LayerPlotContext
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toBufferedImage
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.vLine
import org.jetbrains.kotlinx.kandy.letsplot.util.linetype.LineType
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.kandy.util.color.StandardColor
import org.jetbrains.letsPlot.stat.statSmooth
import org.koin.core.component.inject
import java.time.Duration
import java.time.LocalDateTime
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
                println(forecast)
                val forecast2: HttpResponse? = try {
                    httpClient.httpClient.get(
                        "https://graphdata.buienradar.nl/2.0/forecast/geo/RainHistoryForecast?lat=$lat&lon=$long"
                    )
                } catch (t: Throwable) {
                    t.printStackTrace()
                    null
                }
                println(forecast2)
                respond {
                    val json = forecast.body<JsonObject>()
                    val json2 = forecast2?.body<JsonObject>()

                    val forecasts2 = json2?.get("forecasts")?.jsonArray
                    val start = json["start_human"]?.jsonPrimitive.toString().split(":")
                    val start2 = LocalDateTime.parse(forecasts2?.get(0)?.jsonObject?.get("datetime")?.jsonPrimitive.toString())
//                    val start2 = json2["start_human"]?.jsonPrimitive.toString().split(":")

                    val points = json["precip"]?.jsonArray?.map {
                        (it.jsonPrimitive.float * 10.0)
                    } ?: bail("Couldn't fetch rain data")

                    val points2 = forecasts2?.map {
                        (it.jsonObject.get("precipation")?.jsonPrimitive?.float?.times(0.25)) ?: 0.0
                    } ?: bail("Couldn't fetch rain data")

                    val plotImg = plot {
                        val currentTimeLine: Double = plotPercip(start.firstOrNull()?.toIntOrNull(), start.lastOrNull()?.toIntOrNull(), points, Color.BLUE)
                        plotPercip(start2.hour, start2.minute, points2, Color.LIGHT_BLUE)
                        vLine {
                            xIntercept.constant(currentTimeLine)
                            color = Color.BLACK
                            type = LineType.DASHED
                        }
                    }.toBufferedImage(2)

                    val writer = PngWriter(3)
                    val bais = ImmutableImage.fromAwt(plotImg).forWriter(writer).stream()
                    files.plusAssign(AttachedFile.fromData(bais, "image.png"))
                }
            }
        }
    }

    private fun LayerPlotContext.plotPercip(
        startHour: Int?,
        startMinute: Int?,
        points: List<Double>,
        color: StandardColor.Hex
    ): Double {
        val currentTime = LocalTime.now()
        val startTime = if (startHour != null && startMinute != null) {
            LocalTime.of(startHour, startMinute)
        } else {
            currentTime
        }
        val currentTimeLine: Double =
            Duration.between(startTime, currentTime).toKotlinDuration() / 5.minutes


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
                fillColor = color
                borderLine.color = color
            }
        }
        return currentTimeLine
    }
}