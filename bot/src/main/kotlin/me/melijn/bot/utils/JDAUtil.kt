package me.melijn.bot.utils

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.RestAction
import java.awt.Color
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object JDAUtil {

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
}