package me.melijn.bot.utils

import net.dv8tion.jda.api.entities.Member
import java.awt.Color

object JDAUtil {
//
//    /**
//     * @return png url or gif url based on icon hash (see https://discord.com/developers/docs/reference#image-formatting)))))
//     */
//    fun Guild.iconUrl(size: DiscordSize = DiscordSize.X2048): String? {
//        val hash = data.icon ?: return null
//        val format = hashToImageFormat(hash)
//        return getIconUrl(format) + size.getParam()
//    }
//
//    /**
//     * @return png url or gif url based on banner hash (see https://discord.com/developers/docs/reference#image-formatting)))))
//     */
//    fun Guild.bannerUrl(size: DiscordSize = DiscordSize.X2048): String? {
//        val hash = data.banner ?: return null
//        val format = hashToImageFormat(hash)
//        return getBannerUrl(format) + size.getParam()
//    }
//
//    /**
//     * @return png url or gif url based on icon hash (see https://discord.com/developers/docs/reference#image-formatting)))))
//     */
//    fun Guild.splashUrl(size: DiscordSize = DiscordSize.X2048): String? {
//        val hash = data.splash.value ?: return null
//        val format = hashToImageFormat(hash)
//        return getSplashUrl(format) + size.getParam()
//    }

    fun Color?.toHex(): String {
        if (this == null) return "null"
        return "#" + this.rgb.toString(16).uppercase()
    }

    val Member.asTag: String
            get() = user.asTag

//    private fun hashToImageFormat(hash: String) = if (hash.startsWith("a_")) Image.Format.GIF else Image.Format.PNG
}