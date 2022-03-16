package me.melijn.bot.web.api

import io.ktor.client.*
import io.ktor.client.request.*
import me.melijn.bot.Settings
import me.melijn.bot.model.AnimalSource
import me.melijn.bot.model.AnimalType
import me.melijn.bot.utils.KtorUtils.parametersOf
import mu.KotlinLogging
import org.koin.java.KoinJavaComponent.inject

class AnimalImageApi(private val httpClient: HttpClient) {

    private val settings by inject<Settings>(Settings::class.java)
    private val logger = KotlinLogging.logger { }

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
            httpClient.get<TheCatApiRandomImages>("https://api.thecatapi.com/v1/images/search") {
                header("x-api-key", settings.api.theCatApi.apiKey)
                parametersOf(
                    "limit" to "1",
                    "format" to "json",
                    "order" to "RANDOM"
                )
            }.images.first().url
        } catch (t: Throwable) {
            logger.warn(t) { "TheCatApi failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomSomeRandomApiUrl(tag: String): String? {
        return try {
            httpClient.get<SomeRandomApiRandomImage>("https://some-random-api.ml/img/$tag").link
        } catch (t: Throwable) {
            logger.warn(t) { "SomeRandomApi failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomDuncte123Url(tag: String): String? {
        return try {
            httpClient.get<Duncte123RandomImage>("https://apis.duncte123.me/animal/$tag").data.file
        } catch (t: Throwable) {
            logger.warn(t) { "Duncte123 failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomRandomDukUrl(): String? {
        return try {
            httpClient.get<RandomDukRandomImage>("https://random-d.uk/api/v2/random").url
        } catch (t: Throwable) {
            logger.warn(t) { "RandomDukApi failed to respond properly" }
            null
        }
    }

    private suspend fun getRandomImgHoardUrl(tag: String): String? {
        return try {
            httpClient.get<ImgHoardRandomImage>("https://api.miki.bot/images/random?tags=$tag") {
                header("Authorization", settings.api.imgHoard.token)
            }.url
        } catch (t: Throwable) {
            logger.warn(t) { "ImgHoard failed to respond properly" }
            null
        }
    }
}

data class Duncte123RandomImage(
    val data: Data
) {
    data class Data(
        val file: String
    )
}

data class RandomDukRandomImage(
    val url: String
)

data class TheCatApiRandomImages(
    val images: List<TheCatApiRandomImage>
) {
    data class TheCatApiRandomImage(
        val url: String
    )
}

data class SomeRandomApiRandomImage(
    val link: String
)

data class ImgHoardRandomImage(
    val url: String
)