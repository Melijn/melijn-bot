package me.melijn.bot

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import com.kotlindiscord.kord.extensions.koin.KordExContext
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.DefaultRateLimiter
import com.kotlindiscord.kord.extensions.utils.getKoin
import com.kotlindiscord.kord.extensions.utils.scheduling.TaskConfig
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException
import dev.minn.jda.ktx.jdabuilder.injectKTX
import dev.schlaubi.lavakord.LavaKord
import dev.schlaubi.lavakord.jda.LavaKordShardManager
import dev.schlaubi.lavakord.jda.applyLavakord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import me.melijn.ap.injector.InjectorInterface
import me.melijn.apkordex.command.ExtensionInterface
import me.melijn.apredgres.createtable.CreateTableInterface
import me.melijn.bot.database.manager.PrefixManager
import me.melijn.bot.model.Environment
import me.melijn.bot.model.PodInfo
import me.melijn.bot.model.kordex.MelijnCooldownHandler
import me.melijn.bot.utils.loadLavaLink
import me.melijn.bot.web.server.HttpServer
import me.melijn.gen.Settings
import me.melijn.gen.uselimits.CommandLimitModule
import me.melijn.kordkommons.database.ConfigUtil
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.logger.logger
import me.melijn.kordkommons.redis.RedisConfig
import me.melijn.kordkommons.utils.ReflectUtil
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.koin.dsl.bind
import org.koin.dsl.module
import java.net.InetAddress
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/** Application entry point */
suspend fun main() {
    Melijn.susInit()
}

object Melijn {

    private val logger = logger()
    lateinit var lavalink: LavaKord

    suspend fun susInit() {
        logger.info("Starting Melijn..")
        val settings = Settings
        initGlobalSettings(settings)

        ExtensibleBot(settings.api.discord.token) {
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

            kord {
                setShardsTotal(PodInfo.shardCount)
                setShards(PodInfo.shardList)
                setEventPool(Executors.newVirtualThreadPerTaskExecutor())
                setCallbackPool(Executors.newVirtualThreadPerTaskExecutor())
                enableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY, CacheFlag.EMOJI)
                setMemberCachePolicy(MemberCachePolicy.lru(1000).unloadUnless(MemberCachePolicy.VOICE))
                injectKTX()
                applyLavakord(lShardManager)
            }

            presence({OnlineStatus.ONLINE}, { shardId -> Activity.customStatus("This is shard $shardId")})

            hooks {
                beforeKoinSetup {
                    loadMelijnStartupKoinModules(settings)

                    // HttpServer probeServer requires our koin context to be loaded
                    HttpServer.startProbeServer()
                }

                setup {
                    // start the extensible bot, includes shardManager login
                    this.start()

                    logger.info { "Discord loging success, starting runtime services..." }

                    // HttpServer bot api
                    HttpServer.startHttpServer()

                    loadMelijnRuntimeKoinModules()
                    loadLavaLink(lShardManager)
                }
            }

            registerCommands(settings)

            i18n {
                interactionUserLocaleResolver()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initGlobalSettings(settings: Settings) {
        val podCount = settings.process.podCount
        val shardCount = settings.process.shardCount
        val podId = fetchPodIdFromHostname(
            podCount,
            settings.process.environment == Environment.PRODUCTION
        )
        PodInfo.init(podCount, shardCount, podId)

        ImageIO.setUseCache(false)

        if (settings.process.environment == Environment.TESTING) {
            // Enable coroutine names, they are visible when dumping the coroutines
            System.setProperty("kotlinx.coroutines.debug", "on")

            // Enable coroutines stacktrace recovery
            System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true")

            // It is recommended to set this to false to avoid performance hits with the DebugProbes option!
            DebugProbes.enableCreationStackTraces = false
            DebugProbes.install()
        }
    }

    private fun ExtensibleBot.loadMelijnRuntimeKoinModules() {
        val injectorInterface = ReflectUtil.getInstanceOfKspClass<InjectorInterface>(
            "me.melijn.gen", "InjectionKoinModule"
        )

        getKoin().loadModules(listOf(injectorInterface.module))
        injectorInterface.initInjects()
    }

    private fun loadMelijnStartupKoinModules(settings: Settings) {
        val driverManager = initDriverManager(settings)

        KordExContext.get().loadModules(listOf(module {
            single { settings } bind Settings::class
            single { driverManager } bind DriverManager::class
        }, CommandLimitModule.getModule()))
    }

    private suspend fun ExtensibleBotBuilder.registerCommands(settings: Settings) {
        extensions {
            helpExtensionBuilder.enableBundledExtension = false

            val extensionModule = ReflectUtil.getInstanceOfKspClass<ExtensionInterface>(
                "me.melijn.gen", "ExtensionAdderModule"
            )
            val list = extensionModule.list
            for (ex in list) add { ex }
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

        val driverManager = try {
            DriverManager(hikariConfig, redisConfig) { createTableInterface.createTables() }
        } catch (e: PoolInitializationException) {
            logger.error(e) { "Melijn cannot start without a database connection" }
            exitProcess(5)
        }

        return driverManager
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
}