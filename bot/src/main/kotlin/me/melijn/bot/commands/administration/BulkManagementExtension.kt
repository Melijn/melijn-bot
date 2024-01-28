package me.melijn.bot.commands.administration

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.await
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.MessageEdit
import kotlinx.datetime.Clock
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.events.UserNameListener
import me.melijn.bot.utils.KordExUtils.publicGuildSlashCommand
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.StringsUtil
import me.melijn.gen.uselimits.PersistentUsageLimitType
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@KordExtension
class BulkManagementExtension : Extension() {

    override val name: String = "bulkManagement"

    override suspend fun setup() {
        publicGuildSlashCommand {
            name = "clean-names"
            description = "Clean up member usernames by normalizing characters."

            cooldown(PersistentUsageLimitType.GuildCommand) { 1.minutes } // Avoid spam while finding members.
            requireBotPermissions(Permission.NICKNAME_CHANGE)
            requirePermission(Permission.ADMINISTRATOR)

            action {
                val buttonId = UUID.randomUUID().toString()
                val yesButtonId = buttonId + "yes"
                val noButtonId = buttonId + "no"
                respond {
                    content = tr("nameNormalization.admin.confirmationQuestion")
                    actionRow(
                        button(yesButtonId, "YES", null, ButtonStyle.SUCCESS),
                        button(noButtonId, "NO", null, ButtonStyle.DANGER)
                    )
                }
                val guild = this.guild!!
                val buttonEvent = guild.jda.await<ButtonInteractionEvent> {
                    (it.button.id == yesButtonId || it.button.id == noButtonId) && it.user == this.user
                }

                val hook = buttonEvent.deferEdit().await()
                // Handle the cancellation button of cleaning names
                if (buttonEvent.button.id == noButtonId) {
                    hook.editOriginal(MessageEdit {
                        content = tr("nameNormalization.admin.cancel")
                    }).setReplace(true).await()
                    return@action
                }

                val members = guild.findMembers {
                    !it.user.isBot
                            && StringsUtil.getNormalizedUsername(it) != it.effectiveName
                            && guild.selfMember.canInteract(it)
                }.await()

                val bonusCooldown = members.size / 10_000 // Per 10k users an extra day cooldown
                cooldowns[PersistentUsageLimitType.GuildCommand] = 1.days + bonusCooldown.days

                if (members.isEmpty()) {
                    hook.editOriginal(MessageEdit {
                        content = tr("nameNormalization.admin.noMembers")
                    }).setReplace(true).await()
                    return@action
                }

                val header = tr("nameNormalization.admin.header", members.size)
                hook.editOriginal(header).setReplace(true).await()

                var lastUpdate = Clock.System.now()
                members.forEachIndexed { index, member ->
                    val count = index + 1
                    if (count % 100 == 0 || (Clock.System.now() - lastUpdate > 5.minutes)) {
                        hook.editOriginal("$header\n\n${tr("nameNormalization.admin.progress", count, members.size)}")
                            .await()
                        lastUpdate = Clock.System.now()
                    }
                    UserNameListener.fixName(member)
                }

                hook.editOriginal("$header\n\n${tr("nameNormalization.admin.done")}")
                    .await()
            }
        }
    }


}
