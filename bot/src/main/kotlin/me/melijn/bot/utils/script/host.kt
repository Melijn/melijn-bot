/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package me.melijn.bot.utils.script

import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import me.melijn.bot.commands.EvalCommand
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

/**
 * Good sources
 * https://github.com/Kotlin/kotlin-script-examples/blob/master/ReadMe.md
 * https://github.com/Kotlin/KEEP/blob/master/proposals/scripting-support.md#kotlin-main-kts
 * https://kotlinlang.org/docs/custom-script-deps-tutorial.html#run-scripts
 *
 * this file:
 * https://github.com/Kotlin/kotlin-script-examples/blob/99bca23d0de02b27ba0e9f2a9b2bb246174fce3e/jvm/basic/jvm-maven-deps/host/src/main/kotlin/org/jetbrains/kotlin/script/examples/jvm/resolve/maven/host/host.kt
 */


fun evalCode(script: String, implicitReceivers: ChatCommandContext<out EvalCommand.EvalArgs>, props: Map<String, Any?>): ResultWithDiagnostics<EvaluationResult> {

    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<ScriptWithMavenDeps>()

    return BasicJvmScriptingHost().eval(StringScriptSource(script), compilationConfiguration, ScriptEvaluationConfiguration {
        this.providedProperties("test" to implicitReceivers)
        println(implicitReceivers)
        this.implicitReceivers(implicitReceivers)
    })
}

