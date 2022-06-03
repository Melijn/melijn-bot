package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.XPManager
import org.koin.core.component.inject

@KordExtension
class LevelingExtension : Extension() {

    override val name: String = "leveling"
    val xpManager by inject<XPManager>()

    override suspend fun setup() {
        publicSlashCommand {
            name = "xp"
            description = "xp"

            action {
                val xp = xpManager.getGlobalXP(user.id)
                respond {
                    content = "your xp: $xp"
                }
            }
        }
        publicSlashCommand(::SetXPArgs) {
            name = "setxp"
            description = "gives xp"

            action {
                val xp = arguments.xp.parsed
                val user = arguments.user.parsed

                xpManager.setGlobalXP(user.id, xp.toULong())

                respond {
                    content = "${user.tag} xp: $xp"
                }
            }
        }

    }
    inner class SetXPArgs : Arguments() {
        val user = user {
            name = "user"
            description = "user"
        }
        val xp = long {
            name = "xp"
            description = "Sets xp lol"
        }
    }
}