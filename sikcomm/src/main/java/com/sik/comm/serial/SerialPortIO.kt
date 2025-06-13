package com.sik.comm.serial

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Basic serial port I/O wrapper working on /dev/ttyS*.
 * This class clears the input buffer before every read
 * to avoid residual data as required.
 */
class SerialPortIO(path: String) {
    private val file: File = File(path)
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
    private val input = FileInputStream(pfd.fileDescriptor)
    private val output = FileOutputStream(pfd.fileDescriptor)

    fun clearInput() {
        while (input.available() > 0) {
            input.read()
        }
    }

    fun write(data: ByteArray) {
        output.write(data)
        output.flush()
    }

    fun read(length: Int): ByteArray {
        val buffer = ByteArray(length)
        input.read(buffer)
        return buffer
    }

    /**
     * Close all underlying streams and descriptor.
     */
    fun close() {
        try {
            input.close()
        } catch (_: Exception) {
        }
        try {
            output.close()
        } catch (_: Exception) {
        }
        try {
            pfd.close()
        } catch (_: Exception) {
        }
    }
}
