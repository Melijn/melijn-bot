package me.melijn.bot

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.koin.KordExContext
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.DefaultRateLimiter
import com.kotlindiscord.kord.extensions.utils.getKoin
import com.kotlindiscord.kord.extensions.utils.scheduling.TaskConfig
import dev.minn.jda.ktx.jdabuilder.injectKTX
import dev.schlaubi.lavakord.LavaKord
import dev.schlaubi.lavakord.jda.LavaKordShardManager
import dev.schlaubi.lavakord.jda.applyLavakord
import dev.schlaubi.lavakord.jda.lavakord
import io.sentry.Sentry
import me.melijn.ap.injector.InjectorInterface
import me.melijn.apkordex.command.ExtensionInterface
import me.melijn.apredgres.createtable.CreateTableInterface
import me.melijn.bot.database.manager.PrefixManager
import me.melijn.bot.model.Environment
import me.melijn.bot.model.PodInfo
import me.melijn.bot.model.kordex.MelijnCooldownHandler
import me.melijn.bot.music.MusicManager
import me.melijn.bot.services.ServiceManager
import me.melijn.bot.utils.EnumUtil.lcc
import me.melijn.bot.utils.RealLinearRetry
import me.melijn.bot.web.server.HttpServer
import me.melijn.gen.Settings
import me.melijn.gen.uselimits.CommandLimitModule
import me.melijn.kordkommons.database.ConfigUtil
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.logger.logger
import me.melijn.kordkommons.redis.RedisConfig
import me.melijn.kordkommons.utils.ReflectUtil
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.koin.dsl.bind
import org.koin.dsl.module
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
        // initSentry(settings)

        val botInstance = ExtensibleBot(settings.api.discord.token) {
            val lShardManager = LavaKordShardManager()
            intents {
                add(GatewayIntent.DIRECT_MESSAGES)
                add(GatewayIntent.GUILD_MEMBERS)
                add(GatewayIntent.GUILD_MESSAGES)
                add(GatewayIntent.GUILD_MODERATION)
                add(GatewayIntent.GUILD_EMOJIS_AND_STICKERS)
                add(GatewayIntent.GUILD_MESSAGE_REACTIONS)
                add(GatewayIntent.GUILD_PRESENCES)
                add(GatewayIntent.GUILD_VOICE_STATES)
            }

            this.kord {
                setShardsTotal(PodInfo.shardCount)
                setShards(PodInfo.shardList)
                enableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY, CacheFlag.EMOJI)
                setMemberCachePolicy(MemberCachePolicy.DEFAULT)
                injectKTX()
                applyLavakord(lShardManager)
            }

            extensions {
                helpExtensionBuilder.enableBundledExtension = false

                val extensionModule = ReflectUtil.getInstanceOfKspClass<ExtensionInterface>(
                    "me.melijn.gen", "ExtensionAdderModule"
                )
                val list = extensionModule.list
                for (ex in list) add { ex }
            }

            hooks {
                beforeKoinSetup {
                    val objectMapper: ObjectMapper = jacksonObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    val driverManager = initDriverManager(settings)
                    KordExContext.get().loadModules(listOf(module {
                        single { settings } bind Settings::class
                        single { objectMapper } bind ObjectMapper::class
                        single { driverManager } bind DriverManager::class
                    }, CommandLimitModule.getModule()))

                    HttpServer.startProbeServer()
                }

                setup {
                    this.start()

                    val koin = getKoin()
                    HttpServer.startHttpServer()
                    val injectorInterface = ReflectUtil.getInstanceOfKspClass<InjectorInterface>(
                        "me.melijn.gen", "InjectionKoinModule"
                    )
                    koin.loadModules(listOf(injectorInterface.module))
                    injectorInterface.initInjects()

                    val serviceManager by koin.inject<ServiceManager>()
                    serviceManager.startAll()

                    val kord by koin.inject<ShardManager>()
                    lavalink = kord.lavakord(lShardManager, TaskConfig.dispatcher) {
                        link {
                            autoReconnect = true
                            retry = RealLinearRetry(1.seconds, 60.seconds, Int.MAX_VALUE)
                        }
                    }
                    for (i in 0 until Settings.lavalink.url.size)
                        lavalink.addNode(Settings.lavalink.url[i], Settings.lavalink.password[i], "node$i")
                    for (node in lavalink.nodes) {
                        MusicManager.setupReconnects(node)
                    }
                }
            }

            val defaultRateLimiter = DefaultRateLimiter()
            val defaultCooldownHandler = MelijnCooldownHandler()
            applicationCommands {
                enabled = true

                if (settings.process.environment == Environment.TESTING)
                    defaultGuild(settings.process.testingServerId)

                useLimiter {
                    cooldownHandler = defaultCooldownHandler
                    rateLimiter = defaultRateLimiter
                }
            }

            chatCommands {
                enabled = true
                prefix callback@{ _ ->
                    val event = this
                    val prefixManager by getKoin().inject<PrefixManager>()
                    val prefixes = prefixManager.getPrefixes(event.guild) +
                            prefixManager.getPrefixes(event.message.author)
                    prefixes.sortedByDescending { it.prefix.length }.forEach {
                        if (message.contentRaw.startsWith(it.prefix)) return@callback it.prefix
                    }
                    return@callback settings.bot.prefix
                }
                useLimiter {
                    cooldownHandler = defaultCooldownHandler
                    rateLimiter = defaultRateLimiter
                }
            }

            i18n {
                interactionUserLocaleResolver()
            }
        }
    }

    private fun initDriverManager(settings: Settings): DriverManager {
        logger.info { "Initializing driverManager" }
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