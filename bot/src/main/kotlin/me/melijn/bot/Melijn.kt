package me.melijn.bot

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.loadModule
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import me.melijn.bot.commands.HelpCommand
import me.melijn.bot.commands.SettingsCommand
import me.melijn.bot.commands.SpotifyCommand
import me.melijn.bot.model.Environment
import me.melijn.bot.services.ServiceManager
import me.melijn.bot.utils.ReflectUtil
import me.melijn.kordkommons.logger.logger
import org.koin.core.context.GlobalContext.loadKoinModules
import org.koin.dsl.bind

object Melijn {

    val logger = logger()

    suspend fun susInit() {
        logger.info("Starting Melijn..")
        val settings = Settings

        val botInstance = ExtensibleBot(settings.api.discord.token) {
            @OptIn(PrivilegedIntent::class)
            intents {
                +Intent.DirectMessages
                +Intent.Guilds
                +Intent.GuildMembers
                +Intent.GuildMessages
                +Intent.GuildBans
                +Intent.GuildEmojis
                +Intent.GuildMessageReactions
                +Intent.GuildPresences
            }

            extensions {
                helpExtensionBuilder.enableBundledExtension = false
                add {
                    HelpCommand()
                    SettingsCommand()
                    SpotifyCommand()
//                    TestCommand()
                }
            }

            hooks {
                beforeKoinSetup {
                    val objectMapper: ObjectMapper = jacksonObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    val serviceManager = ServiceManager()

                    loadModule {
                        single { settings } bind Settings::class
                        single { objectMapper } bind ObjectMapper::class
                        single { serviceManager } bind ServiceManager::class
                    }

                    val sexy = ReflectUtil.findAllClassesUsingClassLoader("me.melijn.gen")
                        .filterNotNull()
                        .filter { it.toString().contains("InjectionKoinModule", true) && !it.toString().contains("$") }
                        .maxByOrNull { it.name.replace(".*InjectionKoinModule(\\d+)".toRegex()) { res -> res.groups[1]?.value ?: "" }.toInt() }
                        ?.getConstructor() ?: return@beforeKoinSetup

                    loadKoinModules((sexy.newInstance() as InjectorInterface).module)
                }
            }

            this.applicationCommands {
                enabled = true

                if (settings.process.environment == Environment.Testing)
                    defaultGuild(settings.process.testingServerId.toULong())
            }
        }
        botInstance.start()
    }
}

suspend fun main() {
    Melijn.susInit()
}