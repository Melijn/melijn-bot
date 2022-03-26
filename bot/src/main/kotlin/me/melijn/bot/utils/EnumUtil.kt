package me.melijn.bot.utils

object EnumUtil {

    fun Enum<*>.lcc(): String {
        return this.toString()
            .lowercase()
            .replace("_(.)".toRegex()) { match ->
                match.groups[1]!!.value.uppercase()
            }
    }

}