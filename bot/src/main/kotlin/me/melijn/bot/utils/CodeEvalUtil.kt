@file:Suppress("UNCHECKED_CAST")

package me.melijn.bot.utils

import kotlinx.coroutines.Deferred
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngine
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import javax.script.ScriptEngine

object CodeEvalUtil {

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

    suspend fun runCode(innerCode: String, paramStr: String, vararg params: Any?): String {
        val suppliedImports = innerCode.lines()
            .takeWhile { it.startsWith("import ") || it.startsWith("\nimport ") }
            .joinToString("\n")
        val script = innerCode.lines().dropWhile { suppliedImports.contains(it) }
            .joinToString("\n${"\t".repeat(5)}")
            .replace("return ", "return@evalTaskValueNAsync ")
        val functionName = "exec"
        val functionDefinition = "fun $functionName($paramStr): Deferred<Pair<Any?, String>> {"
        val code = """
                $standardImports
                $suppliedImports
                $functionDefinition
                    return TaskManager.evalTaskValueNAsync {
                        $script
                    }
                }""".trimIndent()

        return evalScript(code, functionName, params)
    }

    private suspend fun evalScript(code: String, functionName: String, vararg params: Any?): String {
        return try {
            engine.eval(code)
            val se = engine as KotlinJsr223JvmLocalScriptEngine
            val resp = se.invokeFunction(functionName, params) as Deferred<Pair<Any?, String>>

            val (result, error) = resp.await()
            result?.toString() ?: "ERROR:\n```${error}```"
        } catch (t: Throwable) {
            "ERROR:\n```${t.message}```"
        }
    }
}