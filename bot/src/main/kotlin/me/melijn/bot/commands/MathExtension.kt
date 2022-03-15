package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand

class MathExtension : Extension() {
    override val name: String = "math"

    override suspend fun setup() {
        chatCommand(::TwoNumberArgs) {
            name = "gcd"

            action {
                val gcd = gcd(this.arguments.a.parsed, this.arguments.b.parsed)
                this.channel.createMessage("The greatest common denominator is: **${gcd}**")
            }
        }

        chatCommand(::TwoNumberArgs) {
            name = "scm"

            action {
                val scm = scm(this.arguments.a.parsed, this.arguments.b.parsed)
                this.channel.createMessage("The smallest common multiple is: **${scm}**")
            }
        }
    }

    /** finds the greatest common denominator **/
    private fun gcd(a: Long, b: Long): Long {
        var a2 = a
        var b2 = b
        var rest = a2 % b2
        while (rest != 0L) {
            a2 = b2
            b2 = rest
            rest = a2 % b2
        }
        return b2
    }

    /** finds smallest common multiple **/
    private fun scm(a: Long, b: Long): Long {
        return a / gcd(a, b) * b
    }

    inner class TwoNumberArgs: Arguments() {
        val a = long {
            name = "a"
            description = "number 1"
        }
        val b = long {
            name = "b"
            description = "number 2"
        }
    }
}