package me.melijn.bot.utils

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.InlineEmbed
import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.awt.Color
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object JDAUtil {

    fun Color?.toHex(): String {
        if (this == null) return "null"
        // TODO: Fix nevativ hex numbers
        return "#" + this.rgb.toString(16).uppercase()
    }

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


    suspend inline fun MessageChannel.createMessage(s: String): Message {
        return sendMessage(s).await()
    }

    suspend inline fun MessageChannel.createMessage(builder: InlineMessage<MessageCreateData>.() -> Unit): Message {
        return sendMessage(MessageCreate { builder() }).await()
    }

    suspend inline fun MessageChannel.createEmbed(builder: InlineEmbed.() -> Unit): Message {
        return sendMessage(MessageCreate { embed { builder() } }).await()
    }
}