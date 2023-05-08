package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.types.CheckContextWithCache
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.database.manager.VoiceManager
import me.melijn.bot.utils.JDAUtil.awaitOrNull
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TimeUtil.formatElapsedVerbose
import me.melijn.bot.utils.embedWithColor
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.koin.core.component.inject
import kotlin.time.Duration

@KordExtension
class VoiceActivityCommand : Extension() {

    override val name: String = "voice"
    private val voiceManager by inject<VoiceManager>()
    private val guildSettingsManager by KoinUtil.inject<GuildSettingsManager>()

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "voice"
            description = "View voice statistics"

            publicSubCommand {
                name = "self"
                description = "View own voice statistics"

                check {
                    failIfNoVoiceTracking()
                }

                action {
                    val duration =
                        voiceManager.getPersonalVoiceStatistics(this.guild!!.idLong, this.user.idLong)

                    respond {
                        embedWithColor {
                            description = if (duration == null)
                                tr("voiceactivity.personal.timespent.none")
                            else
                                tr("voiceactivity.personal.timespent", duration.formatElapsedVerbose())
                        }
                    }
                }
            }

            publicSubCommand {
                name = "leaderboard"
                description = "View this guild's voice leaderboard"

                check {
                    failIfNoVoiceTracking()
                }

                action {
                    val guild = this.guild!!
                    val duration =
                        voiceManager.getGuildStatistics(guild.idLong)

                    respond {
                        embedWithColor {
                            description = if (duration.isEmpty())
                                tr("voiceactivity.leaderboard.timespent.none")
                            else {
                                val entries = duration.entries.toList().mapIndexed { idx, (userId, duration) ->
                                    tr(
                                        "voiceactivity.leaderboard.timespent.line",
                                        idx + 1,
                                        guild.retrieveMemberById(userId).awaitOrNull()?.effectiveName ?: "???",
                                        (duration ?: Duration.ZERO).formatElapsedVerbose()
                                    )
                                }

                                entries.joinToString("\n")
                            }

                        }
                    }
                }
            }
        }
    }

    private suspend fun CheckContextWithCache<SlashCommandInteractionEvent>.failIfNoVoiceTracking() =
        failIf(
            !guildSettingsManager.get(this.event.guild!!).allowVoiceTracking,
            tr("voiceactivity.disabled")
        )

}