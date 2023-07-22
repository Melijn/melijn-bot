package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import me.melijn.bot.utils.InferredChoiceEnum
import org.jetbrains.exposed.dao.id.IdTable
import kotlin.reflect.KMutableProperty1
import me.melijn.gen.GuildSettingsData as GSD

@CreateTable
@TableModel(true)
object GuildSettings : IdTable<Long>("guild_settings") {

    override var id = long("guild_id").entityId()

    var allowSpacedPrefix = bool("allow_spaced_prefix").default(false)
    var allowNsfw = bool("allow_nsfw").default(false)
    var allowVoiceTracking = bool("allow_voice_tracking").default(false)
    var allowInviteTracking = bool("allow_invite_tracking").default(false)
    var enableNameNormalization = bool("name_normalization").default(false)
    var enableLeveling = bool("enable_leveling").default(false)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

enum class GuildFeature(val correspondent: KMutableProperty1<GSD, Boolean>) : InferredChoiceEnum {
    SPACED_PREFIX(GSD::allowSpacedPrefix),
    NSFW(GSD::allowNsfw),
    VOICE_TRACKING(GSD::allowVoiceTracking),
    INVITE_TRACKING(GSD::allowInviteTracking),
    NAME_NORMALIZATION(GSD::enableNameNormalization),
    LEVELING(GSD::enableLeveling)
}
