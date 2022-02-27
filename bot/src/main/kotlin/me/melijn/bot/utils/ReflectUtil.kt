package me.melijn.bot.utils

import java.io.BufferedReader
import java.io.InputStreamReader

object ReflectUtil {

    fun findAllClassesUsingClassLoader(packageName: String): Sequence<Class<*>?> {
        val stream = ClassLoader.getSystemClassLoader()
            .getResourceAsStream(packageName.replace("[.]".toRegex(), "/"))
            ?: return emptySequence()

        val reader = BufferedReader(InputStreamReader(stream))
        return reader.lineSequence()
            .filter { line: String -> line.endsWith(".class") }
            .map { line: String ->
                getClass(
                    line,
                    packageName
                )
            }
            .filter { it != null }
    }

    private fun getClass(className: String, packageName: String): Class<*>? {
        try {
            return Class.forName(
                packageName + "."
                        + className.substring(0, className.lastIndexOf('.'))
            )
        } catch (e: ClassNotFoundException) {
            // handle the exception
        }
        return null
    }
}