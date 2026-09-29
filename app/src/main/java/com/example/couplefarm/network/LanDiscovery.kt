package com.example.couplefarm.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LanDiscovery"
const val LAN_DISCOVERY_PORT = 8889
const val DEFAULT_LAN_PORT = 8888
private const val PREFIX_BEACON = "COUPLE_FARM:BEACON:"
private const val PREFIX_OFFER = "COUPLE_FARM:OFFER:"
private const val QUERY_DISCOVER = "COUPLE_FARM:DISCOVER"

data class DiscoveredFarm(
    val name: String,
    val hostAddress: String,
    val port: Int,
    val day: Int,
    val lastSeenMs: Long = System.currentTimeMillis(),
)

/**
 * Returns the device's current non-loopback IPv4 address on the active local network (e.g. Wi-Fi).
 */
fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (intf in interfaces) {
            if (intf.isLoopback || !intf.isUp) continue
            val addrs = intf.inetAddresses ?: continue
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                        return host
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to get local IP: " + e.message)
    }
    return null
}

/**
 * Runs on the Host phone to broadcast availability on the local Wi-Fi network.
 */
class LanBeacon(
    private val farmName: String,
    private val port: Int = DEFAULT_LAN_PORT,
    private val getDay: () -> Int = { 1 },
) : AutoCloseable {
    private val isRunning = AtomicBoolean(true)
    private var socket: DatagramSocket? = null
    private var broadcastThread: Thread? = null

    fun start() {
        broadcastThread = Thread({
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                }
                socket = s
                try {
                    s.bind(java.net.InetSocketAddress(LAN_DISCOVERY_PORT))
                } catch (_: Exception) {
                    socket = DatagramSocket().apply { broadcast = true }
                }

                val broadcastAddr = InetAddress.getByName("255.255.255.255")

                while (isRunning.get()) {
                    val cleanName = farmName.replace(":", "_").ifBlank { "Granja" }
                    val message = "$PREFIX_BEACON$cleanName:$port:${getDay()}"
                    val bytes = message.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, LAN_DISCOVERY_PORT)

                    try {
                        socket?.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Broadcast error: " + e.message)
                    }

                    Thread.sleep(1500)
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                Log.e(TAG, "LanBeacon failed: " + e.message)
            } finally {
                socket?.close()
            }
        }, "LanBeacon-Thread").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        isRunning.set(false)
        broadcastThread?.interrupt()
        socket?.close()
    }
}

/**
 * Runs on the Client phone to discover running farm hosts on the local network.
 */
class LanScanner(
    private val onFarmsUpdated: (List<DiscoveredFarm>) -> Unit,
) : AutoCloseable {
    private val isRunning = AtomicBoolean(true)
    private var socket: DatagramSocket? = null
    private var workerThread: Thread? = null
    private val discoveredFarms = ConcurrentHashMap<String, DiscoveredFarm>()

    fun start() {
        workerThread = Thread({
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                }
                socket = s
                try {
                    s.bind(java.net.InetSocketAddress(LAN_DISCOVERY_PORT))
                } catch (_: Exception) {
                    socket = DatagramSocket().apply { broadcast = true }
                }

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                val broadcastAddr = InetAddress.getByName("255.255.255.255")

                var lastPingTime = 0L

                while (isRunning.get()) {
                    val now = System.currentTimeMillis()
                    if (now - lastPingTime > 2000L) {
                        lastPingTime = now
                        val pingBytes = QUERY_DISCOVER.toByteArray(Charsets.UTF_8)
                        val pingPacket = DatagramPacket(pingBytes, pingBytes.size, broadcastAddr, LAN_DISCOVERY_PORT)
                        try {
                            socket?.send(pingPacket)
                        } catch (_: Exception) {}
                    }

                    socket?.soTimeout = 1000
                    try {
                        socket?.receive(packet)
                        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                        val senderIp = packet.address.hostAddress ?: ""

                        if (text.startsWith(PREFIX_BEACON) || text.startsWith(PREFIX_OFFER)) {
                            val prefix = if (text.startsWith(PREFIX_BEACON)) PREFIX_BEACON else PREFIX_OFFER
                            val parts = text.substring(prefix.length).split(":")
                            if (parts.isNotEmpty()) {
                                val name = parts[0]
                                val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_LAN_PORT
                                val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
                                val key = "$senderIp:$port"
                                discoveredFarms[key] = DiscoveredFarm(
                                    name = name,
                                    hostAddress = senderIp,
                                    port = port,
                                    day = day,
                                    lastSeenMs = now,
                                )
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                        Log.w(TAG, "Receive error: " + e.message)
                    }

                    val cutoff = System.currentTimeMillis() - 5000L
                    discoveredFarms.entries.removeIf { it.value.lastSeenMs < cutoff }
                    onFarmsUpdated(discoveredFarms.values.toList().sortedByDescending { it.lastSeenMs })
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                Log.e(TAG, "LanScanner failed: " + e.message)
            } finally {
                socket?.close()
            }
        }, "LanScanner-Thread").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        isRunning.set(false)
        workerThread?.interrupt()
        socket?.close()
    }
}
