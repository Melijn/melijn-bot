package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import me.melijn.apkordex.command.KordExtension

@KordExtension
class OsuExtension : Extension() {

    override val name: String = "osu"

    override suspend fun setup() {
        publicSlashCommand {
            name = "osu"
            description = "osu statistic viewing commands"

            publicSubCommand {
                name = "linkaccount"
                description = "Links your osu username to your discord id for user inference when using osu commands"

                action {
                    respond {

                    }
                }
            }
        }
    }

    inner class OsuAccountArg : Arguments() {
        val account by string {
            name = "account"
            description = "an existing osu account"
            validate {
                value
            }
        }
    }
}