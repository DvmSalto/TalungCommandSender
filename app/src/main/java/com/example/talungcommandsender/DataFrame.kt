package com.example.talungcommandsender

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
