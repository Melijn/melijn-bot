package me.melijn.bot.cache

import me.melijn.ap.injector.Inject
import me.melijn.bot.model.AbstractOwnedMessage
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit

@Inject
class ButtonCache {

    val latexButtonOwners: ExpiringMap<AbstractOwnedMessage, Boolean> = ExpiringMap.builder()
        .expiration(60, TimeUnit.SECONDS)
        .build()

}