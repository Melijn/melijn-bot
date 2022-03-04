package me.melijn.bot.database.model

/**
 * use this on exposed entities so our database model can store it in redis for us.
 */
annotation class Cacheable<T>
