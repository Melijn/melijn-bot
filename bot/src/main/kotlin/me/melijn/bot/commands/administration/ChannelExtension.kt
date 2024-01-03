package me.melijn.bot.commands.administration

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.LogChannelManager
import me.melijn.bot.model.enums.LogChannelType
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.publicGuildSubCommand
import me.melijn.gen.LogChannelsData
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel

@KordExtension
class ChannelExtension : Extension() {
    override val name: String = "channel"

    val channelManager: LogChannelManager by inject()
    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "channel"
            description = "5"
            publicGuildSubCommand {
                name = "log"
                description = "manage log chnanels"
                publicGuildSubCommand(::ChannelSetArgs) {
                    name = "set"
                    description = "sets a logchannel"
                    action {
                        respond {
                            val type = arguments.channelType
                            val channel = arguments.channel

                            val data = LogChannelsData(guild!!.idLong, type, channel.idLong)
                            channelManager.store(data)
                            content = "Set logchannel to $data"
                        }
                    }
                }
                publicGuildSubCommand(::ChannelTypeArgs) {
                    name = "unset"
                    description = "Unsets a logchannel"
                    action {
                        respond {
                            val type = arguments.channelType

                            channelManager.deleteById(guild!!.idLong, type)
                            content = "Unset logchannel"
                        }
                    }
                }
                publicGuildSubCommand(::ChannelTypeArgs) {
                    name = "view"
                    description = "Views a logchannel"
                    action {
                        respond {
                            val type = arguments.channelType

                            val data = channelManager.getRawById(guild!!.idLong, type)
                            content = "LogChannel: ${data}"
                        }
                    }
                }
            }
        }
    }

    inner class ChannelSetArgs : Arguments() {
        val channelType by enumChoice<LogChannelType> {
            name = "type"
            description = "type of logchannel"
            typeName = "type"
        }
        val channel by channel<GuildChannel> {
            name = "channel"
            description = "guild channel"
        }
    }

    inner class ChannelTypeArgs : Arguments() {
        val channelType by enumChoice<LogChannelType> {
            name = "type"
            description = "type of logchannel"
            typeName = "type"
        }
    }
}