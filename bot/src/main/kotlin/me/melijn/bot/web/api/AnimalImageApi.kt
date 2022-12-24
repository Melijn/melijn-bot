package me.melijn.bot.web.api

import com.kotlindiscord.kord.extensions.koin.KordExKoinComponent
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import me.melijn.bot.commands.AnimalSource
import me.melijn.bot.commands.AnimalType
import me.melijn.bot.utils.KtorUtils.parametersOf
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
            AnimalSource.TheCatApi -> getRandomTheCatApiUrl()
        }
    }

    private suspend fun getRandomTheCatApiUrl(): String? {
        return try {
            httpClient.get("https://api.thecatapi.com/v1/images/search") {
                header("x-api-key", settings.api.theCatApi.apiKey)
                parametersOf(
                    "limit" to "1",
                    "format" to "json",
                    "order" to "RANDOM"
                )
            }.body<List<TheCatApiRandomImages.TheCatApiRandomImage>>().first().url
        } catch (t: Throwable) {
            logger.warn(t) { "TheCatApi failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomSomeRandomApiUrl(tag: String): String? {
        return try {
            httpClient.get("https://some-random-api.ml/img/$tag").body<SomeRandomApiRandomImage>().link
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

@kotlinx.serialization.Serializable
data class Duncte123RandomImage(
    val data: Data
) {

    @kotlinx.serialization.Serializable
    data class Data(
        val file: String
    )
}

@kotlinx.serialization.Serializable
data class RandomDukRandomImage(
    val url: String
)

@kotlinx.serialization.Serializable
data class TheCatApiRandomImages(
    val images: List<TheCatApiRandomImage>
) {

    @kotlinx.serialization.Serializable
    data class TheCatApiRandomImage(
        val url: String
    )
}

@kotlinx.serialization.Serializable
data class SomeRandomApiRandomImage(
    val link: String
)

@kotlinx.serialization.Serializable
data class ImgHoardRandomImage(
    val url: String
)