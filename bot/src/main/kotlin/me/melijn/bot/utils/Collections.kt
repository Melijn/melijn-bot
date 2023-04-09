package me.melijn.bot.utils

fun <K, V> Iterable<K>.keysToMap(value: (K) -> V): Map<K, V> {
    return associateBy({ it }, value)
}