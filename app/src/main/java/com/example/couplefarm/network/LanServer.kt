package com.example.couplefarm.network

import android.util.Log
import com.example.couplefarm.game.FarmWorldState
import com.example.couplefarm.persistence.FarmWorldJsonCodec
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LanServer"

class LanServer(
    private val port: Int = DEFAULT_LAN_PORT,
    private val hostName: String = "Anfitrión",
    private val getWorld: () -> FarmWorldState,
    private val onGuestJoined: (name: String) -> Unit,
    private val onGuestLeft: () -> Unit,
    private val onGuestMove: (x: Float, y: Float, facing: String, dirX: Float, dirY: Float, isMoving: Boolean, tool: String, activeItem: String?, isMounted: Boolean) -> Unit,
    private val onGuestAction: (tool: String, targetX: Float, targetY: Float) -> Unit,
    private val onGuestEmote: (emote: String) -> Unit,
) : AutoCloseable {
    private val isRunning = AtomicBoolean(true)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var syncThread: Thread? = null

    @Volatile
    private var activeClient: Socket? = null
    @Volatile
    private var clientOut: DataOutputStream? = null

    @Volatile
    var guestName: String? = null
        private set

    val isGuestConnected: Boolean
        get() = activeClient != null && activeClient?.isConnected == true && !activeClient!!.isClosed

    fun start() {
        acceptThread = Thread({
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "LanServer started on port $port")

                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    Log.i(TAG, "Client connected from ${client.inetAddress.hostAddress}")

                    // Close existing client if any
                    try {
                        activeClient?.close()
                    } catch (_: Exception) {}

                    activeClient = client
                    val output = DataOutputStream(client.getOutputStream())
                    clientOut = output
                    val input = DataInputStream(client.getInputStream())

                    // Start client listener thread
                    Thread({
                        handleClient(client, input, output)
                    }, "LanServer-ClientHandler").start()
                }
            } catch (_: java.net.SocketException) {
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "Server loop error: ${e.message}")
            }
        }, "LanServer-AcceptThread").apply {
            isDaemon = true
            start()
        }

        // Periodic sync sender thread (approx 16 frames per second)
        syncThread = Thread({
            while (isRunning.get()) {
                try {
                    Thread.sleep(60)
                    val out = clientOut
                    val client = activeClient
                    if (out != null && client != null && client.isConnected && !client.isClosed) {
                        val currentWorld = getWorld()
                        val worldBytes = FarmWorldJsonCodec.encode(currentWorld, System.currentTimeMillis())
                        val msg = JSONObject().apply {
                            put("type", "SERVER_SYNC")
                            put("payload", String(worldBytes, Charsets.UTF_8))
                        }
                        sendJson(out, msg)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Sync send error: ${e.message}")
                }
            }
        }, "LanServer-SyncThread").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleClient(client: Socket, input: DataInputStream, output: DataOutputStream) {
        try {
            while (isRunning.get() && client.isConnected && !client.isClosed) {
                val length = input.readInt()
                if (length <= 0 || length > 1_000_000) break
                val bytes = ByteArray(length)
                input.readFully(bytes)
                val jsonStr = String(bytes, Charsets.UTF_8)
                val json = JSONObject(jsonStr)

                when (json.optString("type")) {
                    "HELLO" -> {
                        val name = json.optString("farmerName", "Amigo")
                        guestName = name
                        onGuestJoined(name)
                        // Send initial full world snapshot
                        val world = getWorld()
                        val worldBytes = FarmWorldJsonCodec.encode(world, System.currentTimeMillis())
                        val welcome = JSONObject().apply {
                            put("type", "INIT_WORLD")
                            put("hostName", hostName)
                            put("payload", String(worldBytes, Charsets.UTF_8))
                        }
                        sendJson(output, welcome)
                    }
                    "CLIENT_MOVE" -> {
                        onGuestMove(
                            json.optDouble("x", 0.0).toFloat(),
                            json.optDouble("y", 0.0).toFloat(),
                            json.optString("facing", "DOWN"),
                            json.optDouble("dirX", 0.0).toFloat(),
                            json.optDouble("dirY", 1.0).toFloat(),
                            json.optBoolean("isMoving", false),
                            json.optString("tool", "HANDS"),
                            json.optString("activeItem").takeIf { it.isNotBlank() },
                            json.optBoolean("isMounted", false),
                        )
                    }
                    "CLIENT_ACTION" -> {
                        onGuestAction(
                            json.optString("tool", "HANDS"),
                            json.optDouble("targetX", 0.0).toFloat(),
                            json.optDouble("targetY", 0.0).toFloat(),
                        )
                    }
                    "CLIENT_EMOTE" -> {
                        onGuestEmote(json.optString("emote", "HEART"))
                    }
                    "DISCONNECT" -> {
                        break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            Log.i(TAG, "Client disconnected")
            if (activeClient == client) {
                activeClient = null
                clientOut = null
                guestName = null
                onGuestLeft()
            }
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun sendJson(out: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        synchronized(out) {
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
        }
    }

    override fun close() {
        isRunning.set(false)
        try {
            activeClient?.close()
        } catch (_: Exception) {}
        activeClient = null
        clientOut = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        acceptThread?.interrupt()
        syncThread?.interrupt()
    }
}
