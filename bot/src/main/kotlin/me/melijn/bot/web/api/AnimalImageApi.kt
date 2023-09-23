package me.melijn.bot.web.api

import com.kotlindiscord.kord.extensions.koin.KordExKoinComponent
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import me.melijn.bot.commands.thirdparty.AnimalSource
import me.melijn.bot.commands.thirdparty.AnimalType
import me.melijn.bot.utils.Log
import me.melijn.gen.Settings
import org.koin.core.component.inject

class AnimalImageApi(private val httpClient: HttpClient) : KordExKoinComponent {

    private val settings by inject<Settings>()
    private val logger by Log

    suspend fun getRandomAnimalImage(animal: AnimalType): String? {
        val pairs = animal.pairs

        return pairs.firstNotNullOfOrNull { (source, extra) ->
            fetchFromSource(source, extra)
        }
    }

    private suspend fun fetchFromSource(source: AnimalSource, extra: String): String? {
        return when (source) {
            AnimalSource.ImgHoard -> getRandomImgHoardUrl(extra)
            AnimalSource.RandomDuk -> getRandomRandomDukUrl()
            AnimalSource.Duncte123 -> getRandomDuncte123Url(extra)
            AnimalSource.SomeRandomApi -> getRandomSomeRandomApiUrl(extra)
        }
    }

    private suspend fun getRandomSomeRandomApiUrl(tag: String): String? {
        return try {
            httpClient.get("https://some-random-api.com/img/$tag").body<SomeRandomApiRandomImage>().link
        } catch (t: Throwable) {
            logger.warn(t) { "SomeRandomApi failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomDuncte123Url(tag: String): String? {
        return try {
            httpClient.get("https://apis.duncte123.me/animal/$tag").body<Duncte123RandomImage>().data.file
        } catch (t: Throwable) {
            logger.warn(t) { "Duncte123 failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomRandomDukUrl(): String? {
        return try {
            httpClient.get("https://random-d.uk/api/v2/random").body<RandomDukRandomImage>().url
        } catch (t: Throwable) {
            logger.warn(t) { "RandomDukApi failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomImgHoardUrl(tag: String): String? {
        return try {
            httpClient.get("https://api.miki.bot/images/random?tags=$tag") {
                header("Authorization", settings.api.imgHoard.token)
            }.body<ImgHoardRandomImage>().url
        } catch (t: Throwable) {
            logger.warn(t) { "ImgHoard failed to respond properly" }
            null
        }
    }
}

@Serializable
data class Duncte123RandomImage(
    val data: Data
) {

    @Serializable
    data class Data(
        val file: String
    )
}

@Serializable
data class RandomDukRandomImage(
    val url: String
)

@Serializable
data class TheCatApiRandomImage(
    val url: String
)

@Serializable
data class SomeRandomApiRandomImage(
    val link: String
)

@Serializable
data class ImgHoardRandomImage(
    val url: String
)