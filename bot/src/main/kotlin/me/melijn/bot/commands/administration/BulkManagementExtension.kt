package me.melijn.bot.commands.administration

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.coroutines.await
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.events.UserNameListener
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.StringsUtil
import me.melijn.gen.uselimits.PersistentUsageLimitType
import net.dv8tion.jda.api.Permission
import kotlin.time.Duration.Companion.days

@KordExtension
class BulkManagementExtension : Extension() {

    override val name: String = "bulkManagement"

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "clean-names"
            description = "Clean up member usernames by normalizing characters."

            cooldown(PersistentUsageLimitType.GuildCommand) { 1.days }
            requireBotPermissions(Permission.NICKNAME_CHANGE)
            requirePermission(Permission.ADMINISTRATOR)

            action {
                val guild = this.guild!!
                val members = guild.findMembers {
                    !it.user.isBot
                            && StringsUtil.filterGarbage(it.effectiveName) != it.effectiveName
                            && guild.selfMember.canInteract(it)
                }.await()

                if (members.isEmpty()) {
                    respond {
                        content = tr("namenormalization.admin.nomembers")
                    }
                    return@action
                }

                val header = tr("namenormalization.admin.header", members.size)
                val hook = respond {
                    content = header
                }

                members.forEachIndexed { index, member ->
                    val count = index + 1
                    if (count % 100 == 0) {
                        hook.editMessage("$header\n\n${tr("namenormalization.admin.progress", count)}")
                            .await()
                    }
                    UserNameListener.fixName(member)
                }

                hook.editMessage("$header\n\n${tr("namenormalization.admin.done")}")
                    .await()
            }
        }
    }


}
