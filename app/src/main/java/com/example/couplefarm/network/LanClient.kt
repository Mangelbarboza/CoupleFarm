package com.example.couplefarm.network

import android.util.Log
import com.example.couplefarm.game.FarmWorldState
import com.example.couplefarm.game.createDefaultFarmWorld
import com.example.couplefarm.persistence.FarmWorldJsonCodec
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LanClient"

sealed class LanClientStatus {
    object Connecting : LanClientStatus()
    data class Connected(val hostName: String) : LanClientStatus()
    data class Error(val message: String) : LanClientStatus()
    data class Disconnected(val reason: String) : LanClientStatus()
}

class LanClient(
    private val hostAddress: String,
    private val port: Int = DEFAULT_LAN_PORT,
    private val clientFarmerName: String = "Invitado",
    private val onStatusChanged: (LanClientStatus) -> Unit,
    private val onWorldReceived: (FarmWorldState) -> Unit,
) : AutoCloseable {
    private val isRunning = AtomicBoolean(true)
    private var socket: Socket? = null
    private var dataOut: DataOutputStream? = null
    private var workerThread: Thread? = null

    fun connect() {
        onStatusChanged(LanClientStatus.Connecting)
        workerThread = Thread({
            try {
                val s = Socket()
                socket = s
                s.connect(InetSocketAddress(hostAddress, port), 4000)
                val output = DataOutputStream(s.getOutputStream())
                dataOut = output
                val input = DataInputStream(s.getInputStream())

                // Send HELLO
                val hello = JSONObject().apply {
                    put("type", "HELLO")
                    put("farmerName", clientFarmerName)
                }
                sendJson(output, hello)

                // Read INIT_WORLD first
                val initLen = input.readInt()
                val initBytes = ByteArray(initLen)
                input.readFully(initBytes)
                val initJson = JSONObject(String(initBytes, Charsets.UTF_8))

                var currentWorld: FarmWorldState = createDefaultFarmWorld()
                if (initJson.optString("type") == "INIT_WORLD") {
                    val hostName = initJson.optString("hostName", "Anfitrión")
                    val payload = initJson.optString("payload")
                    val decoded = FarmWorldJsonCodec.decode(payload.toByteArray(Charsets.UTF_8), currentWorld)
                    if (decoded != null) {
                        currentWorld = decoded.state
                        onStatusChanged(LanClientStatus.Connected(hostName))
                        onWorldReceived(decoded.state)
                    } else {
                        onStatusChanged(LanClientStatus.Error("Error al sincronizar mundo"))
                        return@Thread
                    }
                }

                // Loop reading continuous syncs
                while (isRunning.get() && s.isConnected && !s.isClosed) {
                    val len = input.readInt()
                    if (len <= 0 || len > 2_000_000) break
                    val bytes = ByteArray(len)
                    input.readFully(bytes)
                    val json = JSONObject(String(bytes, Charsets.UTF_8))

                    if (json.optString("type") == "SERVER_SYNC") {
                        val payload = json.optString("payload")
                        val decoded = FarmWorldJsonCodec.decode(payload.toByteArray(Charsets.UTF_8), currentWorld)
                        if (decoded != null) {
                            currentWorld = decoded.state
                            onWorldReceived(decoded.state)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Connection error: ${e.message}")
                    onStatusChanged(LanClientStatus.Error("No se pudo conectar: ${e.localizedMessage ?: "Error de red"}"))
                }
            } finally {
                if (isRunning.get()) {
                    onStatusChanged(LanClientStatus.Disconnected("Conexión finalizada"))
                }
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }, "LanClient-Worker").apply {
            isDaemon = true
            start()
        }
    }

    fun sendMove(
        x: Float,
        y: Float,
        facing: String,
        dirX: Float,
        dirY: Float,
        isMoving: Boolean,
        tool: String,
        activeItem: String?,
        isMounted: Boolean,
    ) {
        val out = dataOut ?: return
        val json = JSONObject().apply {
            put("type", "CLIENT_MOVE")
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("facing", facing)
            put("dirX", dirX.toDouble())
            put("dirY", dirY.toDouble())
            put("isMoving", isMoving)
            put("tool", tool)
            put("activeItem", activeItem ?: "")
            put("isMounted", isMounted)
        }
        try {
            sendJson(out, json)
        } catch (_: Exception) {}
    }

    fun sendAction(tool: String, targetX: Float, targetY: Float) {
        val out = dataOut ?: return
        val json = JSONObject().apply {
            put("type", "CLIENT_ACTION")
            put("tool", tool)
            put("targetX", targetX.toDouble())
            put("targetY", targetY.toDouble())
        }
        try {
            sendJson(out, json)
        } catch (_: Exception) {}
    }

    fun sendEmote(emote: String) {
        val out = dataOut ?: return
        val json = JSONObject().apply {
            put("type", "CLIENT_EMOTE")
            put("emote", emote)
        }
        try {
            sendJson(out, json)
        } catch (_: Exception) {}
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
            val out = dataOut
            if (out != null) {
                val bye = JSONObject().put("type", "DISCONNECT")
                sendJson(out, bye)
            }
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        dataOut = null
        workerThread?.interrupt()
    }
}
