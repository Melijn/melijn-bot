package me.melijn.bot.database.model

import me.melijn.annotationprocessors.cacheable.Cacheable
import me.melijn.annotationprocessors.createtable.CreateTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

@Cacheable
class GuildSetting(id: EntityID<ULong>) : Entity<ULong>(id),CacheableTable {
    companion object : EntityClass<ULong, GuildSetting>(GuildSettings)

    val prefixes by GuildSettings.prefixes
    val allowNsfw by GuildSettings.allowNsfw

    override fun toData(): CacheableData = this.toCache()
}

@CreateTable
object GuildSettings : IdTable<ULong>("guild_settings") {

    @OptIn(ExperimentalUnsignedTypes::class)
    override var id = ulong("guild_id").entityId()
    var prefixes = text("prefixes").default("")
    var allowNsfw = bool("allow_nsfw").default(false)

    override val primaryKey: PrimaryKey = PrimaryKey(id)

}

fun GuildSetting.toCache(): GuildSettingData {
    return GuildSettingData(this.id.value, this.prefixes, this.allowNsfw)
}

class GuildSettingData(
    private val _guildId: ULong,
    private val _prefixes: String,
    private val _allowNsfw: Boolean
) : CacheableData {

    var prefixes: String = _prefixes
    var allowNsfw: Boolean = _allowNsfw

    override fun getId(): String {
        return _guildId.toString()
    }
}