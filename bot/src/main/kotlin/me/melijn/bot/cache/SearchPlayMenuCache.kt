package me.melijn.bot.cache

import me.melijn.ap.injector.Inject
import me.melijn.bot.model.OwnedGuildMessage
import me.melijn.bot.model.SearchPlayMenu
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit

@Inject
class SearchPlayMenuCache {

    val cache = ExpiringMap.builder()
        .expiration(60, TimeUnit.SECONDS)
        .build<OwnedGuildMessage, SearchPlayMenu>()

}