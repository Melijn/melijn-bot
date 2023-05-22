package me.melijn.bot.utils

object ExceptionUtil {

    fun unreachable(): Nothing {
        throw RuntimeException("Unreachable line has been reached ! self destruct !")
    }
}