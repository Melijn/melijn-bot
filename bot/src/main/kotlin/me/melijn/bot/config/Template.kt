package me.melijn.bot.config

import me.melijn.ap.settings.SettingsTemplate
import me.melijn.bot.model.Environment
import me.melijn.kordkommons.environment.BotSettings

@SettingsTemplate("import me.melijn.bot.model.Environment")
private class Template {

    class Bot : BotSettings("bot") {
        val prefix by string("prefix", ">")
        val username by string("prefix", "Melijn")
        val discriminator by string("discrim", "0001")
        val ownerIds by string("ownerIds", "")
        val id by long("id")
        val version by string("version")
    }

    class Process : BotSettings("process") {
        val environment by enum<Environment>("environment", "")
        val shardCount by int("shardCount", 1)
        val podCount by int("podCount", 1)
        val hostPattern by string(
            "hostPattern", "http://localhost:8181"
        ) // This pattern will be used to extract te podId
        val testingServerId by longN("testingServerId")
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

        class Discord : BotSettings("discord") {
            val token by string("token")
            val gateway by string("gateway", "")
        }

        class Sentry : BotSettings("sentry") {
            val url by stringN("url")
        }

        class Spotify : BotSettings("spotify") {
            val clientId by string("clientId")
            val password by string("password")
        }

        class ImgHoard : BotSettings("imghoard") {
            val token by string("token")
        }

        class Osu : BotSettings("osu") {
            val clientId by int("clientId")
            val secret by string("secret")
        }
    }

    /**
     * When enabled, melijn will proxy requests through the proxy when contacting user-input urls.
     * Discord and trusted 3th parties will not be proxied by this.
     * **/
    class HttpProxy : BotSettings("proxy") {
        val enabled by boolean("enabled", false)
        val host by string("host", "1.1.1.1")
        val port by int("port", 443)
    }

    class HttpServer : BotSettings("httpserver") {
        val enabled by boolean("enabled", true)
        val port by int("port", 8181)
        val runningLimit by int("runningLimit", 3)
        val requestQueueLimit by int("requestQueueLimit", 3)
    }

    // K8s probeserver
    class ProbeServer : BotSettings("probeserver") {
        val enabled by boolean("enabled", true)
        val port by int("port", 1180)
        val runningLimit by int("runningLimit", 3)
        val requestQueueLimit by int("requestQueueLimit", 3)
    }

    class Sentry : BotSettings("sentry") {
        val url by string("url")
    }

    class Lavalink : BotSettings("lavalink") {
        val url by stringList("url")
        val password by stringList("password")
    }
}