package me.melijn.bot.commands.utils

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNames
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.logger.Log
import net.dv8tion.jda.api.utils.AttachedFile
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.internal.LayerPlotContext
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.scale.PositionalTransform
import org.jetbrains.kotlinx.kandy.letsplot.export.toBufferedImage
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.vLine
import org.jetbrains.kotlinx.kandy.letsplot.scales.Transformation
import org.jetbrains.kotlinx.kandy.letsplot.settings.LineType
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.stat.statSmooth
import org.koin.core.component.inject
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.random.Random
import kotlin.random.nextUInt


@KordExtension
class WeatherExtension : Extension() {
    override val name: String = "weather"
    val logger by Log
    val httpClient by inject<WebManager>()

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "rain"
            description = "rain again"

            action {
                val lat = System.getenv("latitude").toDouble()
                val long = System.getenv("longitude").toDouble()

                val buienAlarm = fetchBuienAlarm(lat, long)
                val buienRadar = fetchBuienRadar(lat, long)

                val rainAgains = setOfNotNull(buienRadar, buienAlarm?.first)
                val minEndTime = rainAgains.minOfOrNull { it.endTime } ?: bail("Failed to fetch weather apis")

                // over all predictions, for each all precipitations (until minEndTime) are 0.0
                val noRain = rainAgains.all { it.timePoints.filter { it.key <= minEndTime }.all { it.value == 0.0 }}
                if (noRain) {
                    respond {
                        content = "No rain until at least ${formatTime(minEndTime)}, it is currently ${buienAlarm?.second}°C outside."
                    }
                    return@action
                }

                respond {
                    val scale = 2
                    val plotImg = plot {
                        val crimson = java.awt.Color(220,20,40)
                        val pink = java.awt.Color(255, 153, 184)
                        val purple = java.awt.Color(219, 153, 255)
                        val jColors = listOf(crimson, pink, purple).map { Color.rgb(it.red, it.green, it.blue) }

                        val colors = (listOf(Color.BLUE, Color.LIGHT_BLUE) + jColors).shuffled()
                        for ((i, rainAgain) in rainAgains.withIndex()) {
                            plotPercip(
                                rainAgain,
                                minEndTime,
                                colors[i % colors.size]
                            )
                        }

                        vLine {
                            xIntercept.constant(java.time.LocalTime.now().toSecondOfDay())
                            color = Color.BLACK
                            type = LineType.DASHED
                        }
                    }.toBufferedImage(scale)

                    drawTemperature(plotImg, scale, buienAlarm?.second)

                    val writer = PngWriter.MaxCompression
                    val bais = ImmutableImage.fromAwt(plotImg).forWriter(writer).stream()
                    files.plusAssign(AttachedFile.fromData(bais, "image.png"))
                }
            }
        }
    }

    private fun drawTemperature(
        plotImg: BufferedImage,
        scale: Int,
        temp: Float?
    ) {
        val graphics = plotImg.createGraphics()
        val rh = RenderingHints(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        )
        graphics.setRenderingHints(rh)
        graphics.paint = java.awt.Color.GREEN.darker().darker()
        graphics.font = graphics.font.deriveFont(14f * scale)
        graphics.drawString("${temp ?: "?"}°C", 8 * scale, plotImg.height - 16 * scale)
        graphics.dispose()
    }

    private fun LayerPlotContext.plotPercip(
        rainAgain: RainAgain,
        minEndTime: LocalTime,
        color: Color
    ) {
        val restrictedTimePoints = rainAgain.timePoints.filter { it.key <= minEndTime }
        val points = restrictedTimePoints.values.toList()
        val xsLabels = restrictedTimePoints.keys.map { formatTime(it) }
        val xs = restrictedTimePoints.keys.map { it.toSecondOfDay() }
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
                alpha = 0.3
                fillColor = color
                borderLine.color = color
            }
        }
    }

    private suspend fun fetchBuienAlarm(lat: Double, long: Double): Pair<RainAgain, Float>? {
        val httpResp: HttpResponse = httpClient.httpClient.get(
            "https://cdn-secure.buienalarm.nl/api/3.4/forecast.php?lat=$lat&lon=$long&region=be&unit=mm%2Fu&c=${Random.nextUInt()}"
        )

        val resp = try {
            httpResp.body<BuienAlarmResp>()
        } catch (t: Exception) {
            logger.error("Couldn't parse json from buienalarm.nl\n${httpResp.bodyAsText()}", t)
            return null
        }
        val startTime = resp.startTime

        val points = resp.precip.map { point -> point * 10.0 }

        val timePoints = points.withIndex().associate { (idx, point) ->
            val time = startTime.toJavaLocalTime().plusMinutes((idx * 5).toLong()).toKotlinLocalTime()
            time to point
        }

        return RainAgain(startTime, timePoints, timePoints.keys.max()) to resp.temp
    }

    private suspend fun fetchBuienRadar(lat: Double, long: Double): RainAgain? {
        val httpResp = httpClient.httpClient.get(
            "https://graphdata.buienradar.nl/2.0/forecast/geo/RainHistoryForecast?lat=$lat&lon=$long"
        )
        val resp = try {
            httpResp.body<BuienRadarResp>()
        } catch (t: SerializationException) {
            logger.error("Couldn't parse json from buienradar.nl\n${httpResp.bodyAsText()}", t)
            return null
        }

        val timePoints = resp.forecasts.associate { (time, value) ->
            val scaledPrecip = value * 0.25
            time.time to scaledPrecip
        }

        return RainAgain(timePoints.keys.min(), timePoints, timePoints.keys.max())
    }

    data class RainAgain(
        val startTime: LocalTime,
        val timePoints: Map<LocalTime, Double>,
        val endTime: LocalTime
    )


    private fun formatTime(it: LocalTime) = "${it.hour}:${if (it.minute < 10) "0${it.minute}" else it.minute}"
}


@Serializable
data class BuienRadarResp(
    val forecasts: List<Forecast>
) {
    @Serializable
    data class Forecast(
        val datetime: LocalDateTime,
        val value: Float
    )
}

@Serializable
data class BuienAlarmResp(
    val precip: List<Float>,
    val temp: Float,
    @JsonNames("start_human")
    val startTime: LocalTime
)