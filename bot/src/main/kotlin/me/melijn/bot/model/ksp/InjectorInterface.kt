package me.melijn.bot.model.ksp

import org.koin.core.module.Module

abstract class InjectorInterface {
    abstract val module: Module
}