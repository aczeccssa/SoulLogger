package com.lestere.opensource.arc

import com.lestere.opensource.logger.SoulLogger
import com.lestere.opensource.utils.toReadableString
import com.lestere.opensource.utils.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.*
import java.util.concurrent.locks.ReentrantLock

public object AutoReleaseCyber {
    /**
     * Launch Auto Release Cyber system.
     */
    internal fun launch() {
        try {
            this._active = true
            this.observeTasks()
            this.registerShutdownHook()
        } catch (e: Exception) {
            throw _root_ide_package_.com.lestere.opensource.arc.ARCException.arcSystemCached(e)
        }
    }

    private var _active = false

    /**
     * Auto release cyber running state
     */
    val active: Boolean get() = _active

    /**
     * Reentrant lock for queue read write safety.
     */
    private val lock = ReentrantLock()

    /**
     * The threshold time watch queue run...
     */
    private const val OBSERVER_TASK_THRESHOLD = 100L

    /**
     * Shutdown leave hook waiting duration.
     */
    private const val SHUTDOWN_HOOK_WAITING_DURATION = 10000L

    /**
     * Default coroutine scope for task queue observe...
     */
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Task queue storage the task which will be run...
     */
    private val taskQueue: PriorityQueue<com.lestere.opensource.arc.AutoReleaseCyberTask> = PriorityQueue(compareBy { it.timestamp })

    /**
     * Push task to task queue, system will run task in specific moment.
     */
    fun push(task: com.lestere.opensource.arc.AutoReleaseCyberTask) {
        if (_active) {
            val currency = Clock.System.now().toEpochMilliseconds() + OBSERVER_TASK_THRESHOLD
            if (task.timestamp < currency) {
                _root_ide_package_.com.lestere.opensource.logger.SoulLogger.error(_root_ide_package_.com.lestere.opensource.arc.ARCException.OUT_OF_TIME)
                return
            }
            // Safety write
            lock.withLock {
                taskQueue.add(task)
            }
        } else {
            _root_ide_package_.com.lestere.opensource.logger.SoulLogger.error(_root_ide_package_.com.lestere.opensource.arc.ARCException.PLUGIN_NOT_INSTALLED)
            return
        }
    }

    /**
     * Cancel the task in task queue by provided id.
     */
    fun cancel(task: com.lestere.opensource.arc.AutoReleaseCyberTask) {
        lock.withLock {
            taskQueue.remove(task)
        }
    }

    /**
     * The main function for task queue observe and run.
     */
    private fun observeTasks() {
        scope.launch {
            while (_active) {
                lock.withLock {
                    val tasks = mutableListOf<AutoReleaseCyberTask>()
                    val currencyStamp = Clock.System.now().toEpochMilliseconds()
                    val minDiff = currencyStamp - OBSERVER_TASK_THRESHOLD
                    val maxDiff = currencyStamp + OBSERVER_TASK_THRESHOLD
                    val currentRestOffset = minDiff..maxDiff
                    this@AutoReleaseCyber.taskQueue.forEach { task ->
                        if (!currentRestOffset.contains(task.timestamp)) return@forEach
                        tasks.add(task)
                    }
                    tasks.forEach(::runTask)
                }
                delay(OBSERVER_TASK_THRESHOLD)
            }
        }
    }

    /**
     * Main way to run task.
     */
    private fun runTask(task: AutoReleaseCyberTask) {
        scope.launch {
            taskQueue.remove(task)
            try {
                val startTime = Clock.System.now()
                SoulLogger.info("Task ${task.id} is start in ${startTime.toReadableString()}.")
                task.activity()
                val finishTime = Clock.System.now()
                SoulLogger.info("Task ${task.id} finished, during ${(finishTime - startTime).absoluteValue}.")
            } catch (e: Exception) {
                SoulLogger.error(ARCException.arcTaskRunningException(task.id, e))
            }
        }
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            this._active = false
            val tasks = taskQueue.filter { it.tag == AutoReleaseCyberTag.FORCE }
            SoulLogger.info("ARC(Auto Release Cyber) shutdown force tasks cyber, ${tasks.size} has been force run, ${taskQueue.size - tasks.size} been canceled cause server down.")
            SoulLogger.info("ARC(Auto Release Cyber) shutdown force thread will not capture the task running information.")

            tasks.forEach { task ->
                Thread {
                    try {
                        task.activity()
                    } catch (e: Exception) {
                        SoulLogger.error(ARCException.arcTaskRunningException(task.id, e))
                    }
                }.start()
            }

            // Wait for up to 10 seconds for all tasks to complete
            Thread.sleep(SHUTDOWN_HOOK_WAITING_DURATION)
        })
    }
}