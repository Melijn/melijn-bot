package me.melijn.bot.commands.utils

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingEnumChoice
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.TimeUtil.plus
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.logger.Log
import me.melijn.kordkommons.utils.remove
import net.dv8tion.jda.api.utils.AttachedFile
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.internal.LayerPlotContext
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toBufferedImage
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.vLine
import org.jetbrains.kotlinx.kandy.letsplot.settings.LineType
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.letsPlot.stat.statSmooth
import org.koin.core.component.inject
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds


@KordExtension
class WeatherExtension : Extension() {
    override val name: String = "weather"
    val logger by Log
    val httpClient by inject<WebManager>()

    override suspend fun setup() {
        publicGuildSlashCommand(::WeatherArgs) {
            name = "rain"
            description = "rain again"

            action {
                val lookaheadTime = this.arguments.lookahead
                val lat = System.getenv("latitude").toDouble()
                val long = System.getenv("longitude").toDouble()

                val buienAlarm = fetchBuienAlarmRainData(lookaheadTime, lat, long)
                val buienRadar = fetchBuienRadar(lookaheadTime, lat, long)

                val rainAgains = setOfNotNull(buienRadar, buienAlarm?.first)
                val minEndTime = rainAgains.minOfOrNull { it.endTime } ?: bail("Failed to fetch weather apis")

                // over all predictions, for each all precipitations (until minEndTime) are 0.0
                val noRain = rainAgains.all { it.timePoints.filter { it.key <= minEndTime }.all { it.value == 0.0 } }
                if (noRain) {
                    respond {
                        content =
                            "No rain until at least ${formatTime(minEndTime)}, it is currently ${buienAlarm?.second}°C outside."
                    }
                    return@action
                }

                respond {
                    val scale = 2
                    val plotImg = plot {
                        val crimson = java.awt.Color(220, 20, 40)
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
                            xIntercept.constant(Clock.System.now().epochSeconds)
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

    enum class LookaheadTime : InferredChoiceEnum {
        TwoHours, Day
    }

    class WeatherArgs : Arguments() {
        val lookahead by defaultingEnumChoice<LookaheadTime> {
            name = "lookahead"
            description = "Lookahead time [default 2 hours]"
            defaultValue = LookaheadTime.TwoHours
            typeName = "piss"
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
        minEndTime: LocalDateTime,
        color: Color
    ) {
        val restrictedTimePoints = rainAgain.timePoints.filter { it.key <= minEndTime }
        val points = restrictedTimePoints.values.toList()
        val xsLabels = restrictedTimePoints.keys.map { formatTime(it) }
        val xs = restrictedTimePoints.keys.map { it.toInstant(TimeZone.of("Europe/Brussels")).epochSeconds }
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
                y(points, "mm/4u") {
                    scale = continuous(0.0..25.0)
                }
                alpha = 0.3
                fillColor = color
                borderLine.color = color
            }
        }
    }

    private suspend fun fetchBuienAlarmRainData(
        lookaheadTime: LookaheadTime,
        lat: Double,
        long: Double
    ): Pair<RainAgain, Float>? {
        return when (lookaheadTime) {
            LookaheadTime.TwoHours -> {
                val apiData = fetchBuienAlarmApi(lat, long) ?: return null
                RainAgain.fromBuienAlarmApiData(apiData)
            }

            LookaheadTime.Day -> {
                val siteData = fetchBuienAlarmSiteData(lat, long) ?: return null
                RainAgain.fromBuienAlarmWeatherData(siteData)
            }
        }
    }

    private suspend fun fetchBuienAlarmSiteData(lat: Double, long: Double): BuienAlarmWeatherData? {
        val httpResp: HttpResponse = httpClient.httpClient.get("https://www.buienalarm.nl/$lat,$long")

        val body = httpResp.bodyAsText()
        return try {
            val toMatch = "var weatherData = "
            val weatherDataStartPos = body.lastIndexOf(toMatch) + toMatch.length
            val lineJs = body.substring(weatherDataStartPos).takeWhile { it != ';' }.remove("+00:00")
            Json.decodeFromString<BuienAlarmWeatherData>(lineJs)

        } catch (t: Exception) {
            logger.error("Couldn't parse html weatherdata from buienalarm.nl\n", t)
            return null
        }
    }

    private suspend fun fetchBuienAlarmApi(lat: Double, long: Double): BuienAlarmResp? {
        val httpResp: HttpResponse = httpClient.httpClient.get(
            "https://cdn-secure.buienalarm.nl/api/3.4/forecast.php?lat=$lat&lon=$long&region=be&unit=mm%2Fu&c=${Random.nextUInt()}"
        )

        try {
            return httpResp.body<BuienAlarmResp>()
        } catch (t: Exception) {
            logger.error("Couldn't parse json from buienalarm.nl\n${httpResp.bodyAsText()}", t)
            return null
        }
    }

    private suspend fun fetchBuienRadar(lookaheadTime: LookaheadTime, lat: Double, long: Double): RainAgain? {
        val url = when (lookaheadTime) {
            LookaheadTime.TwoHours -> "https://graphdata.buienradar.nl/2.0/forecast/geo/RainHistoryForecast?lat=$lat&lon=$long"
            LookaheadTime.Day -> "https://graphdata.buienradar.nl/2.0/forecast/geo/Rain24Hour?lat=$lat&lon=$long&btc=202401250032&ak=${UUID.randomUUID()}"
        }

        val httpResp = httpClient.httpClient.get(url)
        val resp = try {
            httpResp.body<BuienRadarResp>()
        } catch (t: SerializationException) {
            logger.error("Couldn't parse json from buienradar.nl\n${httpResp.bodyAsText()}", t)
            return null
        }

        val timePoints = resp.forecasts.associate { (time, value) ->
            val scaledPrecip = value * 0.25
            time to scaledPrecip
        }

        return RainAgain(timePoints.keys.min(), timePoints, timePoints.keys.max())
    }

    data class RainAgain(
        val startTime: LocalDateTime,
        val timePoints: Map<LocalDateTime, Double>,
        val endTime: LocalDateTime
    ) {
        companion object
    }


    private fun formatTime(dateTime: LocalDateTime) = formatTime(dateTime.time)
    private fun formatTime(it: LocalTime) = "${it.hour}:${if (it.minute < 10) "0${it.minute}" else it.minute}"
}

private fun WeatherExtension.RainAgain.Companion.fromBuienAlarmApiData(apiData: BuienAlarmResp): Pair<WeatherExtension.RainAgain, Float> {
    val startTime = Instant.fromEpochSeconds(apiData.startEpochSeconds).toLocalDateTime(TimeZone.of("Europe/Brussels"))
    val points = apiData.precip.map { point -> point * 10.0 }
    val timePoints = points.withIndex().associate { (idx, point) ->
        val time = (startTime + (idx * 5).minutes)
        time to point
    }

    return WeatherExtension.RainAgain(startTime, timePoints, timePoints.keys.max()) to apiData.temp
}



private fun WeatherExtension.RainAgain.Companion.fromBuienAlarmWeatherData(siteData: BuienAlarmWeatherData): Pair<WeatherExtension.RainAgain, Float> {
    val hourData = siteData.hours
    val firstHourInstance = hourData.first()
    return WeatherExtension.RainAgain(
        firstHourInstance.date,
        hourData.associate { it.date to it.rain.toDouble() },
        hourData.last().date
    ) to firstHourInstance.temp
}


@Serializable
data class BuienAlarmWeatherData(
    val minutes: List<WeatherData>,
    val hours: List<WeatherData>
) {
    @Serializable
    data class WeatherData(
        val date: LocalDateTime,
        val rain: Float, // mm/hour
        val temp: Float
    )
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
    @JsonNames("start")
    val startEpochSeconds: Long
)