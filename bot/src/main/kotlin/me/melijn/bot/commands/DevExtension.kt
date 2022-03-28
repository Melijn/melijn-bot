package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import me.melijn.apkordex.command.KordExtension

@KordExtension
class DevExtension : Extension() {

    override val name: String = "dev"

    override suspend fun setup() {
        publicSlashCommand {
            name = "test"
            description = "test"
            action {
                respond {
                    content = ":flushed:"
                }
            }
        }
    }
}