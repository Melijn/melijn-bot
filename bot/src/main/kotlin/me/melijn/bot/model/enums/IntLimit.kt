package me.melijn.bot.model.enums

open class IntLimit(val limit: Int?, val type: Type = Type.VALUE) {
    object Inherit : IntLimit(null, Type.INHERIT)
    object NoLimit : IntLimit(null, Type.NO_LIMIT)

    enum class Type {
        VALUE,
        INHERIT,
        NO_LIMIT
    }
}


