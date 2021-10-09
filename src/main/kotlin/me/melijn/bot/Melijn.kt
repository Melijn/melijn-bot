package me.melijn.bot

import com.kotlindiscord.kord.extensions.ExtensibleBot
import me.melijn.bot.commands.HelpCommand
import me.melijn.bot.model.Environment
import me.melijn.kordkommons.logger.logger

object Melijn {
    val logger = logger()

    suspend fun susInit() {
        logger.info("Starting Melijn..")
        val settings = Settings

        val botInstance = ExtensibleBot(settings.api.discord.token) {
            extensions {
                helpExtensionBuilder.enableBundledExtension = false
                add {
                    HelpCommand()
                }
            }
            this.chatCommands {
                enabled = true
                prefix { settings.bot.prefix }
            }
            this.applicationCommands {
                enabled = true

                if (settings.process.environment == Environment.Testing)
                    defaultGuild(234277444708859904L)
            }
        }
        botInstance.start()
    }
}

suspend fun main() {
    Melijn.susInit()
}