package me.melijn.bot.commands

import com.freya02.emojis.Emojis
import com.freya02.emojis.TwemojiType
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.ImageUtil.download
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
                        val imageOut = ImmutableImage.loader()
                            .fromBytes(data)
                            .toImmutableImage()
                            .bytes(jpegWriter)

                        respond {
                            content = "converted image into jpg"
                            files += AttachedFile.fromData(imageOut, "image.jpg")
                        }
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