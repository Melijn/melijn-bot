@file:Suppress("UNCHECKED_CAST")

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

//    private val engine: ScriptEngine = KotlinJsr223JvmLocalScriptEngineFactory().scriptEngine
    private val standardImports = """
                import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
                import me.melijn.bot.commands.EvalCommand
                import me.melijn.bot.utils.CodeEvalUtil
                import org.koin.core.component.inject
                import kotlinx.coroutines.Deferred
                import java.io.File
                import javax.imageio.ImageIO
                import kotlinx.coroutines.*""".trimIndent()

//    suspend fun runCode(innerCode: String, receiver: Any, vararg props: Pair<String, Any?>): String {
//        val suppliedImports = innerCode.lines()
//            .takeWhile { it.startsWith("import ") || it.startsWith("\nimport ") }
//            .joinToString("\n")
//        val script = innerCode.lines().dropWhile { suppliedImports.contains(it) }
//            .joinToString("\n${"\t".repeat(5)}")
//            .replace("return ", "return@evalTaskValueNAsync ")
//        val functionName = "exec"
//        val functionDefinition = "fun $functionName($paramStr): Deferred<Pair<Any?, String>> {"
//        val code = """
//                $standardImports
//                $suppliedImports
//                $functionDefinition
//                    return CodeEvalUtil.evalTaskValueNAsync {
//                        $script
//                    }
//                }""".trimIndent()
//
//        return evalScript(code, functionName, props)
//    }

    suspend fun evalScript(code: String, receiver: ChatCommandContext<out EvalCommand.EvalArgs>, klass: KClass<*>, vararg props: Pair<String, Any?>): String {
        return try {
//            val se = engine as KotlinJsr223JvmLocalScriptEngine
//            se.compile(code).eval()
//            engine.eval(code)
            val results = evalCode(code, receiver, props.toMap())
            return results.valueOr {
                return "ERROR:\n```${it.reports.joinToString("\n") { it.message }}```"
            }.returnValue.toString()

//            val resp = se.invokeFunction(functionName, params) as Deferred<Pair<Any?, String>>
//
//            val (result, error) = resp.await()
//            result?.toString() ?: "ERROR:\n```${error}```"
        } catch (t: Throwable) {
            "ERROR:\n```${t.message}```"
        }
    }

    fun <T> evalTaskValueNAsync(block: suspend CoroutineScope.() -> T?): Deferred<Pair<T?, String>> = TaskScope.async {
        try {
            block() to ""
        } catch (t: Throwable) {
            null to (t.message ?: "unknown")
        }
    }
}