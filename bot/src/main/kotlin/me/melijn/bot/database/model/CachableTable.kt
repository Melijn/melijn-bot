package me.melijn.bot.database.model

interface CacheableTable {
    fun toData(): CacheableData
}