package com.sik.comm.impl_can

import com.sik.comm.core.interceptor.CommInterceptor
import com.sik.comm.core.interceptor.InterceptorChain
import com.sik.comm.core.model.CommMessage

class RealSendInterceptor: CommInterceptor {
    override suspend fun intercept(
        chain: InterceptorChain,
        original: CommMessage
    ): CommMessage {
        TODO("Not yet implemented")
    }
}