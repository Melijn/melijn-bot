package config

import me.melijn.ap.settings.SettingsTemplate
import me.melijn.kordkommons.environment.BotSettings

@SettingsTemplate("")
private class Template {

    class Bot : BotSettings("bot") {
        val id by string("id", "")
    }

    class Backend : BotSettings("backend") {
        val host by string(
            "hostPattern", "http://localhost:8181"
        )
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

    class Service : BotSettings("service") {
        val port by int("port")
        val jwtKey by string("jwtkey")
    }

    class DiscordOauth : BotSettings("discordOauth") {
        val botId by string("botId")
        val botSecret by string("botSecret")
        val redirectUrl by string("redirectUrl")
        val discordApiHost by string("discordApiHost")
    }
}