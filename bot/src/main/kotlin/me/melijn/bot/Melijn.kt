package me.melijn.bot

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.loadModule
import dev.kord.core.Kord
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.schlaubi.lavakord.LavaKord
import dev.schlaubi.lavakord.kord.lavakord
import io.sentry.Sentry
import me.melijn.ap.createtable.CreateTableInterface
import me.melijn.ap.injector.InjectorInterface
import me.melijn.apkordex.command.ExtensionInterface
import me.melijn.bot.database.manager.PrefixManager
import me.melijn.bot.model.Environment
import me.melijn.bot.model.PodInfo
import me.melijn.bot.services.ServiceManager
import me.melijn.bot.utils.EnumUtil.lcc
import me.melijn.bot.web.server.RestServer
import me.melijn.gen.Settings
import me.melijn.kordkommons.database.ConfigUtil
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.logger.logger
import me.melijn.kordkommons.redis.RedisConfig
import me.melijn.kordkommons.utils.ReflectUtil
import org.koin.core.context.GlobalContext.loadKoinModules
import org.koin.dsl.bind
import org.koin.java.KoinJavaComponent.inject
import java.net.InetAddress
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

object Melijn {

    private val logger = logger()
    lateinit var lavalink: LavaKord

    suspend fun susInit() {
        logger.info("Starting Melijn..")
        val settings = Settings
        val podCount = settings.process.podCount
        val shardCount = settings.process.shardCount
        val podId = fetchPodIdFromHostname(
            podCount,
            settings.process.environment == Environment.PRODUCTION
        )
        PodInfo.init(podCount, shardCount, podId)
        //initSentry(settings)

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

                val sexy = ReflectUtil.getInstanceOfKspClass<ExtensionInterface>(
                    "me.melijn.gen", "ExtensionAdderModule"
                )
                val list = sexy.list
                for (ex in list) add { ex }
            }

            hooks {
                beforeKoinSetup {
                    val objectMapper: ObjectMapper = jacksonObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    val driverManager = initDriverManager(settings)

                    loadModule {
                        single { settings } bind Settings::class
                        single { objectMapper } bind ObjectMapper::class
                        single { driverManager } bind DriverManager::class
                    }

                    val injectorInterface = ReflectUtil.getInstanceOfKspClass<InjectorInterface>(
                        "me.melijn.gen", "InjectionKoinModule"
                    )
                    loadKoinModules(injectorInterface.module)

                    RestServer // Inits restServer object

                    val serviceManager by inject<ServiceManager>(ServiceManager::class.java)
                    serviceManager.startAll()
                }

                setup {
                    val kord by inject<Kord>(Kord::class.java)
                    lavalink = kord.lavakord {
                        link {
                            autoReconnect = true
                            retry = linear(1.seconds, 60.seconds, Int.MAX_VALUE)
                        }
                    }
                    lavalink.addNode(Settings.lavalink.url, Settings.lavalink.password, "node1")
                }
            }


            cache {
                cachedMessages = 0
            }

            applicationCommands {
                enabled = true

                if (settings.process.environment == Environment.TESTING)
                    defaultGuild(settings.process.testingServerId.toULong())
            }
            chatCommands {
                enabled = true
                prefix callback@{ _ ->
                    val event = this
                    val prefixManager by inject<PrefixManager>(PrefixManager::class.java)
                    val prefixes = (event.guildId?.let { prefixManager.getPrefixes(it) } ?: emptyList()) +
                        (event.message.author?.let { prefixManager.getPrefixes(it.id) } ?: emptyList())
                    prefixes.sortedByDescending { it.prefix.length }.forEach {
                        if (message.content.startsWith(it.prefix)) return@callback it.prefix
                    }
                    return@callback settings.bot.prefix
                }
            }
        }
        botInstance.start()
    }

    private fun initDriverManager(settings: Settings): DriverManager {
        val redisConfig = settings.redis.run { RedisConfig(enabled, host, port, user, pass) }
        val hikariConfig = settings.database.run {
            ConfigUtil.generateDefaultHikariConfig(host, port, name, user, pass)
        }

        val createTableInterface = ReflectUtil.getInstanceOfKspClass<CreateTableInterface>(
            "me.melijn.gen", "CreateTablesModule"
        )
        return DriverManager(hikariConfig, redisConfig) { createTableInterface.createTables() }
    }

    private fun fetchPodIdFromHostname(podCount: Int, dynamic: Boolean): Int {
        return try {
            var hostName = "localhost"
            if (dynamic) hostName = InetAddress.getLocalHost().hostName
            logger.info("[hostName] {}", hostName)

            if (podCount == 1) 0
            else hostName.split("-").last().toInt()
        } catch (t: Throwable) {
            logger.warn("Cannot parse podId from hostname", t)
            if (podCount == 1) 0
            else {
                Thread.sleep(1000)
                exitProcess(404)
            }
        }
    }

    private fun initSentry(settings: Settings) {
        Sentry.init { options ->
            options.dsn = settings.sentry.url
            options.environment = settings.process.environment.lcc()
            options.release = settings.bot.version
            // Set traces_sample_rate to 1.0 to capture 100% of transactions for performance monitoring.
            // We recommend adjusting this value in production.
            options.tracesSampleRate = 0.1
            // When first trying Sentry it's good to see what the SDK is doing:
            // options.debug = true
        }
    }
}

suspend fun main() {
    Melijn.susInit()
}