package com.example.couplefarm.network

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val TAG = "OnlineMqttClient"

/**
 * High-availability public MQTT brokers accessible worldwide with no account or configuration.
 */
val PUBLIC_MQTT_BROKERS = listOf(
    "broker.hivemq.com" to 1883,
    "broker.emqx.io" to 1883,
)

sealed class OnlineMqttState {
    object Disconnected : OnlineMqttState()
    data class Connecting(val broker: String) : OnlineMqttState()
    data class Connected(val broker: String) : OnlineMqttState()
    data class Error(val message: String) : OnlineMqttState()
}

/**
 * Pure Kotlin MQTT 3.1.1 client built directly on standard Java Sockets.
 * Requires ZERO third-party libraries, ZERO native .so files, and ZERO external databases.
 */
class OnlineMqttClient(
    private val clientId: String = "cf_${UUID.randomUUID().toString().take(8)}",
    private val brokers: List<Pair<String, Int>> = PUBLIC_MQTT_BROKERS,
    private val onStateChanged: (OnlineMqttState) -> Unit = {},
    private val onMessageReceived: (topic: String, payload: ByteArray) -> Unit = { _, _ -> },
) : AutoCloseable {

    private val isRunning = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    private val packetIdCounter = AtomicInteger(1)

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var networkThread: Thread? = null
    private var pingThread: Thread? = null

    private val pendingSubscriptions = ConcurrentLinkedQueue<String>()
    private val activeSubscriptions = ConcurrentLinkedQueue<String>()

    fun connect() {
        if (isRunning.getAndSet(true)) return

        networkThread = Thread({
            var brokerIndex = 0
            while (isRunning.get()) {
                val (host, port) = brokers[brokerIndex % brokers.size]
                try {
                    onStateChanged(OnlineMqttState.Connecting(host))
                    val s = Socket()
                    socket = s
                    s.tcpNoDelay = true
                    s.soTimeout = 40000 // 40 seconds socket timeout
                    s.connect(InetSocketAddress(host, port), 6000)

                    val input = s.getInputStream()
                    val output = s.getOutputStream()
                    outputStream = output

                    // 1. Send CONNECT packet (MQTT 3.1.1)
                    sendConnectPacket(output, clientId)

                    // 2. Read CONNACK (Fixed header 0x20, remaining length 0x02)
                    val header = input.read()
                    if (header == -1) throw EOFException("Socket closed during handshake")
                    val remLen = readRemainingLength(input)
                    val connackBody = ByteArray(remLen)
                    readFully(input, connackBody)

                    if (header != 0x20 || connackBody.size < 2 || connackBody[1] != 0.toByte()) {
                        throw IOException("MQTT CONNECT rejected with return code: ${connackBody.getOrNull(1)}")
                    }

                    isConnected.set(true)
                    onStateChanged(OnlineMqttState.Connected(host))

                    // Start Keep-Alive Ping thread
                    startPingThread()

                    // Resubscribe to any active/pending topics
                    val topicsToSub = (activeSubscriptions + pendingSubscriptions).toSet()
                    for (topic in topicsToSub) {
                        sendSubscribePacket(output, topic)
                    }

                    // 3. Message dispatch loop
                    while (isRunning.get() && s.isConnected && !s.isClosed) {
                        val packetTypeHeader = input.read()
                        if (packetTypeHeader == -1) break
                        val length = readRemainingLength(input)
                        val payload = ByteArray(length)
                        readFully(input, payload)

                        val packetType = (packetTypeHeader and 0xF0) ushr 4
                        when (packetType) {
                            3 -> { // PUBLISH (QoS 0)
                                handlePublishPacket(payload)
                            }
                            9 -> { // SUBACK
                                Log.d(TAG, "SUBACK received")
                            }
                            13 -> { // PINGRESP
                                Log.v(TAG, "PINGRESP received")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.w(TAG, "MQTT connection failure on $host: ${e.message}")
                        onStateChanged(OnlineMqttState.Error("Fallo de conexión en $host: ${e.localizedMessage ?: "Error"}"))
                    }
                } finally {
                    isConnected.set(false)
                    stopPingThread()
                    try {
                        socket?.close()
                    } catch (_: Exception) {}
                    socket = null
                    outputStream = null
                }

                if (isRunning.get()) {
                    // Try next broker after brief backoff
                    brokerIndex++
                    try {
                        Thread.sleep(2000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            onStateChanged(OnlineMqttState.Disconnected)
        }, "OnlineMqttClient-Worker").apply {
            isDaemon = true
            start()
        }
    }

    fun subscribe(topic: String) {
        if (!activeSubscriptions.contains(topic)) {
            activeSubscriptions.add(topic)
        }
        val out = outputStream
        if (isConnected.get() && out != null) {
            Thread({
                try {
                    sendSubscribePacket(out, topic)
                } catch (e: Exception) {
                    Log.w(TAG, "Subscribe error: ${e.message}")
                }
            }, "OnlineMqtt-Sub").start()
        }
    }

    fun publish(topic: String, message: ByteArray) {
        val out = outputStream ?: return
        if (!isConnected.get()) return
        try {
            sendPublishPacket(out, topic, message)
        } catch (e: Exception) {
            Log.w(TAG, "Publish error: ${e.message}")
        }
    }

    private fun handlePublishPacket(payload: ByteArray) {
        if (payload.size < 2) return
        val topicLen = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        if (payload.size < 2 + topicLen) return
        val topic = String(payload, 2, topicLen, Charsets.UTF_8)
        val msgOffset = 2 + topicLen
        val msgLength = payload.size - msgOffset
        val message = ByteArray(msgLength)
        System.arraycopy(payload, msgOffset, message, 0, msgLength)

        try {
            onMessageReceived(topic, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onMessageReceived: ${e.message}")
        }
    }

    private fun startPingThread() {
        stopPingThread()
        pingThread = Thread({
            while (isRunning.get() && isConnected.get()) {
                try {
                    Thread.sleep(20000) // Send ping every 20 seconds
                    val out = outputStream
                    if (out != null && isConnected.get()) {
                        synchronized(out) {
                            out.write(0xC0) // PINGREQ
                            out.write(0x00)
                            out.flush()
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Ping error: ${e.message}")
                    break
                }
            }
        }, "OnlineMqtt-Ping").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopPingThread() {
        pingThread?.interrupt()
        pingThread = null
    }

    private fun sendConnectPacket(out: OutputStream, clientId: String) {
        val protoName = "MQTT".toByteArray(Charsets.UTF_8)
        val clientIdBytes = clientId.toByteArray(Charsets.UTF_8)

        val variableHeader = ByteArrayOutputStream().apply {
            write((protoName.size ushr 8) and 0xFF)
            write(protoName.size and 0xFF)
            write(protoName)
            write(4) // MQTT 3.1.1 protocol level
            write(0x02) // Connect flags: Clean Session = 1
            write(0) // Keep-alive MSB (60s)
            write(60) // Keep-alive LSB
        }.toByteArray()

        val payload = ByteArrayOutputStream().apply {
            write((clientIdBytes.size ushr 8) and 0xFF)
            write(clientIdBytes.size and 0xFF)
            write(clientIdBytes)
        }.toByteArray()

        val remainingLength = variableHeader.size + payload.size
        synchronized(out) {
            out.write(0x10) // CONNECT fixed header
            encodeRemainingLength(remainingLength, out)
            out.write(variableHeader)
            out.write(payload)
            out.flush()
        }
    }

    private fun sendSubscribePacket(out: OutputStream, topic: String) {
        val topicBytes = topic.toByteArray(Charsets.UTF_8)
        val packetId = packetIdCounter.incrementAndGet() and 0xFFFF

        val variableHeader = byteArrayOf(
            ((packetId ushr 8) and 0xFF).toByte(),
            (packetId and 0xFF).toByte()
        )

        val payload = ByteArrayOutputStream().apply {
            write((topicBytes.size ushr 8) and 0xFF)
            write(topicBytes.size and 0xFF)
            write(topicBytes)
            write(0) // QoS 0
        }.toByteArray()

        val remainingLength = variableHeader.size + payload.size
        synchronized(out) {
            out.write(0x82) // SUBSCRIBE fixed header (QoS 1 required for subscribe header)
            encodeRemainingLength(remainingLength, out)
            out.write(variableHeader)
            out.write(payload)
            out.flush()
        }
    }

    private fun sendPublishPacket(out: OutputStream, topic: String, message: ByteArray) {
        val topicBytes = topic.toByteArray(Charsets.UTF_8)
        val variableHeader = ByteArrayOutputStream().apply {
            write((topicBytes.size ushr 8) and 0xFF)
            write(topicBytes.size and 0xFF)
            write(topicBytes)
        }.toByteArray()

        val remainingLength = variableHeader.size + message.size
        synchronized(out) {
            out.write(0x30) // PUBLISH fixed header (QoS 0)
            encodeRemainingLength(remainingLength, out)
            out.write(variableHeader)
            out.write(message)
            out.flush()
        }
    }

    private fun encodeRemainingLength(length: Int, out: OutputStream) {
        var x = length
        do {
            var encodedByte = (x % 128)
            x /= 128
            if (x > 0) {
                encodedByte = encodedByte or 0x80
            }
            out.write(encodedByte)
        } while (x > 0)
    }

    private fun readRemainingLength(input: InputStream): Int {
        var multiplier = 1
        var value = 0
        do {
            val encodedByte = input.read()
            if (encodedByte == -1) throw EOFException("End of stream while reading MQTT length")
            value += (encodedByte and 0x7F) * multiplier
            multiplier *= 128
            if (multiplier > 128 * 128 * 128) {
                throw IOException("Malformed remaining length in MQTT packet")
            }
        } while ((encodedByte and 0x80) != 0)
        return value
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            val count = input.read(buffer, bytesRead, buffer.size - bytesRead)
            if (count == -1) throw EOFException("Stream closed before reading expected ${buffer.size} bytes (got $bytesRead)")
            bytesRead += count
        }
    }

    override fun close() {
        isRunning.set(false)
        stopPingThread()
        val out = outputStream
        if (isConnected.get() && out != null) {
            try {
                synchronized(out) {
                    out.write(0xE0) // DISCONNECT
                    out.write(0x00)
                    out.flush()
                }
            } catch (_: Exception) {}
        }
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
        networkThread?.interrupt()
    }
}

/**
 * Pure Kotlin RFC-4648 Base64 utility for universal JVM and Android compatibility.
 */
object Base64Util {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) {
            this[ALPHABET[i].code] = i
        }
    }

    fun encode(data: ByteArray): String {
        val sb = java.lang.StringBuilder((data.size * 4 + 2) / 3)
        var i = 0
        while (i < data.size) {
            val b0 = data[i++].toInt() and 0xFF
            val b1 = if (i < data.size) data[i++].toInt() and 0xFF else -1
            val b2 = if (i < data.size) data[i++].toInt() and 0xFF else -1

            val c0 = b0 ushr 2
            val c1 = ((b0 and 0x03) shl 4) or (if (b1 >= 0) (b1 ushr 4) else 0)
            val c2 = if (b1 >= 0) (((b1 and 0x0F) shl 2) or (if (b2 >= 0) (b2 ushr 6) else 0)) else -1
            val c3 = if (b2 >= 0) (b2 and 0x3F) else -1

            sb.append(ALPHABET[c0])
            sb.append(ALPHABET[c1])
            sb.append(if (c2 >= 0) ALPHABET[c2] else '=')
            sb.append(if (c3 >= 0) ALPHABET[c3] else '=')
        }
        return sb.toString()
    }

    fun decode(str: String): ByteArray {
        val clean = str.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' }
        val out = ByteArrayOutputStream(clean.length * 3 / 4)
        var i = 0
        while (i < clean.length) {
            val c0 = DECODE_TABLE[clean[i++].code]
            val c1 = if (i < clean.length) DECODE_TABLE[clean[i++].code] else 0
            val c2 = if (i < clean.length) DECODE_TABLE[clean[i++].code] else -1
            val c3 = if (i < clean.length) DECODE_TABLE[clean[i++].code] else -1

            val b0 = (c0 shl 2) or (c1 ushr 4)
            out.write(b0)
            if (c2 >= 0) {
                val b1 = ((c1 and 0x0F) shl 4) or (c2 ushr 2)
                out.write(b1)
                if (c3 >= 0) {
                    val b2 = ((c2 and 0x03) shl 6) or c3
                    out.write(b2)
                }
            }
        }
        return out.toByteArray()
    }
}

/**
 * High-performance GZIP compressor for farm world states.
 * Reduces 15KB-20KB world snapshots to ~1.5KB-2.5KB for ultra-low latency mobile sync.
 */
object PayloadCompressor {
    fun compress(rawBytes: ByteArray): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(rawBytes) }
        return Base64Util.encode(bos.toByteArray())
    }

    fun decompress(payloadStr: String): ByteArray {
        val trimmed = payloadStr.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed.toByteArray(Charsets.UTF_8)
        }
        return try {
            val bytes = Base64Util.decode(trimmed)
            if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                val bis = ByteArrayInputStream(bytes)
                val bos = ByteArrayOutputStream()
                GZIPInputStream(bis).use { it.copyTo(bos) }
                bos.toByteArray()
            } else {
                bytes
            }
        } catch (_: Exception) {
            payloadStr.toByteArray(Charsets.UTF_8)
        }
    }
}
