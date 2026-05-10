package com.sik.comm.internal.channel

import com.sik.comm.CommChannel
import com.sik.comm.CommException
import com.sik.comm.CommReceiver
import com.sik.comm.OpenCallback
import com.sik.comm.internal.state.ChannelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicReference

/**
 * 通道实现公共基类。
 *
 * 提供：
 * - 协程作用域管理
 * - 接收回调托管
 * - 统一状态机（AtomicReference CAS）
 * - 打开结果回调（[OpenCallback]）自动触发
 *
 * 子类职责：
 * - 实现 [doOpen]：同步打开底层连接，返回句柄
 * - 实现 [doClose]：释放底层资源
 * - 覆盖 [onOpened]：句柄有效后启动 IO 循环等
 * - 自行处理 USB 权限等异步 open 场景（参见 [UsbSerialChannelImpl]）
 */
internal abstract class BaseCommChannel(
    override val id: String
) : CommChannel {

    protected val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val stateRef = AtomicReference<ChannelState>(ChannelState.Closed)

    @Volatile
    protected var currentReceiver: CommReceiver? = null

    @Volatile
    private var openCallback: OpenCallback? = null

    // ---------- 状态机工具 ----------

    protected val currentState: ChannelState get() = stateRef.get()

    protected fun tryTransition(expected: ChannelState, newState: ChannelState): Boolean =
        stateRef.compareAndSet(expected, newState)

    /**
     * 设置状态，并在状态发生实质变化时自动触发 [OpenCallback]。
     */
    protected fun transitionState(newState: ChannelState) {
        val oldState = stateRef.getAndSet(newState)
        if (oldState == newState) return
        when (newState) {
            is ChannelState.Open -> openCallback?.onResult(true, null)
            is ChannelState.Failed -> openCallback?.onResult(false, newState.cause)
            else -> {}
        }
    }

    protected fun requireHandle(): Long {
        val s = stateRef.get()
        return if (s is ChannelState.Open) s.handle
        else throw CommException.NotOpen(id)
    }

    // ---------- CommChannel 实现 ----------

    override fun isOpen(): Boolean = stateRef.get() is ChannelState.Open

    override fun setReceiver(receiver: CommReceiver?) {
        this.currentReceiver = receiver
    }

    override fun setOpenCallback(callback: OpenCallback?) {
        this.openCallback = callback
    }

    /**
     * 同步打开通道。
     *
     * 子类若 open 逻辑是同步的（Serial / CAN），直接继承此方法即可。
     * 若 open 逻辑是异步的（USB 权限），子类可覆盖此方法并使用状态机工具。
     */
    open override fun open() {
        while (true) {
            when (val current = stateRef.get()) {
                is ChannelState.Open -> return
                is ChannelState.Opening -> return
                is ChannelState.Closed,
                is ChannelState.Failed -> {
                    if (!tryTransition(current, ChannelState.Opening)) continue
                    try {
                        val handle = doOpen()
                        if (handle > 0L) {
                            transitionState(ChannelState.Open(handle))
                            onOpened(handle)
                            return
                        } else {
                            val error = CommException.OpenFailed(id, handle)
                            transitionState(ChannelState.Failed(error))
                            throw error
                        }
                    } catch (e: Throwable) {
                        transitionState(ChannelState.Failed(e))
                        throw e
                    }
                }
            }
        }
    }

    override fun close() {
        val prev = stateRef.getAndSet(ChannelState.Closed)
        if (prev is ChannelState.Open) {
            doClose(prev.handle)
        }
        scope.cancel()
    }

    // ---------- 子类钩子 ----------

    /**
     * 同步打开底层连接。
     * @return >0 句柄；<=0 失败
     */
    protected abstract fun doOpen(): Long

    /**
     * 关闭底层连接。
     * @param handle [doOpen] 返回的句柄
     */
    protected abstract fun doClose(handle: Long)

    /**
     * 连接成功后的回调。
     * @param handle 有效句柄
     */
    protected open fun onOpened(handle: Long) {}
}
