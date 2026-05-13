
package com.example.talungcommandsender

// CRC16 function matching the C reference
fun crc16_compute(data: ByteArray, size: Int, p_crc: Int? = null): Int {
    var crc = p_crc ?: 0xFFFF
    for (i in 0 until size) {
        crc = ((crc ushr 8) or (crc shl 8)) and 0xFFFF
        crc = crc xor (data[i].toInt() and 0xFF)
        crc = crc xor ((crc and 0xFF) ushr 4)
        crc = crc xor ((crc shl 8) shl 4) and 0xFFFF
        crc = crc xor (((crc and 0xFF) shl 4) shl 1) and 0xFFFF
    }
    return crc and 0xFFFF
}

/**
 * Build a data frame as specified:
 * SOF | CRC1 | CMD | STATUS | LEN | CRC2 | DATA | CRC3
 * 11  | CC CC| XX XX| 00 00 | 00 00| CC CC| xx xx xx | CC CC
 * All fields are little-endian except SOF (1 byte)
 */
fun makeDataFrame(cmd: Int, status: Int, data: ByteArray): ByteArray {
    val SOF: Byte = 0x11.toByte()
    // Header: SOF (1) + CRC1 (2) + CMD (2) + STATUS (2) + LEN (2) + CRC2 (2)
    val header = ByteArray(1 + 2 + 2 + 2 + 2 + 2)
    header[0] = SOF
    // CRC1 placeholder (2 bytes)
    // CMD (2 bytes LE)
    header[3] = (cmd and 0xFF).toByte()
    header[4] = ((cmd shr 8) and 0xFF).toByte()
    // STATUS (2 bytes LE)
    header[5] = (status and 0xFF).toByte()
    header[6] = ((status shr 8) and 0xFF).toByte()
    // LEN (2 bytes LE)
    header[7] = (data.size and 0xFF).toByte()
    header[8] = ((data.size shr 8) and 0xFF).toByte()
    // CRC2 placeholder (2 bytes)

    // CRC1 = crc16_compute(SOF, 1, CRC16_POLYNOMIAL)
    val crc1 = crc16_compute(header.copyOfRange(0, 1), 1)
    header[1] = (crc1 and 0xFF).toByte()
    header[2] = ((crc1 shr 8) and 0xFF).toByte()

    // CRC2 = crc16_compute(header[0..8], 9, CRC1)
    val crc2 = crc16_compute(header.copyOfRange(0, 9), 9, crc1)
    header[9] = (crc2 and 0xFF).toByte()
    header[10] = ((crc2 shr 8) and 0xFF).toByte()

    // Frame = header + data + CRC3
    val frame = ByteArray(header.size + data.size + 2)
    System.arraycopy(header, 0, frame, 0, header.size)
    System.arraycopy(data, 0, frame, header.size, data.size)
    // CRC3 = crc16_compute(data, data.size, CRC2)
    val crc3 = crc16_compute(data, data.size, crc2)
    frame[frame.size - 2] = (crc3 and 0xFF).toByte()
    frame[frame.size - 1] = ((crc3 shr 8) and 0xFF).toByte()
    return frame
}

