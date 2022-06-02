package me.melijn.bot.utils

import dev.kord.common.Color
import dev.kord.core.entity.Guild
import dev.kord.core.entity.User
import dev.kord.rest.Image
import me.melijn.bot.model.enums.DiscordSize

object KordUtil {
    fun User.effectiveAvatarUrl(): String {
        return User.Avatar(data, kord).url
    }

    /**
     * @return png url or gif url based on icon hash (see https://discord.com/developers/docs/reference#image-formatting)))))
     */
    fun Guild.iconUrl(size: DiscordSize = DiscordSize.X2048): String? {
        val hash = data.icon ?: return null
        val format = hashToImageFormat(hash)
        return getIconUrl(format) + size.getParam()
    }

    /**
     * @return png url or gif url based on banner hash (see https://discord.com/developers/docs/reference#image-formatting)))))
     */
    fun Guild.bannerUrl(size: DiscordSize = DiscordSize.X2048): String? {
        val hash = data.banner ?: return null
        val format = hashToImageFormat(hash)
        return getBannerUrl(format) + size.getParam()
    }

    /**
     * @return png url or gif url based on icon hash (see https://discord.com/developers/docs/reference#image-formatting)))))
     */
    fun Guild.splashUrl(size: DiscordSize = DiscordSize.X2048): String? {
        val hash = data.splash.value ?: return null
        val format = hashToImageFormat(hash)
        return getSplashUrl(format) + size.getParam()
    }

    fun Color?.toHex(): String {
        if (this == null) return "null"
        return "#" + this.rgb.toString(16).uppercase()
    }

    private fun hashToImageFormat(hash: String) = if (hash.startsWith("a_")) Image.Format.GIF else Image.Format.PNG
}