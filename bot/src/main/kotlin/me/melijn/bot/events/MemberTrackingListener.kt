package me.melijn.bot.events

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import kotlinx.datetime.toKotlinInstant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.InvitesManager
import me.melijn.bot.database.manager.MemberJoinTrackingManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.gen.InvitesData
import me.melijn.gen.MemberJoinTrackingData
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration.Companion.seconds

@Inject(true)
class MemberTrackingListener {

    private val inviteManager by KoinUtil.inject<InvitesManager>()
    private val memberTrackingManager by KoinUtil.inject<MemberJoinTrackingManager>()

    init {
        val shardManager by KoinUtil.inject<ShardManager>()
        shardManager.listener<GuildMemberJoinEvent> { event ->
            val guild = event.guild
            if (!guild.selfMember.hasPermission(Permission.MANAGE_SERVER)) return@listener

            val member = event.member
            val knownGuildInvites = inviteManager.getByGuildDeletedKey(guild.idLong, false)
            val newGuildInvites = guild.retrieveInvites().await()

            /**
             * Handle created invites, these could happen when the context menu create invites are used,
             * they get created on the first use.
             */
            val differentGuildInvites = newGuildInvites.filter {
                knownGuildInvites.none { knownInvite ->
                    knownInvite.inviteCode == it.code && knownInvite.uses == it.uses
                }
            }

            differentGuildInvites.forEach { invite ->
                inviteManager.store(
                    inviteData(invite)
                )
            }

            /** Handle deleted invites, handling the event would just cause extra race conditions I think. **/
            val deletedInvites = knownGuildInvites.filter {
                newGuildInvites.none { newInvite ->
                    newInvite.code == it.inviteCode
                }
            }

            deletedInvites.forEach { deletedInvite ->
                if (differentGuildInvites.isEmpty() && deletedInvite.uses == 0) {
                    inviteManager.store(deletedInvite.copy(uses = 1, deleted = true))
                } else {
                    if (deletedInvite.uses == 0) {
                        inviteManager.delete(deletedInvite)
                    } else {
                        inviteManager.store(deletedInvite.copy(deleted = true))
                    }
                }

            }

            val code: String = when (differentGuildInvites.size) {
                0 -> {
                    val invite = deletedInvites.firstOrNull { it.uses == 0 } ?: return@listener
                    invite.inviteCode
                }
                1 -> {
                    val invite = differentGuildInvites.first()
                    invite.code
                }

                else -> {
                    // TODO: Think of something better to handle this
                    val invite = differentGuildInvites.first()
                    invite.code
                }
            }

            val updatedJoinData = memberTrackingManager.getCachedById(guild.idLong, member.idLong)?.apply {
                joins++
                inviteCode = code
            } ?: MemberJoinTrackingData(
                guild.idLong,
                member.idLong,
                code,
                member.timeJoined.toInstant().toKotlinInstant(),
                1
            )

            memberTrackingManager.store(updatedJoinData)
        }

        shardManager.listener<GuildInviteCreateEvent> {
            it.invite.let { invite ->
                inviteManager.store(inviteData(invite))
            }
        }
    }

    private fun inviteData(invite: Invite) = InvitesData(
        invite.code,
        invite.guild?.idLong!!,
        invite.channel?.idLong!!,
        invite.inviter?.idLong!!,
        invite.uses,
        invite.timeCreated.toInstant().toKotlinInstant(),
        invite.maxAge.seconds,
        false
    )
}