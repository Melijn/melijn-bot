package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.extensions.Extension

class TestCommand : Extension() {

    override val name: String = "test"

    override suspend fun setup() {
//        publicSlashCommand {
//            name = "greet"
//            description = "Get a greeting!"
//
//            group("type") {
//                description = "Get a greeting of a specific type."
//
//                publicSlashCommand {
//                    name = "hello"
//                    description = "Hello!"
//
//                    action {
//                        // ...
//                    }
//                }
//
//                publicSlashCommand {
//                    name = "welcome"
//                    description = "Welcome!"
//
//                    action {
//                        // ...
//                    }
//                }
//            }
//        }
    }
}