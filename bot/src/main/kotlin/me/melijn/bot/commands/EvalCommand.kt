package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import kotlinx.coroutines.Deferred
import me.melijn.bot.utils.KordeUtils.userIsOwner
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngine
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import javax.script.ScriptEngine

class EvalCommand : Extension() {

    override val name: String = "eval"

    override suspend fun setup() {
        chatCommand(::EvalArgs) {
            name = this@EvalCommand.name
            description = "evaluating code"
            check {
                userIsOwner()
            }

            action {
                val msg = this.channel.createMessage {
                    this.content = "Executing code.."
                }

                val result = runCode(this.argString, this)

                msg.edit {
                    this.content = "Done!\nResult: $result"
                }
            }
        }
    }

    companion object {
        private val engine: ScriptEngine = KotlinJsr223JvmLocalScriptEngineFactory().scriptEngine
        private val standardImports = """
                import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
                import me.melijn.bot.commands.EvalCommand
                import me.melijn.bot.utils.threading.TaskManager
                import dev.kord.core.behavior.channel.createMessage
                import dev.kord.core.behavior.edit
                import org.koin.core.component.inject
                import kotlinx.coroutines.Deferred
                import java.io.File
                import javax.imageio.ImageIO
                import kotlinx.coroutines.*""".trimIndent()


        suspend fun runCode(innerCode: String, context: ChatCommandContext<out EvalCommand.EvalArgs>? = null): String {
            val global = context == null
            val suppliedImports = innerCode.lines()
                .takeWhile { it.startsWith("import ") || it.startsWith("\nimport ") }
                .joinToString("\n\t\t\t")
            val script = innerCode.lines().dropWhile { suppliedImports.contains(it) }
                .joinToString("\n\t\t\t\t\t")
                .replace("return ", "return@evalTaskValueNAsync ")
            val functionName = "exec"
            val funcParams = if (global) "" else "context: ChatCommandContext<out EvalCommand.EvalArgs>"
            val functionDefinition = "fun $functionName($funcParams): Deferred<Pair<Any?, String>> {"
            val code = """
                $standardImports
                $suppliedImports
                $functionDefinition
                    return TaskManager.evalTaskValueNAsync {
                        $script
                    }
                }""".trimIndent()

            return try {
                engine.eval(code)
                val se = engine as KotlinJsr223JvmLocalScriptEngine

                val resp = (if (global) {
                    se.invokeFunction(functionName)
                } else {
                    se.invokeFunction(functionName, context)
                }) as Deferred<Pair<Any?, String>>

                val (result, error) = resp.await()
                result?.toString() ?: "ERROR:\n```${error}```"
            } catch (t: Throwable) {
                "ERROR:\n```${t.message}```"
            }
        }
    }

    inner class EvalArgs : Arguments() {
        val code = string {
            this.name = "code"
            this.description = "code to execute"
        }
    }
}