package com.camera2kotlin.utils

import android.util.Log
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

object BackgroundExecutor {


    val DEFAULT_EXECUTOR: Executor = Executors.newScheduledThreadPool(2 * Runtime.getRuntime().availableProcessors())
    private val executor = DEFAULT_EXECUTOR
    private val TASKS = ArrayList<Task>()
    private val CURRENT_SERIAL = ThreadLocal<String>()

    private fun directExecute(runnable: Runnable, delay: Long): Future<*>? {
        var future: Future<*>? = null
        if (delay > 0) {
            if (executor !is ScheduledExecutorService) {
                throw IllegalArgumentException("The executor set does not support scheduling")
            }
            future = executor.schedule(runnable, delay, TimeUnit.MILLISECONDS)
        } else {
            if (executor is ExecutorService) {
                future = executor.submit(runnable)
            } else {
                executor.execute(runnable)
            }
        }
        return future
    }


    @Synchronized
    fun execute(task: Task) {
        var future: Future<*>? = null
        if (task.serial == null || !hasSerialRunning(task.serial)) {
            task.executionAsked = true
            future = directExecute(task, task.remainingDelay)
        }
        if ((task.id != null || task.serial != null) && !task.managed.get()) {
            task.future = future
            TASKS.add(task)
        }
    }

    private fun hasSerialRunning(serial: String?): Boolean {
        for (task in TASKS) {
            if (task.executionAsked && serial == task.serial) {
                return true
            }
        }
        return false
    }


    private fun take(serial: String): Task? {
        val len = TASKS.size
        for (i in 0 until len) {
            if (serial == TASKS[i].serial) {
                return TASKS.removeAt(i)
            }
        }
        return null
    }

    @Synchronized
    fun cancelAll(id: String, mayInterruptIfRunning: Boolean) {
        for (i in TASKS.indices.reversed()) {
            val task = TASKS[i]
            if (id == task.id) {
                if (task.future != null) {
                    task.future!!.cancel(mayInterruptIfRunning)
                    if (!task.managed.getAndSet(true)) {
                        task.postExecute()
                    }
                } else if (task.executionAsked) {
                    Log.e(
                        "System out",
                        "A task with id " + task.id + " cannot be cancelled (the executor set does not support it)"
                    )
                } else {

                    TASKS.removeAt(i)
                }
            }
        }
    }

    abstract class Task(id: String, delay: Long, serial: String) : Runnable {

        var id: String? = null
        var remainingDelay: Long = 0
        var targetTimeMillis: Long = 0 /* since epoch */
        var serial: String? = null
        var executionAsked: Boolean = false
        var future: Future<*>? = null

        val managed = AtomicBoolean()

        init {
            if ("" != id) {
                this.id = id
            }

            if (delay > 0) {
                remainingDelay = delay
                targetTimeMillis = System.currentTimeMillis() + delay
            }
            if ("" != serial) {
                this.serial = serial
            }
        }

        override fun run() {
            if (managed.getAndSet(true)) {
                return
            }

            try {
                CURRENT_SERIAL.set(serial)
                execute()
            } finally {
                postExecute()
            }
        }

        abstract fun execute()

        fun postExecute() {
            if (id == null && serial == null) {
                /* nothing to do */
                return
            }
            CURRENT_SERIAL.set(null)
            synchronized(BackgroundExecutor::class.java) {
                /* execution complete */
                TASKS.remove(this)

                if (serial != null) {
                    val next = take(serial!!)
                    if (next != null) {
                        if (next.remainingDelay != 0L) {
                            /* the delay may not have elapsed yet */
                            next.remainingDelay = Math.max(0L, targetTimeMillis - System.currentTimeMillis())
                        }
                        /* a task having the same serial was queued, execute it */
                        BackgroundExecutor.execute(next)
                    }
                }
            }
        }
    }
}

