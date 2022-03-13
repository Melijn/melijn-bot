package me.melijn.bot

import org.koin.core.module.Module

abstract class InjectorInterface {
    abstract val module: Module
}