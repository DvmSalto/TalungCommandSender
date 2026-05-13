// CRC16 with polynomial 0x9400, initial value 0xFFFF
fun crc16_9400(data: ByteArray, offset: Int = 0, length: Int = data.size, initial: Int = 0xFFFF): Int {
    var crc = initial
    for (i in offset until (offset + length)) {
        crc = crc xor (data[i].toInt() and 0xFF shl 8)
        for (j in 0 until 8) {
            crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x9400 else crc shl 1
        }
        crc = crc and 0xFFFF
    }
    return crc
}

/**
 * Build a chameleon data frame (like data_frame_make in C)
 * Format: [SOF][CMD][STATUS][LEN][LRC1][LRC2][DATA][LRC3]
 * All fields are little-endian except SOF (1 byte)
 */
fun makeChameleonFrame(cmd: Int, status: Int, data: ByteArray): ByteArray {
    val SOF: Byte = 0xA5.toByte()
    val preamble = ByteArray(9)
    preamble[0] = SOF
    // CMD (2 bytes LE)
    preamble[1] = (cmd and 0xFF).toByte()
    preamble[2] = ((cmd shr 8) and 0xFF).toByte()
    // STATUS (2 bytes LE)
    preamble[3] = (status and 0xFF).toByte()
    preamble[4] = ((status shr 8) and 0xFF).toByte()
    // LEN (2 bytes LE)
    preamble[5] = (data.size and 0xFF).toByte()
    preamble[6] = ((data.size shr 8) and 0xFF).toByte()
    // LRC1 (2 bytes, CRC16 of SOF only)
    val lrc1 = crc16_9400(preamble, 0, 1)
    preamble[7] = (lrc1 and 0xFF).toByte()
    preamble[8] = ((lrc1 shr 8) and 0xFF).toByte()

    // LRC2 (2 bytes, CRC16 of preamble up to LRC2)
    val lrc2 = crc16_9400(preamble, 0, 7)
    val preambleFull = preamble + byteArrayOf((lrc2 and 0xFF).toByte(), ((lrc2 shr 8) and 0xFF).toByte())

    // Frame = preambleFull + data + LRC3
    val frame = ByteArray(preambleFull.size + data.size + 2)
    System.arraycopy(preambleFull, 0, frame, 0, preambleFull.size)
    System.arraycopy(data, 0, frame, preambleFull.size, data.size)
    val lrc3 = crc16_9400(data, 0, data.size, lrc2)
    frame[frame.size - 2] = (lrc3 and 0xFF).toByte()
    frame[frame.size - 1] = ((lrc3 shr 8) and 0xFF).toByte()
    return frame
}
data class DataFrame(
package com.example.talungcommandsender

// CRC16 with polynomial 0x9400, initial value 0xFFFF
fun crc16_9400(data: ByteArray, offset: Int = 0, length: Int = data.size, initial: Int = 0xFFFF): Int {
    var crc = initial
    for (i in offset until (offset + length)) {
        crc = crc xor (data[i].toInt() and 0xFF shl 8)
        for (j in 0 until 8) {
            crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x9400 else crc shl 1
        }
        crc = crc and 0xFFFF
    }
    return crc
}

/**
 * Build a chameleon data frame (like data_frame_make in C)
 * Format: [SOF][CMD][STATUS][LEN][LRC1][LRC2][DATA][LRC3]
 * All fields are little-endian except SOF (1 byte)
 */
fun makeChameleonFrame(cmd: Int, status: Int, data: ByteArray): ByteArray {
    val SOF: Byte = 0xA5.toByte()
    val preamble = ByteArray(9)
    preamble[0] = SOF
    // CMD (2 bytes LE)
    preamble[1] = (cmd and 0xFF).toByte()
    preamble[2] = ((cmd shr 8) and 0xFF).toByte()
    // STATUS (2 bytes LE)
    preamble[3] = (status and 0xFF).toByte()
    preamble[4] = ((status shr 8) and 0xFF).toByte()
    // LEN (2 bytes LE)
    preamble[5] = (data.size and 0xFF).toByte()
    preamble[6] = ((data.size shr 8) and 0xFF).toByte()
    // LRC1 (2 bytes, CRC16 of SOF only)
    val lrc1 = crc16_9400(preamble, 0, 1)
    preamble[7] = (lrc1 and 0xFF).toByte()
    preamble[8] = ((lrc1 shr 8) and 0xFF).toByte()

    // LRC2 (2 bytes, CRC16 of preamble up to LRC2)
    val lrc2 = crc16_9400(preamble, 0, 7)
    val preambleFull = preamble + byteArrayOf((lrc2 and 0xFF).toByte(), ((lrc2 shr 8) and 0xFF).toByte())

    // Frame = preambleFull + data + LRC3
    val frame = ByteArray(preambleFull.size + data.size + 2)
    System.arraycopy(preambleFull, 0, frame, 0, preambleFull.size)
    System.arraycopy(data, 0, frame, preambleFull.size, data.size)
    val lrc3 = crc16_9400(data, 0, data.size, lrc2)
    frame[frame.size - 2] = (lrc3 and 0xFF).toByte()
    frame[frame.size - 1] = ((lrc3 shr 8) and 0xFF).toByte()
    return frame
}

data class DataFrame(
    val command: Byte,
    val payload: ByteArray
) {
    companion object {
        private const val START_BYTE: Byte = 0xAA.toByte()
        private const val END_BYTE: Byte = 0x55.toByte()

        fun fromBytes(data: ByteArray): DataFrame? {
            if (data.size < 4 || data[0] != START_BYTE || data[data.size - 1] != END_BYTE) return null
            val command = data[1]
            val payload = data.sliceArray(2 until data.size - 2)
            val checksum = data[data.size - 2]
            if (checksum != calcChecksum(command, payload)) return null
            return DataFrame(command, payload)
        }

        fun toBytes(frame: DataFrame): ByteArray {
            val payload = frame.payload
            val checksum = calcChecksum(frame.command, payload)
            return byteArrayOf(START_BYTE, frame.command) + payload + byteArrayOf(checksum, END_BYTE)
        }

        private fun calcChecksum(command: Byte, payload: ByteArray): Byte {
            var sum = command.toInt()
            for (b in payload) sum += b.toInt() and 0xFF
            return (sum and 0xFF).toByte()
        }
    }
}
