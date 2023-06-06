package me.melijn.bot.commands.administration

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.coroutines.await
import kotlinx.datetime.Clock
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.events.UserNameListener
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.StringsUtil
import me.melijn.gen.uselimits.PersistentUsageLimitType
import net.dv8tion.jda.api.Permission
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

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

                var lastUpdate = Clock.System.now()
                members.forEachIndexed { index, member ->
                    val count = index + 1
                    if (count % 100 == 0 || (Clock.System.now() - lastUpdate > 5.minutes)) {
                        hook.editMessage("$header\n\n${tr("namenormalization.admin.progress", count)}")
                            .await()
                        lastUpdate = Clock.System.now()
                    }
                    UserNameListener.fixName(member)
                }

                hook.editMessage("$header\n\n${tr("namenormalization.admin.done")}")
                    .await()
            }
        }
    }


}
