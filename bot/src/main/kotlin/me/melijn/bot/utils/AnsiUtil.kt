package me.melijn.bot.utils

object AnsiUtil {

    private const val ESC = '\u001b'

    val String.ansiBlack: String
        get() = "$ESC[0;30m$this"
    val String.ansiRed: String
        get() = "$ESC[0;34m$this"

    const val ansiResetColour = "$ESC[0;39m"

}