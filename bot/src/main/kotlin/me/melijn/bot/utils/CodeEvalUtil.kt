package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import me.melijn.bot.commands.EvalCommand
import me.melijn.bot.utils.script.evalCode
import me.melijn.kordkommons.async.TaskScope
import kotlin.reflect.KClass
import kotlin.script.experimental.api.valueOr

object CodeEvalUtil {
    fun evalScript(code: String, receiver: ChatCommandContext<out EvalCommand.EvalArgs>, klass: KClass<*>, vararg props: Pair<String, Any?>): String {
        return try {
            val results = evalCode(code, receiver, props.toMap())
            return results.also { result ->
                println(result.reports.joinToString("\n") {
                    it.exception?.printStackTrace()
                    it.message
                })
            }.valueOr { failure ->
                return "ERROR:\n```${failure.reports.joinToString("\n") {
                    it.exception?.printStackTrace()
                    it.message
                }}```"
            }.returnValue.toString()
        } catch (t: Throwable) {
            t.printStackTrace()
            "ERROR:\n```${t.message}```"
        }
    }
}