package me.melijn.bot.commands.utils

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.logger.Log
import me.melijn.kordkommons.utils.remove
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.internal.LayerPlotContext
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.letsPlot.stat.statSmooth
import org.koin.core.component.inject
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.*
import kotlin.random.Random
import kotlin.random.nextUInt


@KordExtension
class NMBSExtension : Extension() {
    override val name: String = "nmbs"
    val logger by Log
    val httpClient by inject<WebManager>()

    override suspend fun setup() {
        publicGuildSlashCommand(::NmbsArgs) {
            name = "nmbs"
            description = "trein again"

            action {


                respond {

                }
            }
        }
    }

    class NmbsArgs : Arguments() {
        val vertrek by stringChoice {
            name = "vertrek"
            description = "Beginpunt treinconnectie voor gezochte treinreis"
        }

        val tijd by optionalStringChoice {
            handlethin {
                val stringChocies = HashSet()
                for (h in 0..23) {
                    for (m in 0..59) {
                        val volleMinuten = m.toString().prependZeroes(2)
                        if (m == 0) {
                            stringChocies.add("${h}h")
                            stringChocies.add("${h}u")
                        }
                        stringChocies.add("$h:$volleMinuten")
                        stringChocies.add("${h}h$volleMinuten")
                        stringChocies.add("${h}u$volleMinuten")
                    }
                }
                stringChoices
            }
        }

        val date by optionalDateChoice {
            name = "datum"
            description = "Kies een datum dicht bij vandaag anders vinden we mogelijks geen data"
        }



