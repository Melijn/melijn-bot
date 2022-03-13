package me.melijn.bot.utils.threading

import kotlinx.coroutines.*
import java.util.concurrent.*

object TaskManager {

    private val threadFactory = { name: String ->
        var counter = 0
        { r: Runnable ->
            Thread(r, "[$name-Pool-%d]".replace("%d", "${counter++}"))
        }
    }

    val executorService: ExecutorService = ForkJoinPool()
    private val dispatcher = executorService.asCoroutineDispatcher()
    val scheduledExecutorService: ScheduledExecutorService =
        Executors.newScheduledThreadPool(15, threadFactory.invoke("Repeater"))
    val coroutineScope = CoroutineScope(dispatcher)

    fun async(block: suspend CoroutineScope.() -> Unit): Job {
        return coroutineScope.launch {
            Task {
                block.invoke(this)
            }.run()
        }
    }

    fun asyncIgnoreEx(block: suspend CoroutineScope.() -> Unit) = coroutineScope.launch {
        try {
            block.invoke(this)
        } catch (t: Throwable) {
            // ignored by design
        }
    }

    fun <T> taskValueAsync(block: suspend CoroutineScope.() -> T): Deferred<T> = coroutineScope.async {
        DeferredTask { block.invoke(this) }.run()
    }

    fun <T> taskValueNAsync(block: suspend CoroutineScope.() -> T?): Deferred<T?> = coroutineScope.async {
        DeferredNTask {
            block.invoke(this)
        }.run()
    }

    fun <T> evalTaskValueNAsync(block: suspend CoroutineScope.() -> T?): Deferred<Pair<T?, String>> = coroutineScope.async {
        EvalDeferredNTask {
            block.invoke(this)
        }.run()
    }

    inline fun asyncInline(crossinline block: CoroutineScope.() -> Unit) = coroutineScope.launch {
        TaskInline {
            block.invoke(this)
        }.run()
    }

    inline fun asyncAfter(afterMillis: Long, crossinline func: suspend () -> Unit): ScheduledFuture<*> {
        return scheduledExecutorService.schedule(RunnableTask { func() }, afterMillis, TimeUnit.MILLISECONDS)
    }
}
