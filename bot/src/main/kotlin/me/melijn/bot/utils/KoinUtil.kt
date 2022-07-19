package me.melijn.bot.utils

import com.kotlindiscord.kord.extensions.utils.getKoin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.mp.KoinPlatformTools

object KoinUtil {
    inline fun <reified T : Any> inject(
        qualifier: Qualifier? = null,
        mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
        noinline parameters: ParametersDefinition? = null
    ): Lazy<T> = lazy(mode) {
        getKoin().get(qualifier, parameters)
    }
}