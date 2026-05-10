package com.sik.comm.internal.pipeline

import com.sik.comm.CommReceiver
import org.junit.Assert.*
import org.junit.Test

class ReceivePipelineTest {

    @Test
    fun `empty pipeline passes through`() {
        val received = mutableListOf<ByteArray>()
        val finalReceiver = CommReceiver { data, offset, length ->
            received.add(data.copyOfRange(offset, offset + length))
        }

        val pipeline = ReceivePipeline(emptyList(), finalReceiver)
        pipeline.onBytesReceived(byteArrayOf(0x01, 0x02), 0, 2)

        assertEquals(1, received.size)
        assertArrayEquals(byteArrayOf(0x01, 0x02), received[0])
    }

    @Test
    fun `single stage transforms data`() {
        val stage = PipelineStage { data, offset, length, downstream ->
            // 简单反转
            val reversed = data.copyOfRange(offset, offset + length).reversedArray()
            downstream.onBytesReceived(reversed, 0, reversed.size)
        }

        val received = mutableListOf<ByteArray>()
        val finalReceiver = CommReceiver { data, offset, length ->
            received.add(data.copyOfRange(offset, offset + length))
        }

        val pipeline = ReceivePipeline(listOf(stage), finalReceiver)
        pipeline.onBytesReceived(byteArrayOf(0x01, 0x02, 0x03), 0, 3)

        assertEquals(1, received.size)
        assertArrayEquals(byteArrayOf(0x03, 0x02, 0x01), received[0])
    }

    @Test
    fun `multiple stages chain correctly`() {
        val stage1 = PipelineStage { data, offset, length, downstream ->
            val doubled = ByteArray(length * 2)
            val slice = data.copyOfRange(offset, offset + length)
            System.arraycopy(slice, 0, doubled, 0, length)
            System.arraycopy(slice, 0, doubled, length, length)
            downstream.onBytesReceived(doubled, 0, doubled.size)
        }

        val stage2 = PipelineStage { data, offset, length, downstream ->
            // 只传前一半
            downstream.onBytesReceived(data, offset, length / 2)
        }

        val received = mutableListOf<ByteArray>()
        val finalReceiver = CommReceiver { data, offset, length ->
            received.add(data.copyOfRange(offset, offset + length))
        }

        val pipeline = ReceivePipeline(listOf(stage1, stage2), finalReceiver)
        pipeline.onBytesReceived(byteArrayOf(0x0A, 0x0B), 0, 2)

        assertEquals(1, received.size)
        // stage1: [0A, 0B, 0A, 0B]; stage2 传前一半 -> [0A, 0B]
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), received[0])
    }

    @Test
    fun `stage can drop data`() {
        val stage = PipelineStage { _, _, _, _ ->
            // 什么都不传，直接丢弃
        }

        val received = mutableListOf<ByteArray>()
        val finalReceiver = CommReceiver { data, offset, length ->
            received.add(data.copyOfRange(offset, offset + length))
        }

        val pipeline = ReceivePipeline(listOf(stage), finalReceiver)
        pipeline.onBytesReceived(byteArrayOf(0x01), 0, 1)

        assertTrue(received.isEmpty())
    }
}
