package me.melijn.bot.utils

object EnumUtil {

    fun Enum<*>.lcc(): String {
        return this.toString()
            .lowercase()
            .replace("_(.)".toRegex()) { match ->
                match.groups[1]!!.value.uppercase()
            }
    }

    fun Enum<*>.ucc(): String {
        return this
            .toString()
            .ucc()
    }

    fun String.ucc(): String {
        return this
            .lowercase()
            .replace("_(.)".toRegex()) { match ->
                match.groups[1]!!.value.uppercase()
            }.replaceFirstChar { char ->
                char.uppercase()
            }
    }
}