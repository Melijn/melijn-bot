package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.requestMembers
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import me.melijn.bot.database.manager.GuildSettingsManager
import org.koin.core.component.inject

class SpotifyCommand : Extension() {

    override val name: String = "spotify"
    val guildSettingsManager: GuildSettingsManager by inject()

    inner class SpotifyArguments : Arguments() {
        val target by user {
            name = "user"
            description = "Person you'd like to view"
        }
    }

    @OptIn(PrivilegedIntent::class)
    override suspend fun setup() {
        publicSlashCommand(::SpotifyArguments) {
            val cmd = this
            name = "spotify"
            description = "spot"
            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
                anyGuild()
            }

            action {
                val target = this.arguments.target
                val userId = this.arguments.target.id
                val fullMember = this.guild?.requestMembers {
                    this.userIds.add(userId)
                    presences = true
                }?.firstOrNull()
                val sex = fullMember?.data?.presences?.value?.mapNotNull { presence ->
                    presence.activities.firstOrNull { it.name == "Spotify" }
                } ?: emptyList()
                val fullSex = sex.firstOrNull()
                if (fullSex == null) {
                    this.respond {
                        this.embed {
                            this.title = "no more s[ptofu"
                        }
                    }
                } else {
                    this.respond {
                        this.embed {
                            this.title = target.username + " listening to spotify"
                            this.thumbnail {
                                url = "https://i.scdn.co/image/" + fullSex.assets.value?.largeImage?.value?.drop(8)
                            }
                            this.color = Color(30, 215, 96)
                            this.description = """
                                **${fullSex.details.value}**
                                by ${fullSex.state.value}
                                on ${fullSex.assets.value?.largeText?.value}
                            """.trimIndent()
                        }
                    }
                }
            }
        }
    }
}