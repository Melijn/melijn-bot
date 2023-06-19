package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Mem
import me.melijn.gen.MemData
import me.melijn.gen.database.manager.AbstractMemManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.select

@Inject
class MemManager(override val driverManager: DriverManager) : AbstractMemManager(driverManager) {
    suspend fun getRandomMeme(guildId: Long): MemData? {
        return scopedTransaction {
            Mem.select {
                Mem.guildId eq guildId
            }.orderBy(Random())
                .limit(1)
                .firstOrNull()?.let {
                    MemData.fromResRow(it)
                }
        }
    }
}