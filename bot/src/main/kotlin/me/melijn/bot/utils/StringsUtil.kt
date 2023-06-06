package me.melijn.bot.utils

import io.github.furstenheim.CodeBlockStyle
import io.github.furstenheim.CopyDown
import io.github.furstenheim.OptionsBuilder
import net.dv8tion.jda.api.entities.Member
import org.springframework.boot.ansi.AnsiColor
import java.text.Normalizer
import kotlin.random.Random

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

    fun <T> Iterable<T>.batchingJoinToString(
        batchLimit: Int,
        separator: CharSequence = ", ",
        transform: ((T) -> String)
    ): List<String> {
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

    private fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

    fun getNormalizedUsername(member: Member): String {
        val username = member.user.name
        val displayName = member.user.globalName
        val nick = member.nickname
        return nick?.let { filterGarbage(it) }?.takeIf { it.isNotBlank() } ?: displayName?.let { filterGarbage(it) }
            ?.takeIf { it.isNotBlank() } ?: filterGarbage(username).takeIf { it.isNotBlank() } ?: "Nr${
            Random.nextInt(member.guild.memberCount)
        }"
    }

    fun filterGarbage(s: String) = normalize(s).toCharArray().filter {
        Character.isWhitespace(it) || Character.isLetterOrDigit(it)
    }.joinToString(separator = "") {
        if (it.isTitleCase()) it.lowercase() else it.toString()
    }.trim()
}