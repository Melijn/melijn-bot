package me.melijn.bot.utils

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinDurationColumnType
import kotlin.time.Duration

object ExposedUtil {
    class CustomExpression<T>(private val content: String) : Expression<T>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            append(content)
        }
    }

    inline fun <reified T> retype(expression: Expression<*>) = object : Expression<T>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = expression.toQueryBuilder(queryBuilder)
    }

    fun intervalEpoch(interval: Expression<*>) = object : Function<Duration?>(KotlinDurationColumnType()) {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            append("extract(epoch from ", interval, ") * 1e+9")
        }
    }
}