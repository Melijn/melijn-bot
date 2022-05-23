package me.melijn.bot.utils

import org.springframework.boot.ansi.AnsiColor

object StringsUtil {
    fun ansiFormat(color: AnsiColor) = me.melijn.kordkommons.utils.ansiFormat(color.toString())
}