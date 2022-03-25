package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand

class DevExtension : Extension() {
    override val name: String= "dev"

    override suspend fun setup() {
        publicSlashCommand {
            name = "test"
            description = "testing"
            action {

            }
        }
    }
}