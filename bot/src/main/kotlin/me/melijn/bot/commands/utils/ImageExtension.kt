package me.melijn.bot.commands.utils

import at.dhyan.open_imaging.GifDecoder
import com.freya02.emojis.Emojis
import com.freya02.emojis.TwemojiType
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.DisposeMethod
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.metadata.ImageMetadata
import com.sksamuel.scrimage.nio.GifWriter
import com.sksamuel.scrimage.nio.ImageWriter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.StringsUtil.prependZeros
import me.melijn.bot.utils.image.GifSequenceWriter
import me.melijn.bot.utils.image.ImageUtil.download
import me.melijn.kordkommons.utils.StringUtils
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.utils.AttachedFile
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

private val Emoji.url: String
    get() {
        return when (this) {
            is CustomEmoji -> this.imageUrl
            is UnicodeEmoji -> Emojis.ofUnicode(this.name).getTwemojiImageUrl(TwemojiType.X72)
            else -> error("Unknown emoji type")
        }
    }

@KordExtension
class ImageExtension : Extension() {

    override val name: String = "image"

    override suspend fun setup() {
        publicSlashCommand {
            name = "image"
            description = "image manipulation"

            group("gif") {
                description = "gif manipulation"

                publicSubCommand(::ConstructArgs) {
                    name = "construct"
                    description = "construct a gif from its frames"

                    action {
                        val frames = arguments.getFrames()
                        val frameDelay = arguments.delay
                        val loopCount = arguments.loopCount

                        val baos = ByteArrayOutputStream()
                        ImageIO.createImageOutputStream(baos).use { ios ->
                            GifSequenceWriter(ios, loopCount).use { writer ->
                                for (frame in frames) {
                                    writer.writeToSequence(frame.awt(), frameDelay)
                                }
                            }
                        }

                        respond {
                            content = "Constructed gif from ${frames.size} frames"
                            files += AttachedFile.fromData(baos.toByteArray(), "constructed.gif")
                        }
                    }
                }

                publicSubCommand(::DeconstructArgs) {
                    name = "deconstruct"
                    description = "deconstruct a gif into its frames"

                    fun fillZeros(i: Int, cutOff: Int): String {
                        val shouldSize = cutOff.toString().length + 1
                        return "$i".prependZeros(shouldSize)
                    }

                    action {
                        val gif = arguments.getGif()

                        val imageWriter = when (arguments.imageType) {
                            ImageType.JPG -> JpegWriter.NoCompression
                            ImageType.PNG -> PngWriter.NoCompression
                            ImageType.WEBP -> WebpWriter.MAX_LOSSLESS_COMPRESSION
                            ImageType.GIF -> GifWriter.Default
                        }

                        ByteArrayOutputStream().use { baos ->
                            ZipOutputStream(baos).use { zos ->
                                for (i in 0 until gif.frameCount) {
                                    val gifFrame = ImmutableImage.fromAwt(gif.getFrame(i))

                                    val zipEntry = ZipEntry(
                                        "frame_${
                                            fillZeros(
                                                i,
                                                gif.frameCount
                                            )
                                        }.${arguments.imageType.name.lowercase()}"
                                    )
                                    zos.putNextEntry(zipEntry)

                                    val frameBytes = gifFrame.bytes(imageWriter)
                                    zos.write(frameBytes)
                                }
                            }

                            respond {
                                content = "Deconstructed gif into ${gif.frameCount} frames"
                                files += AttachedFile.fromData(baos.toByteArray(), "deconstructed.zip")
                            }
                        }
                    }
                }
            }

            group("convert") {
                description = "convert image into a different format"

                publicSubCommand(::JPGConvertArgs) {
                    name = "jpg"
                    description = "convert image into jpg"

                    action {
                        val url = arguments.imageUrl
                        val compression = arguments.compression
                        val progressive = arguments.progressive

                        val data = download(url)
                        val jpegWriter = JpegWriter(compression, progressive)

                        respondConvertedImage(data, jpegWriter)
                    }
                }

                publicSubCommand(::PNGConvertArgs) {
                    name = "png"
                    description = "convert image into png"

                    action {
                        val url = arguments.imageUrl
                        val compression = arguments.compression

                        val data = download(url)
                        val pngWriter = PngWriter(compression)

                        respondConvertedImage(data, pngWriter)
                    }
                }

                publicSubCommand(::WEBPConvertArgs) {
                    name = "webp"
                    description = "convert image into webp"

                    action {
                        val url = arguments.imageUrl

                        // https://developers.google.com/speed/webp/docs/cwebp#options
                        val z = arguments.z
                        val q = arguments.q
                        val m = arguments.m
                        val lossless = arguments.lossless

                        val data = download(url)
                        val pngWriter = WebpWriter(z, q, m, lossless)

                        respondConvertedImage(data, pngWriter)
                    }
                }

                publicSubCommand(::GIFConvertArgs) {
                    name = "gif"
                    description = "convert image into gif"

                    action {
                        val url = arguments.imageUrl
                        val progressive = arguments.progressive

                        val data = download(url)
                        val pngWriter = GifWriter(progressive)

                        respondConvertedImage(data, pngWriter)
                    }
                }

                publicSubCommand(::ConvertArgs) {
                    name = "url"
                    description = "convert image into a url"

                    action {
                        val url = arguments.imageUrl

                        respond {
                            content = "`$url`\n$url"
                        }
                    }
                }
            }
        }
    }

    context(PublicSlashCommandContext<*>)
    private suspend fun respondConvertedImage(data: ByteArray, pngWriter: ImageWriter) {
        fun ImageMetadata.tagByName(name: String) = this.tagsBy { it.name == name }.firstOrNull()?.rawValue
        fun ImageMetadata.fileType(): String = tagByName("Detected File Type Name") ?: "unknown"
        fun ImageMetadata.fileExt(): String = tagByName("Expected File Name Extension") ?: fileType().lowercase()

        val ogImg = ImmutableImage.loader().fromBytes(data)
        val ogSize = StringUtils.humanReadableByteCountBin(data.size)
        val ogType = ogImg.metadata.fileType()

        val newImgBytes = ogImg.bytes(pngWriter)
        val newSize = StringUtils.humanReadableByteCountBin(newImgBytes.size)
        val newMetaData = ImageMetadata.load { newImgBytes }
        val newType = newMetaData.fileType()
        val newExt = newMetaData.fileExt()

        respond {
            content = "**$ogSize** `$ogType` -> **$newSize** `${newType}`"
            files += AttachedFile.fromData(newImgBytes, "image.${newExt}")
        }
    }

    inner class JPGConvertArgs : ConvertArgs() {
        val compression by defaultingInt {
            name = "compression"
            description = "(default: -1 | selects from metadata)"
            minValue = -1
            maxValue = 99
            defaultValue = -1
        }
        val progressive by defaultingBoolean {
            name = "progressive"
            description = "(default: false | some image viewers may not support progressive loaded jpgs)"
            defaultValue = false
        }
    }

    inner class GIFConvertArgs : ConvertArgs() {
        val progressive by defaultingBoolean {
            name = "progressive"
            description = "(default: false | some image viewers may not support progressive loaded jpgs)"
            defaultValue = false
        }
    }

    inner class PNGConvertArgs : ConvertArgs() {
        val compression by defaultingInt {
            name = "compression"
            description = "(default: -1 | selects from metadata)"
            minValue = -1
            maxValue = 9
            defaultValue = -1
        }
    }

    inner class WEBPConvertArgs : ConvertArgs() {
        val z by defaultingInt {
            name = "lossless-level"
            description = "(default: -1 | -1 is off, 0->9 = fast->slow compression)"
            minValue = -1
            maxValue = 9
            defaultValue = -1
        }
        val q by defaultingInt {
            name = "rgb-compression"
            description = "(default: -1 | Compression factor for RGB channels. 0-100)"
            minValue = -1
            maxValue = 100
            defaultValue = -1
        }
        val m by defaultingInt {
            name = "method"
            description = "(default: -1 | 0->6, fast->slow compression)"
            minValue = -1
            maxValue = 6
            defaultValue = -1
        }
        val lossless by defaultingBoolean {
            name = "lossless"
            description = "(default: true)"
            defaultValue = true
        }
    }

    enum class ImageType : InferredChoiceEnum {
        JPG, PNG, WEBP, GIF
    }

    enum class NamedDisposeMethod(val scrimage: DisposeMethod) : InferredChoiceEnum {
        NONE(DisposeMethod.NONE),
        DO_NOT_DISPOSE(DisposeMethod.DO_NOT_DISPOSE),
        RESTORE_TO_BACKGROUND_COLOR(DisposeMethod.RESTORE_TO_BACKGROUND_COLOR),
        RESTORE_TO_PREVIOUS(DisposeMethod.RESTORE_TO_PREVIOUS),
    }

    open inner class ConstructArgs : ImageArgs() {

        val disposeMethod by defaultingEnumChoice<NamedDisposeMethod> {
            name = "dispose-method"
            description = "(default: none)"
            typeName = "disposeMethod"
            defaultValue = NamedDisposeMethod.NONE
        }

        val loopCount by defaultingInt {
            name = "loop-count"
            description = "(default: 0 | 0 = infinite)"
            defaultValue = 0
        }

        val delay by defaultingInt {
            name = "delay-millis"
            description = "(default: 100 | 100ms)"
            defaultValue = 100
        }

        suspend fun getFrames(): List<ImmutableImage> {
            val img = image?.url ?: user?.avatar?.url ?: emoji?.url ?: url ?: error("No image provided")
            val barr = download(img)
            if (barr.size < 4) {
                bail("You will receive coal next christmas for this image. (it's too small)")
            }

            // figure out if it's a gif or zip
            val isGif = barr[0] == 0x47.toByte() && barr[1] == 0x49.toByte() && barr[2] == 0x46.toByte()
            val isZip =
                barr[0] == 0x50.toByte() && barr[1] == 0x4B.toByte() && barr[2] == 0x03.toByte() && barr[3] == 0x04.toByte()
            if (!isGif && !isZip) {
                bail("I could not decode this image as a gif or zip. (maybe it's not a gif or zip?)")
            } else if (isGif) {
                val gif = GifDecoder.read(barr)
                return buildList {
                    for (id in 0 until gif.frameCount) add(
                        ImmutableImage.fromAwt(gif.getFrame(id))
                    )
                }
            } else {
                val zip = ZipInputStream(barr.inputStream())
                val frames = mutableListOf<ImmutableImage>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val entryBytes = zip.readBytes()
                    try {
                        frames += ImmutableImage.loader().fromBytes(entryBytes)
                    } catch (t: Throwable) {
                        bail("One of the files in this zip was not a valid image.")
                    }
                }
                if (frames.isEmpty()) bail("This zip contained no images.")
                return frames
            }
        }
    }

    open inner class DeconstructArgs : ImageArgs() {

        val imageType by defaultingEnumChoice<ImageType> {
            name = "image-type"
            description = "(default: png)"
            typeName = "imageType"
            defaultValue = ImageType.PNG
        }

        suspend fun getGif(): GifDecoder.GifImage {
            val img = image?.url ?: user?.avatar?.url ?: emoji?.url ?: url ?: error("No image provided")
            val barr = download(img)

            try {
                return GifDecoder.read(barr)
            } catch (t: Throwable) {
                bail("I could not decode this image as a gif. (maybe it's not a gif?)")
            }
        }
    }

    open inner class ConvertArgs : ImageArgs() {

        val imageUrl: String
            get() {
                return image?.url ?: user?.avatar?.url ?: emoji?.url ?: url ?: error("No image provided")
            }
    }

    open inner class ImageArgs : Arguments() {
        val image by optionalAttachment {
            name = "image"
            description = "image to convert"
        }
        val user by optionalUser {
            name = "user"
            description = "user avatar to convert"
        }
        val emoji by optionalEmoji {
            name = "emoji"
            description = "emoji to convert"
        }
        val url by optionalString {
            name = "url"
            description = "url to convert"
        }
    }
}