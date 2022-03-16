package me.melijn.bot

import me.melijn.bot.model.Environment
import me.melijn.kordkommons.environment.BotSettings

object Settings {

    class Bot : BotSettings("bot") {
        val prefix by string("prefix", ">")
        val ownerIds by string("ownerIds", "")
        val id by string("id", "")
    }

    class Process : BotSettings("process") {
        val environment by enum<Environment>("environment", "")
        val shardCount by int("shardCount", 1)
        val podCount by int("podCount", 1)
        val hostPattern by string(
            "hostPattern",
            "http://localhost:8181"
        ) // This pattern will be used to extract te podId
        val testingServerId by long("testingServerId", 234277444708859904L)
    }

    class Database : BotSettings("db") {
        /** postgres://user:pass@host:port/name **/
        val host by string("host", "localhost")
        val port by int("port", 5432)
        val user by string("user", "postgres")
        val pass by string("pass")
        val name by string("name", "melijn-bot") // db name
    }

    class Redis : BotSettings("redis") {
        val enabled by boolean("enabled", true)
        val host by string("host", "localhost")
        val port by int("port", 6379)
        val user by stringN("user")
        val pass by string("pass")
    }

    class Api : BotSettings("api") {
        class Discord : BotSettings("api_discord") {
            val token by string("token")
            val gateway by string("gateway", "")
        }

        class Sentry : BotSettings("sentry") {
            val url by stringN("url")
        }

        class Spotify : BotSettings("api_spotify") {
            val clientId by string("clientId")
            val password by string("password")
        }

        class ImgHoard : BotSettings("api_imghoard") {
            val token by string("token")
        }

        class TheCatApi: BotSettings("api_thecatapi") {
            val apiKey by string("apikey")
        }

        val discord = Discord()
        val sentry = Sentry()
        val spotify = Spotify()
        val imgHoard = ImgHoard()
        val theCatApi = TheCatApi()
    }

    /**
     * When enabled, melijn will proxy requests through the proxy when contacting user-input urls.
     * Discord and trusted 3th parties will not be proxied by this.
     * **/
    class HttpProxy : BotSettings("proxy") {
        val enabled by boolean("enabled", true)
        val host by string("host", "1.1.1.1")
        val port by int("port", 443)
    }

    val bot = Bot()
    val process = Process()
    val database = Database()
    val redis = Redis()
    val api = Api()
    val httpProxy = HttpProxy()
}