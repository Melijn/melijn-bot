package me.melijn.bot.utils

import io.ktor.client.request.*

object KtorUtils {

    fun HttpRequestBuilder.parametersOf(vararg params: Pair<String, String>) {
        params.forEach { (key, value) ->
            this.parameter(key, value)
        }
    }
}