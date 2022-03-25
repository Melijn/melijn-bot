package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.stringList
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import me.melijn.annotationprocessors.command.KordExtension
import me.melijn.bot.utils.CodeEvalUtil
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
                val msg = channel.createMessage {
                    content = "Executing code.."
                }

                val paramStr = "context: ChatCommandContext<out EvalCommand.EvalArgs>"
                val result = CodeEvalUtil.runCode(argString, paramStr, this)

                msg.edit {
                    content = "Done!\nResult: $result"
                }
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