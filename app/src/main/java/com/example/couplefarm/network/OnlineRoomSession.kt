package com.example.couplefarm.network

import android.util.Log
import com.example.couplefarm.game.FarmWorldState
import com.example.couplefarm.game.createDefaultFarmWorld
import com.example.couplefarm.persistence.FarmWorldJsonCodec
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "OnlineRoomSession"

sealed class OnlineSessionStatus {
    object Idle : OnlineSessionStatus()
    data class Connecting(val message: String) : OnlineSessionStatus()
    data class WaitingForGuest(val roomCode: String) : OnlineSessionStatus()
    data class Connected(val peerName: String, val roomCode: String) : OnlineSessionStatus()
    data class Error(val message: String) : OnlineSessionStatus()
    data class Disconnected(val reason: String) : OnlineSessionStatus()
}

fun normalizeRoomCode(code: String): String = code.trim().filter { it.isDigit() }.take(4)

/**
 * Host session running on the farm owner's phone.
 * All game data is stored locally on the host's device.
 */
class OnlineHostSession(
    val roomCode: String,
    private val hostName: String = "Anfitrión",
    private val getWorld: () -> FarmWorldState,
    private val onGuestJoined: (name: String) -> Unit,
    private val onGuestLeft: () -> Unit,
    private val onGuestMove: (x: Float, y: Float, facing: String, dirX: Float, dirY: Float, isMoving: Boolean, tool: String, activeItem: String?, isMounted: Boolean) -> Unit,
    private val onGuestAction: (tool: String, targetX: Float, targetY: Float) -> Unit,
    private val onGuestEmote: (emote: String) -> Unit,
    private val onStatusChanged: (OnlineSessionStatus) -> Unit = {},
) : AutoCloseable {

    private val isRunning = AtomicBoolean(true)
    private val hostTopic = "couplefarm/v1/room/$roomCode/host"
    private val guestTopic = "couplefarm/v1/room/$roomCode/guest"

    @Volatile
    var guestName: String? = null
        private set

    val isGuestConnected: Boolean
        get() = guestName != null

    private var syncThread: Thread? = null

    private val client = OnlineMqttClient(
        clientId = "cf_host_${roomCode}_${UUID.randomUUID().toString().take(6)}",
        onStateChanged = { state ->
            when (state) {
                is OnlineMqttState.Connecting -> {
                    onStatusChanged(OnlineSessionStatus.Connecting("Conectando con la nube..."))
                }
                is OnlineMqttState.Connected -> {
                    if (guestName == null) {
                        onStatusChanged(OnlineSessionStatus.WaitingForGuest(roomCode))
                    }
                }
                is OnlineMqttState.Error -> {
                    if (guestName == null) {
                        onStatusChanged(OnlineSessionStatus.Error(state.message))
                    }
                }
                is OnlineMqttState.Disconnected -> {
                    if (isRunning.get()) {
                        onStatusChanged(OnlineSessionStatus.Disconnected("Desconectado de la red"))
                    }
                }
            }
        },
        onMessageReceived = { topic, payloadBytes ->
            if (topic == guestTopic) {
                handleGuestMessage(payloadBytes)
            }
        }
    )

    fun start() {
        client.connect()
        client.subscribe(guestTopic)
        onStatusChanged(OnlineSessionStatus.WaitingForGuest(roomCode))

        // Periodic sync sender thread (~10 updates per second)
        syncThread = Thread({
            while (isRunning.get()) {
                try {
                    Thread.sleep(100) // 100ms = 10 updates/sec
                    if (guestName != null && isRunning.get()) {
                        val currentWorld = getWorld()
                        val rawBytes = FarmWorldJsonCodec.encode(currentWorld, System.currentTimeMillis())
                        val compressedPayload = PayloadCompressor.compress(rawBytes)
                        val msg = JSONObject().apply {
                            put("type", "SERVER_SYNC")
                            put("payload", compressedPayload)
                        }
                        client.publish(hostTopic, msg.toString().toByteArray(Charsets.UTF_8))
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Sync send error: ${e.message}")
                }
            }
        }, "OnlineHost-SyncThread").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleGuestMessage(payloadBytes: ByteArray) {
        try {
            val jsonStr = String(payloadBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            when (json.optString("type")) {
                "HELLO" -> {
                    val name = json.optString("farmerName", "Invitado")
                    guestName = name
                    onGuestJoined(name)
                    onStatusChanged(OnlineSessionStatus.Connected(name, roomCode))

                    // Immediately reply with initial world snapshot compressed
                    val world = getWorld()
                    val rawBytes = FarmWorldJsonCodec.encode(world, System.currentTimeMillis())
                    val compressedPayload = PayloadCompressor.compress(rawBytes)
                    val welcome = JSONObject().apply {
                        put("type", "INIT_WORLD")
                        put("hostName", hostName)
                        put("payload", compressedPayload)
                    }
                    client.publish(hostTopic, welcome.toString().toByteArray(Charsets.UTF_8))
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
                    val name = guestName ?: "Tu amigo"
                    guestName = null
                    onGuestLeft()
                    onStatusChanged(OnlineSessionStatus.WaitingForGuest(roomCode))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling guest message: ${e.message}")
        }
    }

    override fun close() {
        if (!isRunning.getAndSet(false)) return
        syncThread?.interrupt()
        try {
            val bye = JSONObject().put("type", "DISCONNECT").toString().toByteArray(Charsets.UTF_8)
            client.publish(hostTopic, bye)
        } catch (_: Exception) {}
        client.close()
        onStatusChanged(OnlineSessionStatus.Disconnected("Sala cerrada"))
    }
}

/**
 * Guest session running on the joining player's phone.
 * Connects to the host using the 4-digit code.
 */
class OnlineGuestSession(
    val roomCode: String,
    private val clientFarmerName: String = "Invitado",
    private val onStatusChanged: (OnlineSessionStatus) -> Unit,
    private val onWorldReceived: (FarmWorldState) -> Unit,
) : AutoCloseable {

    private val isRunning = AtomicBoolean(true)
    private val hostTopic = "couplefarm/v1/room/$roomCode/host"
    private val guestTopic = "couplefarm/v1/room/$roomCode/guest"

    @Volatile
    private var currentWorld: FarmWorldState = createDefaultFarmWorld()
    @Volatile
    private var hasReceivedInitWorld = false
    private var helloThread: Thread? = null

    private val client = OnlineMqttClient(
        clientId = "cf_guest_${roomCode}_${UUID.randomUUID().toString().take(6)}",
        onStateChanged = { state ->
            when (state) {
                is OnlineMqttState.Connecting -> {
                    onStatusChanged(OnlineSessionStatus.Connecting("Conectando con la sala $roomCode..."))
                }
                is OnlineMqttState.Connected -> {
                    if (!hasReceivedInitWorld) {
                        onStatusChanged(OnlineSessionStatus.Connecting("Buscando anfitrión..."))
                        startHelloHandshake()
                    }
                }
                is OnlineMqttState.Error -> {
                    if (!hasReceivedInitWorld) {
                        onStatusChanged(OnlineSessionStatus.Error(state.message))
                    }
                }
                is OnlineMqttState.Disconnected -> {
                    if (isRunning.get()) {
                        onStatusChanged(OnlineSessionStatus.Disconnected("Desconectado de la sala"))
                    }
                }
            }
        },
        onMessageReceived = { topic, payloadBytes ->
            if (topic == hostTopic) {
                handleHostMessage(payloadBytes)
            }
        }
    )

    fun connect() {
        onStatusChanged(OnlineSessionStatus.Connecting("Buscando sala $roomCode..."))
        client.connect()
        client.subscribe(hostTopic)
    }

    private fun startHelloHandshake() {
        helloThread?.interrupt()
        helloThread = Thread({
            var attempts = 0
            while (isRunning.get() && !hasReceivedInitWorld && attempts < 10) {
                try {
                    val hello = JSONObject().apply {
                        put("type", "HELLO")
                        put("farmerName", clientFarmerName)
                    }
                    client.publish(guestTopic, hello.toString().toByteArray(Charsets.UTF_8))
                    attempts++
                    Thread.sleep(1500)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Hello handshake error: ${e.message}")
                }
            }
            if (!hasReceivedInitWorld && isRunning.get()) {
                onStatusChanged(OnlineSessionStatus.Error("No se encontró al anfitrión en la sala $roomCode. Verifica que tu amigo tenga la granja abierta con este código."))
            }
        }, "OnlineGuest-HelloHandshake").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleHostMessage(payloadBytes: ByteArray) {
        try {
            val jsonStr = String(payloadBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            when (json.optString("type")) {
                "INIT_WORLD" -> {
                    hasReceivedInitWorld = true
                    helloThread?.interrupt()
                    val hostName = json.optString("hostName", "Anfitrión")
                    val rawCompressed = json.optString("payload")
                    val decompressedBytes = PayloadCompressor.decompress(rawCompressed)
                    val decoded = FarmWorldJsonCodec.decode(decompressedBytes, currentWorld)
                    currentWorld = decoded.state
                    onStatusChanged(OnlineSessionStatus.Connected(hostName, roomCode))
                    onWorldReceived(decoded.state)
                }
                "SERVER_SYNC" -> {
                    if (!hasReceivedInitWorld) {
                        hasReceivedInitWorld = true
                        helloThread?.interrupt()
                    }
                    val rawCompressed = json.optString("payload")
                    val decompressedBytes = PayloadCompressor.decompress(rawCompressed)
                    val decoded = FarmWorldJsonCodec.decode(decompressedBytes, currentWorld)
                    currentWorld = decoded.state
                    onWorldReceived(decoded.state)
                }
                "DISCONNECT" -> {
                    onStatusChanged(OnlineSessionStatus.Disconnected("El anfitrión ha cerrado la sala"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling host message: ${e.message}")
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
            client.publish(guestTopic, json.toString().toByteArray(Charsets.UTF_8))
        } catch (_: Exception) {}
    }

    fun sendAction(tool: String, targetX: Float, targetY: Float) {
        val json = JSONObject().apply {
            put("type", "CLIENT_ACTION")
            put("tool", tool)
            put("targetX", targetX.toDouble())
            put("targetY", targetY.toDouble())
        }
        try {
            client.publish(guestTopic, json.toString().toByteArray(Charsets.UTF_8))
        } catch (_: Exception) {}
    }

    fun sendEmote(emote: String) {
        val json = JSONObject().apply {
            put("type", "CLIENT_EMOTE")
            put("emote", emote)
        }
        try {
            client.publish(guestTopic, json.toString().toByteArray(Charsets.UTF_8))
        } catch (_: Exception) {}
    }

    override fun close() {
        if (!isRunning.getAndSet(false)) return
        helloThread?.interrupt()
        try {
            val bye = JSONObject().put("type", "DISCONNECT").toString().toByteArray(Charsets.UTF_8)
            client.publish(guestTopic, bye)
        } catch (_: Exception) {}
        client.close()
    }
}
