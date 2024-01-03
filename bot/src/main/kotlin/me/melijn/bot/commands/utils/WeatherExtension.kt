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
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.letsPlot.stat.statSmooth
import org.koin.core.component.inject
import kotlin.random.Random


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
                    val points = json.get("precip")?.jsonArray?.map {
                        (it.jsonPrimitive.float * 10).toLong()
                    } ?: bail("Couldn't fetch rain data")
                    val xs = List(points.size) { i -> val time =
                        java.time.LocalTime.now().plusMinutes(((i * 5).toLong()))
                        "${time.hour}:${if (time.minute < 10) "0${time.minute}" else time.minute}"
                    }
                    val dataFrame = dataFrameOf(
                        "xs" to xs,
                        "ys" to points
                    )
                    val plotImg = plot(dataFrame) {
                        statSmooth(data = dataFrame.toMap()) {
                            area {
                                x(xs, "time")
                                y(points, "rain strength") {
                                    scale = continuous(0..25L)
                                }
                                alpha = 0.5
                                fillColor = Color.BLUE
                                borderLine.color = Color.BLUE
                            }
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