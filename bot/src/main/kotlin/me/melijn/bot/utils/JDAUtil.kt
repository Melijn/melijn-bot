package me.melijn.bot.utils

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.RestAction
import java.awt.Color
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    suspend fun <T> RestAction<T>.awaitOrNull() = suspendCoroutine<T?> {
        queue(
            { success ->
                it.resume(success)
            },
            { _ ->
                it.resume(null)
            }
        )
    }

    suspend fun <T> RestAction<T>.awaitEX() = suspendCoroutine<Throwable?> {
        queue(
            { _ -> it.resume(null) },
            { throwable -> it.resume(throwable) }
        )
    }

    suspend fun <T> RestAction<T>.awaitBool() = suspendCoroutine<Boolean> {
        queue(
            { _ -> it.resume(true) },
            { _ -> it.resume(false) }
        )
    }

//    private fun hashToImageFormat(hash: String) = if (hash.startsWith("a_")) Image.Format.GIF else Image.Format.PNG
}