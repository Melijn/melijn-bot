package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.stringList
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.MessageEdit
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.CodeEvalUtil
import me.melijn.bot.utils.KordExUtils.respond
import me.melijn.bot.utils.KordExUtils.userIsOwner

@KordExtension
class EvalCommand : Extension() {

    override val name: String = "eval"

    override suspend fun setup() {
        chatCommand(::EvalArgs) {
            name = "eval"
            description = "evaluating code"
            check {
                userIsOwner()
            }

            action {
                val msg = respond("Executing code..")

                val result = CodeEvalUtil.evalScript(argString, this, this::class, "bestNumber" to this)

                msg.editMessage(MessageEdit {
                    content = "Done!\nResult: $result"
                }).await()
            }
        }
    }

    inner class EvalArgs : Arguments() {
        val code = stringList {
            name = "code"
            description = "code to execute"
        }
    }
}