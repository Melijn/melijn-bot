package me.melijn.bot.commands

import com.freya02.emojis.Emojis
import com.freya02.emojis.TwemojiType
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.metadata.ImageMetadata
import com.sksamuel.scrimage.nio.GifWriter
import com.sksamuel.scrimage.nio.ImageWriter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.ImageUtil.download
import me.melijn.kordkommons.utils.StringUtils
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji
import net.dv8tion.jda.api.utils.AttachedFile

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

    open inner class ConvertArgs : Arguments() {
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

        val imageUrl: String
            get() {
                return image?.url ?: user?.avatar?.url ?: emoji?.url ?: url ?: error("No image provided")
            }
    }
}