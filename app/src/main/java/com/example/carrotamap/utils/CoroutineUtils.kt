package com.example.carrotamap.utils

import kotlinx.coroutines.*

/**
 * 协程优化工具类
 * 
 * 提供协程作用域管理的最佳实践：
 * 1. 避免使用 GlobalScope
 * 2. 使用结构化并发
 * 3. 正确的异常处理
 */
object CoroutineUtils {
    
    /**
     * 使用 SupervisorJob 创建协程作用域
     * 确保一个子协程失败不影响其他子协程
     * 
     * @param dispatcher 协程调度器
     * @return CoroutineScope
     */
    fun createSupervisorScope(dispatcher: CoroutineDispatcher = Dispatchers.Default): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcher)
    }
    
    /**
     * 安全执行协程任务
     * 自动处理异常并记录日志
     * 
     * @param scope 协程作用域
     * @param dispatcher 调度器
     * @param onError 错误回调
     * @param block 协程代码块
     */
    fun safeLaunch(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch(dispatcher + CoroutineExceptionHandler { _, throwable ->
            timber.log.Timber.e(throwable, "协程执行异常")
            onError?.invoke(throwable)
        }) {
            try {
                block()
            } catch (e: CancellationException) {
                // 协程取消是正常行为，不需要处理
                throw e
            } catch (e: Exception) {
                timber.log.Timber.e(e, "协程内部异常")
                onError?.invoke(e)
            }
        }
    }
    
    /**
     * 安全执行 IO 操作
     * 
     * @param scope 协程作用域
     * @param onError 错误回调
     * @param block IO 操作代码块
     */
    fun safeIO(
        scope: CoroutineScope,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return safeLaunch(scope, Dispatchers.IO, onError, block)
    }
    
    /**
     * 安全执行 Main 线程操作
     * 
     * @param scope 协程作用域
     * @param onError 错误回调
     * @param block Main 线程代码块
     */
    fun safeMain(
        scope: CoroutineScope,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return safeLaunch(scope, Dispatchers.Main, onError, block)
    }
    
    /**
     * 延迟执行（带取消支持）
     * 
     * @param delayMillis 延迟时间（毫秒）
     * @param scope 协程作用域
     * @param block 延迟后执行的代码
     */
    fun delayedLaunch(
        delayMillis: Long,
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch {
            delay(delayMillis)
            block()
        }
    }
    
    /**
     * 周期性执行任务
     * 
     * @param intervalMillis 执行间隔（毫秒）
     * @param scope 协程作用域
     * @param block 要周期执行的代码
     * @return Job 可用于取消周期任务
     */
    fun periodicLaunch(
        intervalMillis: Long,
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch {
            while (isActive) {
                try {
                    block()
                    delay(intervalMillis)
                } catch (e: CancellationException) {
                    // 协程被取消，退出循环
                    break
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "周期任务执行异常")
                    delay(intervalMillis)  // 出错后仍然等待间隔
                }
            }
        }
    }
    
    /**
     * 超时执行
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @param block 要执行的代码
     * @return 执行结果，超时返回 null
     */
    suspend fun <T> withTimeoutOrNull(
        timeoutMillis: Long,
        block: suspend CoroutineScope.() -> T
    ): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMillis, block)
        } catch (e: TimeoutCancellationException) {
            timber.log.Timber.w("操作超时: ${timeoutMillis}ms")
            null
        }
    }
    
    /**
     * 并发执行多个任务并等待全部完成
     * 
     * @param tasks 任务列表
     * @return 所有任务的结果列表
     */
    suspend fun <T> awaitAll(
        vararg tasks: suspend () -> T
    ): List<T> = coroutineScope {
        tasks.map { task ->
            async { task() }
        }.awaitAll()
    }
}

/**
 * 使用示例：
 * 
 * ```kotlin
 * // 1. 创建受监督的协程作用域
 * val networkScope = CoroutineUtils.createSupervisorScope(Dispatchers.IO)
 * 
 * // 2. 安全执行 IO 操作
 * CoroutineUtils.safeIO(networkScope, onError = { error ->
 *     Timber.e(error, "网络请求失败")
 * }) {
 *     // 网络请求代码
 *     val data = fetchDataFromNetwork()
 *     processData(data)
 * }
 * 
 * // 3. 周期性任务
 * val heartbeatJob = CoroutineUtils.periodicLaunch(
 *     intervalMillis = 1000L,
 *     scope = networkScope
 * ) {
 *     sendHeartbeat()
 * }
 * 
 * // 4. 取消任务
 * heartbeatJob.cancel()
 * 
 * // 5. 超时控制
 * val result = CoroutineUtils.withTimeoutOrNull(5000L) {
 *     longRunningOperation()
 * }
 * if (result == null) {
 *     println("操作超时")
 * }
 * ```
 */
