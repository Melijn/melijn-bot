package me.melijn.bot.utils

import io.github.furstenheim.CodeBlockStyle
import io.github.furstenheim.CopyDown
import io.github.furstenheim.OptionsBuilder
import net.dv8tion.jda.api.entities.Member
import org.springframework.boot.ansi.AnsiColor
import java.lang.Character.UnicodeBlock
import java.text.Normalizer
import javax.swing.text.NumberFormatter
import kotlin.random.Random

object StringsUtil {

    fun ansiFormat(color: AnsiColor) = me.melijn.kordkommons.utils.ansiFormat(color.toString())

    fun htmlToMarkdown(html: String): String {
        val htmlConverter: CopyDown = OptionsBuilder.anOptions().run {
            withCodeBlockStyle(CodeBlockStyle.FENCED)
            withBulletListMaker("• ")
        }.let { CopyDown(it.build()) }

        return htmlConverter.convert(html)
    }

    /** Prepends [this] with '0' until [this.size] >= [targetLength] **/
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

    /**
     * Gets the best possible normalized name for [member].
     * @return nick > displayName > username or falls back to: Nr$x \in 0..guild.memberCount$
     */
    fun getNormalizedUsername(member: Member): String {
        if (filterGarbage(member.effectiveName) == member.effectiveName) return member.effectiveName

        val username = member.user.name
        val displayName = member.user.globalName
        val nick = member.nickname

        fun improveNameOrNull(nick: String?): String? = nick?.let {
            filterGarbage(it)
        }?.takeIf {
            it.isNotBlank() && it != username
        }

        return improveNameOrNull(nick)
            ?: improveNameOrNull(displayName)
            ?: improveNameOrNull(username)
            ?: "Nr${Random.nextInt(member.guild.memberCount)}"
    }

    private val allowedBlocks: Set<UnicodeBlock> = setOf(
        UnicodeBlock.THAI
    )

    fun filterGarbage(s: String): String = normalize(s).toCharArray().filter {
        Character.isWhitespace(it) || Character.isLetterOrDigit(it) || allowedBlocks.contains(UnicodeBlock.of(it))
    }.joinToString(separator = "") {
        if (it.isTitleCase()) it.lowercase() else it.toString()
    }.trim()

    fun Long.format(formatter: NumberFormatter): String = formatter.valueToString(this)
}