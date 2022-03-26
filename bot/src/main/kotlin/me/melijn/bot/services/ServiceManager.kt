package me.melijn.bot.services

import me.melijn.kordkommons.utils.ReflectUtil

class ServiceManager {

    private val services: MutableList<Service> = mutableListOf()

    init {
        ReflectUtil.findAllClassesUsingClassLoader("me.melijn.bot.services")
            .filterNotNull()
            .filter { it.superclass.simpleName == "Service" }
            .forEach {
                services.add(it.getConstructor().newInstance() as Service)
            }

        services.forEach {
            if (it.autoStart) it.start()
        }
    }

    fun stopAll() {
        services.forEach { it.stop() }
    }
}