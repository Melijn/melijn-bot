package me.melijn.bot.utils

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
    fun createSquare(size: Int, color: dev.kord.common.Color): BufferedImage =
        createSquare(size, Color(color.rgb))

    fun BufferedImage.toInputStream(format: String = "png"): InputStream {
        val baos = ByteArrayOutputStream()
        ImageIO.write(this, format, baos)
        return ByteArrayInputStream(baos.toByteArray())
    }
}