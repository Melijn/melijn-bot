package me.melijn.bot.utils

import io.github.furstenheim.CodeBlockStyle
import io.github.furstenheim.CopyDown
import io.github.furstenheim.OptionsBuilder
import org.springframework.boot.ansi.AnsiColor

object StringsUtil {
    private val htmlConverter: CopyDown = OptionsBuilder.anOptions().run {
        withCodeBlockStyle(CodeBlockStyle.FENCED)
        withBulletListMaker("• ")
    }.let { CopyDown(it.build()) }

    fun ansiFormat(color: AnsiColor) = me.melijn.kordkommons.utils.ansiFormat(color.toString())

    fun htmlToMarkdown(html: String): String = htmlConverter.convert(html)

    fun String.prependZeros(targetLength: Int): String {
        val zeros = kotlin.math.max(targetLength - this.length, 0)
        val extraZeros = "0".repeat(zeros)
        return extraZeros + this
    }

    fun <T> Iterable<T>.batchingJoinToString(batchLimit: Int, separator: CharSequence = ", ", transform: ((T) -> String)): List<String> {
        val bins = mutableListOf<String>()
        val builder = StringBuilder()
        for (element in this) {
            val s = transform(element) + separator
            if (builder.length + s.length > batchLimit) {
                bins += builder.toString()
                builder.clear()
            }
            builder.append(s)
        }
        bins += builder.toString()

        return bins
    }
}