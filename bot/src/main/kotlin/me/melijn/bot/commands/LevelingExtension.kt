package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.sksamuel.scrimage.ImmutableImage
import dev.kord.rest.Image
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.XPManager
import org.koin.core.component.inject
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.roundToInt

@KordExtension
class LevelingExtension : Extension() {

    override val name: String = "leveling"
    val xpManager by inject<XPManager>()

    override suspend fun setup() {
        publicSlashCommand {
            name = "xp"
            description = "xp"

            action {
                val xp = xpManager.getGlobalXP(user.id)
                respond {
                    val bufferedImage = LevelingExtension::class.java.getResourceAsStream("/slice2.png").use {
                        ImmutableImage.wrapAwt(ImageIO.read(it))
                    }.awt()
                    val bars = drawXpCard(bufferedImage, xp)

                    val baos = ByteArrayOutputStream()
                    ImageIO.write(bars, "png", baos)
                    val bais = ByteArrayInputStream(baos.toByteArray())

                    addFile("file.png", bais)
                }
            }
        }
        publicSlashCommand(::SetXPArgs) {
            name = "setxp"
            description = "gives xp"

            action {
                val xp = arguments.xp.parsed
                val user = arguments.user.parsed

                xpManager.setGlobalXP(user.id, xp.toULong())

                respond {
                    content = "${user.tag} xp: $xp"
                }
            }
        }

    }

    private suspend fun PublicSlashCommandContext<Arguments>.drawXpCard(
        bufferedImage: BufferedImage,
        xp: ULong
    ): BufferedImage {
        val graphics = bufferedImage.createGraphics()
        val user = user.asUser()

        /** Draw avatar **/
        val avatarData =
            (user.asUser().avatar ?: user.defaultAvatar).getImage(Image.Format.PNG, Image.Size.Size512).data
        val avatarImg = ImmutableImage.loader().fromBytes(avatarData).awt()
        graphics.drawImage(avatarImg, 56, 176, 408, 408, null)

        /** Draw username **/
        val arial = LevelingExtension::class.java.getResourceAsStream("/arial.ttf")
            .use { Font.createFont(Font.TRUETYPE_FONT, it) }
        graphics.font = arial.deriveFont(90f)
        graphics.paint = Color.decode("#BABABA")
        val rh = RenderingHints(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        )
        graphics.setRenderingHints(rh)
        graphics.drawString(user.tag, 174, 140)

        /** Draw XP text **/
        graphics.font = arial.deriveFont(50f)
        val base = 1.6
        val level = getLevel(xp, base)
        val xpLower = floor(base.pow(level.toDouble())).toLong()
        val xpUpper = floor(base.pow((level + 1).toDouble())).toLong()

        /** Relative xp to level variables **/
        val progressToNextLevel = xp - xpLower.toUInt()
        val nextLevelThreshold = xpUpper - xpLower

        val text = "$progressToNextLevel/$nextLevelThreshold XP | Level: $level"
        val textWidth = graphics.fontMetrics.stringWidth(text)
        graphics.drawString(text, 1586 - textWidth, 230)
        graphics.drawString(text, 1586 - textWidth, 449)

        /** Draw XP bars **/
        val bars = BufferedImage(bufferedImage.width, bufferedImage.height, bufferedImage.type)
        val barGraphics = bars.createGraphics()
        barGraphics.paint = Color.decode("#142235")
        val percente = progressToNextLevel.toDouble() / nextLevelThreshold.toDouble()
        val end = (percente * 956).roundToInt()
        barGraphics.fillRect(645, 250, end, 120)
        barGraphics.fillRect(645, 470, end, 120)
        barGraphics.paint = Color.decode("#635C5C")
        barGraphics.fillRect(645 + end, 250, (956 - end), 120)
        barGraphics.fillRect(645 + end, 470, (956 - end), 120)
        barGraphics.drawImage(bufferedImage, 0, 0, null)
        return bars
    }

    private fun getLevel(xp: ULong, base: Double): Long {
        val level = log(xp.toDouble(), base)
        return floor(level).toLong()
    }

    inner class SetXPArgs : Arguments() {
        val user = user {
            name = "user"
            description = "user"
        }
        val xp = long {
            name = "xp"
            description = "Sets xp lol"
        }
    }
}