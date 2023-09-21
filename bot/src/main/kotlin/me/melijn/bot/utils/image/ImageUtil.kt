package me.melijn.bot.utils.image

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.utils.toUpperWordCase
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

object ImageUtil {

    fun createSquare(size: Int, color: Color): BufferedImage {
        val bufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val graphics2D = bufferedImage.createGraphics()
        graphics2D.color = color
        graphics2D.fillRect(0, 0, bufferedImage.width, bufferedImage.height)
        return bufferedImage
    }

    fun BufferedImage.toInputStream(format: String = "png"): InputStream {
        val baos = ByteArrayOutputStream()
        writeSafe(this, format, baos)
        return ByteArrayInputStream(baos.toByteArray())
    }

    public suspend fun download(url: String): ByteArray {
        val webManager by KoinUtil.inject<WebManager>()
        return download(webManager.httpClient, url)
    }

    public suspend fun download(client: HttpClient, url: String): ByteArray {
        val call = client.get(url)
        val contentType = call.headers["Content-Type"]
            ?: error("expected 'Content-Type' header in image request")

        val bytes = call.body<ByteArray>()

        return bytes
    }

    /**
     * @Throws DiscordRelayedException | if there is no image writer for the given [extension]
     * @Trows IOException | if an error occurs during writing or when not able to create required ImageOutputStream.
     */
    fun writeSafe(image: BufferedImage, extension: String, baos: ByteArrayOutputStream) {
        if (!ImageIO.write(image, extension, baos)) bail("Couldn't get a a ${extension.toUpperWordCase()}Writer")
    }
}