        val 
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


package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Alerts (

    @SerialName("number" ) var number : String?           = null,
    @SerialName("alert"  ) var alert  : ArrayList<String> = arrayListOf()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Arrival (

    @SerialName("delay"               ) var delay               : String?       = null,
    @SerialName("station"             ) var station             : String?       = null,
    @SerialName("stationinfo"         ) var stationinfo         : Stationinfo?  = Stationinfo(),
    @SerialName("time"                ) var time                : String?       = null,
    @SerialName("vehicle"             ) var vehicle             : String?       = null,
    @SerialName("vehicleinfo"         ) var vehicleinfo         : Vehicleinfo?  = Vehicleinfo(),
    @SerialName("platform"            ) var platform            : String?       = null,
    @SerialName("platforminfo"        ) var platforminfo        : Platforminfo? = Platforminfo(),
    @SerialName("canceled"            ) var canceled            : String?       = null,
    @SerialName("direction"           ) var direction           : Direction?    = Direction(),
    @SerialName("arrived"             ) var arrived             : String?       = null,
    @SerialName("walking"             ) var walking             : String?       = null,
    @SerialName("departureConnection" ) var departureConnection : String?       = null

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Connection (

    @SerialName("id"        ) var id        : String?    = null,
    @SerialName("departure" ) var departure : Departure? = Departure(),
    @SerialName("arrival"   ) var arrival   : Arrival?   = Arrival(),
    @SerialName("vias"      ) var vias      : Vias?      = Vias(),
    @SerialName("duration"  ) var duration  : String?    = null,
    @SerialName("remarks"   ) var remarks   : Remarks?   = Remarks(),
    @SerialName("alerts"    ) var alerts    : Alerts?    = Alerts()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Departure (

    @SerialName("delay"               ) var delay               : String?       = null,
    @SerialName("station"             ) var station             : String?       = null,
    @SerialName("stationinfo"         ) var stationinfo         : Stationinfo?  = Stationinfo(),
    @SerialName("time"                ) var time                : String?       = null,
    @SerialName("vehicle"             ) var vehicle             : String?       = null,
    @SerialName("vehicleinfo"         ) var vehicleinfo         : Vehicleinfo?  = Vehicleinfo(),
    @SerialName("platform"            ) var platform            : String?       = null,
    @SerialName("platforminfo"        ) var platforminfo        : Platforminfo? = Platforminfo(),
    @SerialName("canceled"            ) var canceled            : String?       = null,
    @SerialName("stops"               ) var stops               : Stops?        = Stops(),
    @SerialName("departureConnection" ) var departureConnection : String?       = null,
    @SerialName("direction"           ) var direction           : Direction?    = Direction(),
    @SerialName("left"                ) var left                : String?       = null,
    @SerialName("walking"             ) var walking             : String?       = null,
    @SerialName("occupancy"           ) var occupancy           : Occupancy?    = Occupancy()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Direction (

    @SerialName("name" ) var name : String? = null

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class ExampleJson2KtKotlin (

    @SerialName("version"    ) var version    : String?               = null,
    @SerialName("timestamp"  ) var timestamp  : String?               = null,
    @SerialName("connection" ) var connection : ArrayList<Connection> = arrayListOf()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Occupancy (

    @SerialName("@id"  ) var @id  : String? = null,
    @SerialName("name" ) var name : String? = null

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Platforminfo (

    @SerialName("name"   ) var name   : String? = null,
    @SerialName("normal" ) var normal : String? = null

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Remarks (

    @SerialName("number" ) var number : String?           = null,
    @SerialName("remark" ) var remark : ArrayList<String> = arrayListOf()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Stationinfo (

    @SerialName("@id"          ) var @id          : String? = null,
    @SerialName("id"           ) var id           : String? = null,
    @SerialName("name"         ) var name         : String? = null,
    @SerialName("locationX"    ) var locationX    : String? = null,
    @SerialName("locationY"    ) var locationY    : String? = null,
    @SerialName("standardname" ) var standardname : String? = null

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Stop (

    @SerialName("id"                     ) var id                     : String?       = null,
    @SerialName("station"                ) var station                : String?       = null,
    @SerialName("stationinfo"            ) var stationinfo            : Stationinfo?  = Stationinfo(),
    @SerialName("scheduledArrivalTime"   ) var scheduledArrivalTime   : String?       = null,
    @SerialName("arrivalCanceled"        ) var arrivalCanceled        : String?       = null,
    @SerialName("arrived"                ) var arrived                : String?       = null,
    @SerialName("scheduledDepartureTime" ) var scheduledDepartureTime : String?       = null,
    @SerialName("arrivalDelay"           ) var arrivalDelay           : String?       = null,
    @SerialName("departureDelay"         ) var departureDelay         : String?       = null,
    @SerialName("departureCanceled"      ) var departureCanceled      : String?       = null,
    @SerialName("left"                   ) var left                   : String?       = null,
    @SerialName("isExtraStop"            ) var isExtraStop            : String?       = null,
    @SerialName("platform"               ) var platform               : String?       = null,
    @SerialName("platforminfo"           ) var platforminfo           : Platforminfo? = Platforminfo()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Stops (

    @SerialName("number" ) var number : String?         = null,
    @SerialName("stop"   ) var stop   : ArrayList<Stop> = arrayListOf()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Vehicleinfo (

    @SerialName("name"      ) var name      : String? = null,
    @SerialName("shortname" ) var shortname : String? = null,
    @SerialName("number"    ) var number    : String? = null,
    @SerialName("type"      ) var type      : String? = null,
    @SerialName("locationX" ) var locationX : String? = null,
    @SerialName("locationY" ) var locationY : String? = null,
    @SerialName("@id"       ) var @id       : String? = null

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Via (

    @SerialName("id"          ) var id          : String?      = null,
    @SerialName("arrival"     ) var arrival     : Arrival?     = Arrival(),
    @SerialName("departure"   ) var departure   : Departure?   = Departure(),
    @SerialName("timebetween" ) var timebetween : String?      = null,
    @SerialName("station"     ) var station     : String?      = null,
    @SerialName("stationinfo" ) var stationinfo : Stationinfo? = Stationinfo(),
    @SerialName("vehicle"     ) var vehicle     : String?      = null,
    @SerialName("vehicleinfo" ) var vehicleinfo : Vehicleinfo? = Vehicleinfo()

)package me.melijn.bot.commands.thirdparty

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class Vias (

    @SerialName("number" ) var number : String?        = null,
    @SerialName("via"    ) var via    : ArrayList<Via> = arrayListOf()

)