package me.melijn.bot.services

import com.kotlindiscord.kord.extensions.utils.getKoin
import me.melijn.ap.injector.Inject
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.Kind
import kotlin.reflect.full.isSubclassOf

@Inject
class ServiceManager {

    private val services: MutableList<Service> = mutableListOf()

    fun startAll() {
        val services = getAllCustom<Service>()

        services.forEach {
            if (it.autoStart) it.start()
        }
    }

    fun stopAll() {
        services.forEach { it.stop() }
    }

    @OptIn(KoinInternalApi::class)
    inline fun <reified T : Any> getAllCustom(): List<T> =
        getKoin().let { koin ->
            koin.instanceRegistry.instances.map { it.value.beanDefinition }
                .filter { it.kind == Kind.Singleton }
                .filter { it.primaryType.isSubclassOf(T::class) }
                .map { koin.get(clazz = it.primaryType, qualifier = null, parameters = null) }
        }
}