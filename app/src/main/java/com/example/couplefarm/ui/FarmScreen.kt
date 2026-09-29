package com.example.couplefarm.ui

import com.example.couplefarm.network.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.window.Dialog
import com.example.couplefarm.game.FactoryBuildingState
import com.example.couplefarm.game.FactoryType
import com.example.couplefarm.game.ConstructionPhase
import com.example.couplefarm.game.SoilState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.couplefarm.audio.FarmPickup
import com.example.couplefarm.audio.FarmSoundManager
import com.example.couplefarm.audio.FarmSurface
import com.example.couplefarm.game.FarmWorldEngine
import com.example.couplefarm.game.Facing
import com.example.couplefarm.game.AnimalLifeStage
import com.example.couplefarm.game.ChickenLifeStage
import com.example.couplefarm.game.AnimalHandlingState
import com.example.couplefarm.game.AnimalPurchaseRequest
import com.example.couplefarm.game.AnimalReference
import com.example.couplefarm.game.AnimalRenameRequest
import com.example.couplefarm.game.DomesticAnimalType
import com.example.couplefarm.game.FoxPhase
import com.example.couplefarm.game.PetCommandRequest
import com.example.couplefarm.game.PetCommandType
import com.example.couplefarm.game.FarmWorldState
import com.example.couplefarm.game.PlayerState
import com.example.couplefarm.game.EmoteType
import com.example.couplefarm.game.FarmerStyle
import com.example.couplefarm.game.FishingPhase
import com.example.couplefarm.game.FishingState
import com.example.couplefarm.game.ItemType
import com.example.couplefarm.game.SoundCue
import com.example.couplefarm.game.ToolType
import com.example.couplefarm.game.TruckPhase
import com.example.couplefarm.game.Vector2
import com.example.couplefarm.game.WorldEvent
import com.example.couplefarm.game.WorldInput
import com.example.couplefarm.ui.game.FarmTool
import com.example.couplefarm.ui.game.FarmEmote
import com.example.couplefarm.ui.game.FarmWorldCanvas
import com.example.couplefarm.ui.game.BakeryInteriorCanvas
import com.example.couplefarm.ui.game.EmotePicker
import com.example.couplefarm.ui.game.GameControls
import com.example.couplefarm.persistence.FarmSaveStore
import kotlinx.coroutines.delay

private val HudCream = Color(0xFFF8E9B8)
private val HudBrown = Color(0xFF513822)
private val HudGreen = Color(0xFF42633A)

private fun moveIndoorPlayer(
    current: Vector2,
    delta: Vector2,
): Vector2 {
    var nx = (current.x + delta.x).coerceIn(2.4f, 13.6f)
    var ny = current.y
    if (isCollidingIndoorProp(nx, ny)) {
        nx = current.x
    }
    val maxY = if (nx in 6.8f..9.2f) 8.5f else 7.8f
    ny = (ny + delta.y).coerceIn(3.8f, maxY)
    if (isCollidingIndoorProp(nx, ny)) {
        ny = current.y
    }
    return Vector2(nx, ny)
}

private fun isCollidingIndoorProp(x: Float, y: Float): Boolean {
    // Left Molcajete Table
    if (x in 1.8f..4.6f && y in 4.2f..5.8f) return true
    // Right Kneading Table
    if (x in 11.4f..14.2f && y in 4.2f..5.8f) return true
    // North wall / ovens counter
    if (y < 3.8f) return true
    return false
}

@Composable
fun FarmScreen(
    farmerStyle: FarmerStyle,
    lanJoinHost: String? = null,
    lanJoinPort: Int = DEFAULT_LAN_PORT,
    onlineRoomCode: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val saveStore = remember { FarmSaveStore(context) }
    val engine = remember { FarmWorldEngine(saveStore.restore()) }
    val sounds = remember { FarmSoundManager(context) }
    var world by remember { mutableStateOf(engine.state) }
    var movement by remember { mutableStateOf(Vector2.ZERO) }
    var selectedTool by remember { mutableStateOf(FarmTool.HAND) }
    var actionPressed by remember { mutableStateOf(false) }
    var actionHeld by remember { mutableStateOf(false) }
    var pendingEmote by remember { mutableStateOf<EmoteType?>(null) }
    var showMap by remember { mutableStateOf(false) }
    var showShop by remember { mutableStateOf(false) }
    var pendingPurchase by remember { mutableStateOf<AnimalPurchaseRequest?>(null) }
    var pendingPetCommand by remember { mutableStateOf<PetCommandRequest?>(null) }
    var pendingBell by remember { mutableStateOf(false) }
    var pendingThrow by remember { mutableStateOf(false) }
    var namingTarget by remember { mutableStateOf<AnimalReference?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var pendingRename by remember { mutableStateOf<AnimalRenameRequest?>(null) }
    var pendingGrab by remember { mutableStateOf(false) }
    var pendingChickenPurchase by remember { mutableStateOf(false) }
    var pendingToolPurchase by remember { mutableStateOf<ToolType?>(null) }
    var pendingPetAction by remember { mutableStateOf(false) }
    var pendingFencePurchase by remember { mutableStateOf(0) }
    var pendingSeedPurchase by remember { mutableStateOf(0) }
    var pendingBikePurchase by remember { mutableStateOf(false) }
    var pendingDebugCoins by remember { mutableStateOf(false) }
    var pendingHireHelperName by remember { mutableStateOf<String?>(null) }
    var pendingRenameHelper by remember { mutableStateOf<String?>(null) }
    var pendingDismissHelper by remember { mutableStateOf(false) }
    var pendingRotateBlueprint by remember { mutableStateOf(false) }
    var pendingTogglePencil by remember { mutableStateOf(false) }
    var pendingPencilTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingBlueprintPurchase by remember { mutableStateOf<FactoryType?>(null) }
    var slaughterFlashAlpha by remember { mutableFloatStateOf(0f) }
    var activeMinigameFactory by remember { mutableStateOf<FactoryBuildingState?>(null) }
    var eggPackerPlacedCount by remember { mutableIntStateOf(0) }
    var veggiePackerPlacedCount by remember { mutableIntStateOf(0) }
    var bakeryStep by remember { mutableIntStateOf(1) }
    var bakeryGrindTaps by remember { mutableIntStateOf(0) }
    var pendingGrindWheat by remember { mutableStateOf(false) }
    var pendingKneadDough by remember { mutableStateOf(false) }
    var pendingStartBakeryOven by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingCollectBakeryOven by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var insideBakeryId by remember { mutableStateOf<Int?>(null) }
    var indoorPlayerPos by remember { mutableStateOf(Vector2(8.0f, 7.5f)) }
    var indoorPlayerFacing by remember { mutableStateOf(Facing.UP) }
    var indoorPlayerMoving by remember { mutableStateOf(false) }
    var indoorPlayerMovementDir by remember { mutableStateOf(Vector2.ZERO) }
    var showMolcajeteMinigame by remember { mutableStateOf(false) }
    var showKneadMinigame by remember { mutableStateOf(false) }
    var showFpsCounter by rememberSaveable { mutableStateOf(false) }
    var currentFps by remember { mutableIntStateOf(60) }
    var pendingCompleteMinigame by remember { mutableStateOf<FactoryType?>(null) }
    var fishProcessorTaps by remember { mutableIntStateOf(0) }
    var showBarnBodega by remember { mutableStateOf(false) }
    var pendingWheatSeedPurchase by remember { mutableIntStateOf(0) }
    var pendingGatePurchase by remember { mutableIntStateOf(0) }
    var pendingNudge by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingConfirmStructure by remember { mutableStateOf(false) }
    var pendingRotateStructure by remember { mutableStateOf(false) }
    var pendingDepositAllToBarn by remember { mutableStateOf(false) }
    var pendingDepositItemToBarn by remember { mutableStateOf<ItemType?>(null) }
    var pendingWithdrawBarnItem by remember { mutableStateOf<ItemType?>(null) }
    var pendingWithdrawBarnItemAmount by remember { mutableIntStateOf(10) }
    var pendingWithdrawBarnEggs by remember { mutableStateOf<Int?>(null) }
    var pendingSelectActiveItem by remember { mutableStateOf<ItemType?>(null) }
    var pendingToggleGateId by remember { mutableStateOf<Int?>(null) }
    var pendingToggleWheelbarrow by remember { mutableStateOf(false) }
    var pendingLoadWheelbarrowItem by remember { mutableStateOf<ItemType?>(null) }
    var pendingUnloadWheelbarrowItem by remember { mutableStateOf<ItemType?>(null) }
    var pendingUnloadWheelbarrowToBarn by remember { mutableStateOf(false) }
    var pendingSellWheelbarrowCargo by remember { mutableStateOf(false) }
    var pendingThrowItem by remember { mutableStateOf(false) }
    var showPets by remember { mutableStateOf(false) }
    var showBackpack by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var soundEffectsEnabled by remember { mutableStateOf(sounds.enabled) }
    var musicEnabled by remember { mutableStateOf(sounds.musicEnabled) }

    val isGuestMode = lanJoinHost != null || onlineRoomCode != null
    val localPlayer = if (isGuestMode) (world.guestPlayer ?: world.player) else world.player

    // LAN Multiplayer states
    var lanHostEnabled by remember { mutableStateOf(false) }
    var lanConnectedFriendName by remember { mutableStateOf<String?>(null) }
    var lanHostIp by remember { mutableStateOf(getLocalIpAddress() ?: "127.0.0.1") }
    var lanClient by remember { mutableStateOf<LanClient?>(null) }
    var lanClientStatus by remember { mutableStateOf<LanClientStatus>(LanClientStatus.Connecting) }

    // Online Multiplayer states (4-digit room code, zero DB)
    var onlineHostEnabled by remember { mutableStateOf(false) }
    var onlineRoomCodeGenerated by remember { mutableStateOf((1000..9999).random().toString()) }
    var onlineConnectedFriendName by remember { mutableStateOf<String?>(null) }
    var onlineHostSession by remember { mutableStateOf<OnlineHostSession?>(null) }
    var onlineGuestSession by remember { mutableStateOf<OnlineGuestSession?>(null) }
    var onlineSessionStatus by remember { mutableStateOf<OnlineSessionStatus>(OnlineSessionStatus.Idle) }

    // Host LAN networking effect
    DisposableEffect(lanHostEnabled) {
        var server: LanServer? = null
        var beacon: LanBeacon? = null
        if (lanHostEnabled && !isGuestMode) {
            lanHostIp = getLocalIpAddress() ?: "127.0.0.1"
            server = LanServer(
                port = DEFAULT_LAN_PORT,
                hostName = farmerStyle.name.ifBlank { "Granja" },
                getWorld = { engine.state },
                onGuestJoined = { name ->
                    lanConnectedFriendName = name
                    alertMessage = "¡$name se unió a tu granja!"
                    val hostPos = engine.state.player.position
                    engine.updateGuestPlayerState(
                        position = hostPos + Vector2(1.2f, 0f),
                        facing = Facing.DOWN,
                        movementDirection = Vector2.DOWN,
                        isMoving = false,
                        name = name,
                    )
                },
                onGuestLeft = {
                    val name = lanConnectedFriendName ?: "Tu amigo"
                    lanConnectedFriendName = null
                    alertMessage = "$name se desconectó"
                    engine.removeGuestPlayer()
                },
                onGuestMove = { x, y, facingStr, dirX, dirY, isMoving, toolStr, activeItemStr, isMounted ->
                    val facing = runCatching { Facing.valueOf(facingStr) }.getOrDefault(Facing.DOWN)
                    val tool = runCatching { ToolType.valueOf(toolStr) }.getOrDefault(ToolType.HANDS)
                    val activeItem = activeItemStr?.let { runCatching { ItemType.valueOf(it) }.getOrNull() }
                    engine.updateGuestPlayerState(
                        position = Vector2(x, y),
                        facing = facing,
                        movementDirection = Vector2(dirX, dirY),
                        isMoving = isMoving,
                        selectedTool = tool,
                        activeItemId = activeItem,
                        isMounted = isMounted,
                    )
                },
                onGuestAction = { toolStr, tx, ty ->
                    val tool = runCatching { ToolType.valueOf(toolStr) }.getOrDefault(ToolType.HANDS)
                    engine.handleGuestAction(tool, Vector2(tx, ty))
                },
                onGuestEmote = { emoteStr ->
                    val emote = runCatching { EmoteType.valueOf(emoteStr) }.getOrNull()
                    if (emote != null) {
                        val currentGuest = engine.state.guestPlayer
                        if (currentGuest != null) {
                            engine.updateGuestPlayerState(
                                position = currentGuest.position,
                                facing = currentGuest.facing,
                                movementDirection = currentGuest.movementDirection,
                                isMoving = currentGuest.isMoving,
                                selectedTool = currentGuest.selectedTool,
                                activeItemId = currentGuest.activeItemId,
                                isMounted = currentGuest.isMounted,
                            )
                        }
                    }
                }
            ).also { it.start() }

            beacon = LanBeacon(
                farmName = farmerStyle.name.ifBlank { "Granja" },
                port = DEFAULT_LAN_PORT,
                getDay = { engine.state.day },
            ).also { it.start() }
        }

        onDispose {
            beacon?.close()
            server?.close()
            if (lanHostEnabled) {
                engine.removeGuestPlayer()
            }
        }
    }

    // Host Online networking effect (4-digit room)
    DisposableEffect(onlineHostEnabled, onlineRoomCodeGenerated) {
        var hostSession: OnlineHostSession? = null
        if (onlineHostEnabled && !isGuestMode) {
            val code = onlineRoomCodeGenerated
            hostSession = OnlineHostSession(
                roomCode = code,
                hostName = farmerStyle.name.ifBlank { "Anfitrión" },
                getWorld = { engine.state },
                onGuestJoined = { name ->
                    onlineConnectedFriendName = name
                    alertMessage = "¡$name se unió a tu sala online!"
                    val hostPos = engine.state.player.position
                    engine.updateGuestPlayerState(
                        position = hostPos + Vector2(1.2f, 0f),
                        facing = Facing.DOWN,
                        movementDirection = Vector2.DOWN,
                        isMoving = false,
                        name = name,
                    )
                },
                onGuestLeft = {
                    val name = onlineConnectedFriendName ?: "Tu amigo"
                    onlineConnectedFriendName = null
                    alertMessage = "$name se desconectó de la sala"
                    engine.removeGuestPlayer()
                },
                onGuestMove = { x, y, facingStr, dirX, dirY, isMoving, toolStr, activeItemStr, isMounted ->
                    val facing = runCatching { Facing.valueOf(facingStr) }.getOrDefault(Facing.DOWN)
                    val tool = runCatching { ToolType.valueOf(toolStr) }.getOrDefault(ToolType.HANDS)
                    val activeItem = activeItemStr?.let { runCatching { ItemType.valueOf(it) }.getOrNull() }
                    engine.updateGuestPlayerState(
                        position = Vector2(x, y),
                        facing = facing,
                        movementDirection = Vector2(dirX, dirY),
                        isMoving = isMoving,
                        selectedTool = tool,
                        activeItemId = activeItem,
                        isMounted = isMounted,
                    )
                },
                onGuestAction = { toolStr, tx, ty ->
                    val tool = runCatching { ToolType.valueOf(toolStr) }.getOrDefault(ToolType.HANDS)
                    engine.handleGuestAction(tool, Vector2(tx, ty))
                },
                onGuestEmote = { emoteStr ->
                    val emote = runCatching { EmoteType.valueOf(emoteStr) }.getOrNull()
                    if (emote != null) {
                        val currentGuest = engine.state.guestPlayer
                        if (currentGuest != null) {
                            engine.updateGuestPlayerState(
                                position = currentGuest.position,
                                facing = currentGuest.facing,
                                movementDirection = currentGuest.movementDirection,
                                isMoving = currentGuest.isMoving,
                                selectedTool = currentGuest.selectedTool,
                                activeItemId = currentGuest.activeItemId,
                                isMounted = currentGuest.isMounted,
                            )
                        }
                    }
                },
                onStatusChanged = { status ->
                    onlineSessionStatus = status
                }
            ).also { it.start() }
            onlineHostSession = hostSession
        }

        onDispose {
            hostSession?.close()
            onlineHostSession = null
            if (onlineHostEnabled) {
                engine.removeGuestPlayer()
            }
        }
    }

    // Guest LAN networking effect
    DisposableEffect(lanJoinHost) {
        if (lanJoinHost != null) {
            val client = LanClient(
                hostAddress = lanJoinHost,
                port = lanJoinPort,
                clientFarmerName = farmerStyle.name.ifBlank { "Invitado" },
                onStatusChanged = { status ->
                    lanClientStatus = status
                    if (status is LanClientStatus.Connected) {
                        alertMessage = "¡Conectado a la granja de ${status.hostName}!"
                    } else if (status is LanClientStatus.Disconnected) {
                        alertMessage = "Desconectado de la granja"
                    }
                },
                onWorldReceived = { syncedWorld ->
                    val localGuest = world.guestPlayer
                    val incomingGuest = syncedWorld.guestPlayer
                    val finalGuest = if (localGuest != null && incomingGuest != null) {
                        incomingGuest.copy(
                            position = localGuest.position,
                            facing = localGuest.facing,
                            isMoving = localGuest.isMoving,
                            movementDirection = localGuest.movementDirection,
                            activeItemId = localGuest.activeItemId ?: incomingGuest.activeItemId,
                            selectedTool = localGuest.selectedTool,
                        )
                    } else {
                        incomingGuest ?: localGuest
                    }
                    world = syncedWorld.copy(guestPlayer = finalGuest)
                }
            )
            lanClient = client
            client.connect()
            onDispose {
                client.close()
            }
        } else {
            onDispose {}
        }
    }

    // Guest Online networking effect (4-digit room)
    DisposableEffect(onlineRoomCode) {
        if (onlineRoomCode != null) {
            val session = OnlineGuestSession(
                roomCode = onlineRoomCode,
                clientFarmerName = farmerStyle.name.ifBlank { "Invitado" },
                onStatusChanged = { status ->
                    onlineSessionStatus = status
                    when (status) {
                        is OnlineSessionStatus.Connected -> {
                            alertMessage = "¡Conectado a la sala de ${status.peerName}!"
                        }
                        is OnlineSessionStatus.Disconnected -> {
                            alertMessage = "Desconectado: ${status.reason}"
                        }
                        is OnlineSessionStatus.Error -> {
                            alertMessage = status.message
                        }
                        else -> {}
                    }
                },
                onWorldReceived = { syncedWorld ->
                    val localGuest = world.guestPlayer
                    val incomingGuest = syncedWorld.guestPlayer
                    val finalGuest = if (localGuest != null && incomingGuest != null) {
                        incomingGuest.copy(
                            position = localGuest.position,
                            facing = localGuest.facing,
                            isMoving = localGuest.isMoving,
                            movementDirection = localGuest.movementDirection,
                            activeItemId = localGuest.activeItemId ?: incomingGuest.activeItemId,
                            selectedTool = localGuest.selectedTool,
                        )
                    } else {
                        incomingGuest ?: localGuest
                    }
                    world = syncedWorld.copy(guestPlayer = finalGuest)
                }
            )
            onlineGuestSession = session
            session.connect()
            onDispose {
                session.close()
                onlineGuestSession = null
            }
        } else {
            onDispose {}
        }
    }

    DisposableEffect(sounds, saveStore) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        lifecycle?.let(saveStore::attach)
        val audioObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> sounds.resumeAll()
                Lifecycle.Event.ON_STOP -> sounds.pauseAll()
                else -> Unit
            }
        }
        lifecycle?.addObserver(audioObserver)
        onDispose {
            lifecycle?.removeObserver(audioObserver)
            saveStore.track(engine.state)
            saveStore.close()
            sounds.close()
        }
    }

    LaunchedEffect(engine) {
        var previous = withFrameNanos { it }
        var fpsTimer = 0f
        var fpsFrameCount = 0
        while (true) {
            val now = withFrameNanos { it }
            val delta = ((now - previous) / 1_000_000_000f).coerceIn(0f, .05f)
            previous = now
            fpsFrameCount++
            fpsTimer += delta
            if (fpsTimer >= 0.5f) {
                currentFps = (fpsFrameCount / fpsTimer).toInt().coerceIn(1, 240)
                fpsFrameCount = 0
                fpsTimer = 0f
            }
            val pressed = actionPressed
            actionPressed = false
            val emote = pendingEmote
            pendingEmote = null
            val purchase = pendingPurchase.also { pendingPurchase = null }
            val petCommand = pendingPetCommand.also { pendingPetCommand = null }
            val bell = pendingBell.also { pendingBell = false }
            val throwAnimal = pendingThrow.also { pendingThrow = false }
            val rename = pendingRename.also { pendingRename = null }
            val grab = pendingGrab.also { pendingGrab = false }
            val fences = pendingFencePurchase.also { pendingFencePurchase = 0 }
            val seeds = pendingSeedPurchase.also { pendingSeedPurchase = 0 }
            val wheatSeeds = pendingWheatSeedPurchase.also { pendingWheatSeedPurchase = 0 }
            val gates = pendingGatePurchase.also { pendingGatePurchase = 0 }
            val buyBike = pendingBikePurchase.also { pendingBikePurchase = false }
            val buyChicken = pendingChickenPurchase.also { pendingChickenPurchase = false }
            val buyTool = pendingToolPurchase.also { pendingToolPurchase = null }
            val petAction = pendingPetAction.also { pendingPetAction = false }
            val debugCoins = pendingDebugCoins.also { pendingDebugCoins = false }
            val hireHelper = pendingHireHelperName.also { pendingHireHelperName = null }
            val renameHelper = pendingRenameHelper.also { pendingRenameHelper = null }
            val dismissHelper = pendingDismissHelper.also { pendingDismissHelper = false }
            val rotateBp = pendingRotateBlueprint.also { pendingRotateBlueprint = false }
            val togglePencil = pendingTogglePencil.also { pendingTogglePencil = false }
            val pencilTarget = pendingPencilTarget.also { pendingPencilTarget = null }
            val buyBlueprint = pendingBlueprintPurchase.also { pendingBlueprintPurchase = null }
            val completeMinigameReq = pendingCompleteMinigame.also { pendingCompleteMinigame = null }
            val nudge = pendingNudge.also { pendingNudge = null }
            val confirmStructure = pendingConfirmStructure.also { pendingConfirmStructure = false }
            val rotateStructure = pendingRotateStructure.also { pendingRotateStructure = false }
            val depositAllBarn = pendingDepositAllToBarn.also { pendingDepositAllToBarn = false }
            val depositItemBarn = pendingDepositItemToBarn.also { pendingDepositItemToBarn = null }
            val withdrawItemBarn = pendingWithdrawBarnItem.also { pendingWithdrawBarnItem = null }
            val withdrawItemBarnAmount = pendingWithdrawBarnItemAmount
            val withdrawEggsBarn = pendingWithdrawBarnEggs.also { pendingWithdrawBarnEggs = null }
            val selectItem = pendingSelectActiveItem.also { pendingSelectActiveItem = null }
            val toggleGate = pendingToggleGateId.also { pendingToggleGateId = null }
            val toggleWb = pendingToggleWheelbarrow.also { pendingToggleWheelbarrow = false }
            val loadWbItem = pendingLoadWheelbarrowItem.also { pendingLoadWheelbarrowItem = null }
            val unloadWbItem = pendingUnloadWheelbarrowItem.also { pendingUnloadWheelbarrowItem = null }
            val unloadWbToBarn = pendingUnloadWheelbarrowToBarn.also { pendingUnloadWheelbarrowToBarn = false }
            val sellWbCargo = pendingSellWheelbarrowCargo.also { pendingSellWheelbarrowCargo = false }
            val throwItem = pendingThrowItem.also { pendingThrowItem = false }
            val grindWheat = pendingGrindWheat.also { pendingGrindWheat = false }
            val kneadDough = pendingKneadDough.also { pendingKneadDough = false }
            val startOven = pendingStartBakeryOven.also { pendingStartBakeryOven = null }
            val collectOven = pendingCollectBakeryOven.also { pendingCollectBakeryOven = null }

            if (slaughterFlashAlpha > 0f) {
                slaughterFlashAlpha = (slaughterFlashAlpha - delta * 3.5f).coerceAtLeast(0f)
            }

            if (isGuestMode) {
                val activeBakery = world.factories.firstOrNull { it.id == insideBakeryId }
                if (insideBakeryId != null && activeBakery != null) {
                    if (movement.lengthSquared() > 0.001f) {
                        indoorPlayerMoving = true
                        indoorPlayerMovementDir = movement.normalized()
                        indoorPlayerFacing = if (kotlin.math.abs(movement.x) > kotlin.math.abs(movement.y)) {
                            if (movement.x > 0) Facing.RIGHT else Facing.LEFT
                        } else {
                            if (movement.y > 0) Facing.DOWN else Facing.UP
                        }
                        val indoorSpeed = 4.2f
                        val deltaMove = movement * (indoorSpeed * delta)
                        indoorPlayerPos = moveIndoorPlayer(indoorPlayerPos, deltaMove)
                        if (indoorPlayerPos.y >= 8.3f && indoorPlayerPos.x in 6.6f..9.4f) {
                            insideBakeryId = null
                            sounds.resumeMeadowMusic()
                            world = world.copy(
                                guestPlayer = (world.guestPlayer ?: world.player).copy(
                                    position = Vector2(activeBakery.collider.center.x, activeBakery.collider.bottom + 0.8f),
                                    facing = Facing.DOWN,
                                )
                            )
                            alertMessage = "Has salido a la granja"
                        }
                    } else {
                        indoorPlayerMoving = false
                        indoorPlayerMovementDir = Vector2.ZERO
                    }
                } else {
                    val guest = world.guestPlayer ?: world.player
                    var guestPos = guest.position
                    var guestFacing = guest.facing
                    var isMoving = false

                    if (movement.lengthSquared() > 0.001f) {
                        val speed = 5.2f
                        val deltaPos = movement.normalized() * (speed * delta)
                        val nextPos = guestPos + deltaPos
                        guestPos = Vector2(
                            nextPos.x.coerceIn(world.bounds.left + 0.5f, world.bounds.right - 0.5f),
                            nextPos.y.coerceIn(world.bounds.top + 0.5f, world.bounds.bottom - 0.5f),
                        )
                        guestFacing = if (kotlin.math.abs(movement.x) > kotlin.math.abs(movement.y)) {
                            if (movement.x > 0) Facing.RIGHT else Facing.LEFT
                        } else {
                            if (movement.y > 0) Facing.DOWN else Facing.UP
                        }
                        isMoving = true
                    }

                    // Check if guest approached bakery door
                    val approachingBakery = world.factories.firstOrNull {
                        it.type == FactoryType.BAKERY && it.phase == ConstructionPhase.OPERATIONAL &&
                            kotlin.math.abs(guestPos.x - it.collider.center.x) <= 1.2f &&
                            guestPos.y in (it.collider.bottom - 0.4f .. it.collider.bottom + 0.9f)
                    }
                    if (approachingBakery != null && selectedTool == FarmTool.HAND) {
                        insideBakeryId = approachingBakery.id
                        indoorPlayerPos = Vector2(8.0f, 7.2f)
                        indoorPlayerFacing = Facing.UP
                        indoorPlayerMoving = false
                        indoorPlayerMovementDir = Vector2.ZERO
                        sounds.playBakeryMusic()
                        alertMessage = "¡Entraste a la panadería artesanal!"
                    }

                    val activeItem = if (selectItem != null) (if (guest.activeItemId == selectItem) null else selectItem) else guest.activeItemId
                    val updatedGuest = guest.copy(
                        position = guestPos,
                        facing = guestFacing,
                        movementDirection = movement,
                        isMoving = isMoving,
                        selectedTool = selectedTool.toEngineTool(),
                        activeItemId = activeItem,
                    )
                    world = world.copy(guestPlayer = updatedGuest)

                    lanClient?.sendMove(
                        x = guestPos.x,
                        y = guestPos.y,
                        facing = guestFacing.name,
                        dirX = movement.x,
                        dirY = movement.y,
                        isMoving = isMoving,
                        tool = selectedTool.toEngineTool().name,
                        activeItem = activeItem?.name,
                        isMounted = false,
                    )
                    onlineGuestSession?.sendMove(
                        x = guestPos.x,
                        y = guestPos.y,
                        facing = guestFacing.name,
                        dirX = movement.x,
                        dirY = movement.y,
                        isMoving = isMoving,
                        tool = selectedTool.toEngineTool().name,
                        activeItem = activeItem?.name,
                        isMounted = false,
                    )
                }

                if (pressed) {
                    val currentGuestPos = (world.guestPlayer ?: world.player).position
                    lanClient?.sendAction(selectedTool.toEngineTool().name, currentGuestPos.x, currentGuestPos.y)
                    onlineGuestSession?.sendAction(selectedTool.toEngineTool().name, currentGuestPos.x, currentGuestPos.y)
                }
                if (emote != null) {
                    lanClient?.sendEmote(emote.name)
                    onlineGuestSession?.sendEmote(emote.name)
                }
            } else {
                val activeBakery = world.factories.firstOrNull { it.id == insideBakeryId }
                if (insideBakeryId != null && activeBakery != null) {
                    if (movement.lengthSquared() > 0.001f) {
                        indoorPlayerMoving = true
                        indoorPlayerMovementDir = movement.normalized()
                        indoorPlayerFacing = if (kotlin.math.abs(movement.x) > kotlin.math.abs(movement.y)) {
                            if (movement.x > 0) Facing.RIGHT else Facing.LEFT
                        } else {
                            if (movement.y > 0) Facing.DOWN else Facing.UP
                        }
                        val indoorSpeed = 4.2f
                        val deltaMove = movement * (indoorSpeed * delta)
                        indoorPlayerPos = moveIndoorPlayer(indoorPlayerPos, deltaMove)
                        if (indoorPlayerPos.y >= 8.3f && indoorPlayerPos.x in 6.6f..9.4f) {
                            insideBakeryId = null
                            sounds.resumeMeadowMusic()
                            if (isGuestMode) {
                                world = world.copy(
                                    guestPlayer = (world.guestPlayer ?: world.player).copy(
                                        position = Vector2(activeBakery.collider.center.x, activeBakery.collider.bottom + 0.8f),
                                        facing = Facing.DOWN,
                                    )
                                )
                            } else {
                                world = world.copy(
                                    player = world.player.copy(
                                        position = Vector2(activeBakery.collider.center.x, activeBakery.collider.bottom + 0.8f),
                                        facing = Facing.DOWN,
                                    ),
                                )
                            }
                            alertMessage = "Has salido a la granja"
                        }
                    } else {
                        indoorPlayerMoving = false
                        indoorPlayerMovementDir = Vector2.ZERO
                    }

                    if (pressed) {
                        val distToMolcajete = indoorPlayerPos.distanceTo(Vector2(3.2f, 5.4f))
                        val distToKnead = indoorPlayerPos.distanceTo(Vector2(12.8f, 5.4f))
                        val distToOven1 = indoorPlayerPos.distanceTo(Vector2(6.75f, 3.8f))
                        val distToOven2 = indoorPlayerPos.distanceTo(Vector2(9.25f, 3.8f))
                        val distToExit = indoorPlayerPos.distanceTo(Vector2(8.0f, 8.2f))

                        when {
                            distToMolcajete <= 2.2f -> {
                                showMolcajeteMinigame = true
                            }
                            distToKnead <= 2.2f -> {
                                showKneadMinigame = true
                            }
                            distToOven1 <= 2.2f -> {
                                if (activeBakery.oven1Ready) {
                                    pendingCollectBakeryOven = Pair(activeBakery.id, 1)
                                    selectedTool = FarmTool.HAND
                                    alertMessage = "¡Pan recién horneado recogido del horno #1! (+2 Panes en mano y mochila) 🍞"
                                } else if (!activeBakery.oven1Active && (localPlayer.inventory.count(ItemType.DOUGH) > 0 || localPlayer.activeItemId == ItemType.DOUGH)) {
                                    pendingStartBakeryOven = Pair(activeBakery.id, 1)
                                    alertMessage = "¡Masa introducida al horno #1! Horneando a leña (20s)..."
                                }
                            }
                            distToOven2 <= 2.2f -> {
                                if (activeBakery.oven2Ready) {
                                    pendingCollectBakeryOven = Pair(activeBakery.id, 2)
                                    selectedTool = FarmTool.HAND
                                    alertMessage = "¡Pan recién horneado recogido del horno #2! (+2 Panes en mano y mochila) 🍞"
                                } else if (!activeBakery.oven2Active && (localPlayer.inventory.count(ItemType.DOUGH) > 0 || localPlayer.activeItemId == ItemType.DOUGH)) {
                                    pendingStartBakeryOven = Pair(activeBakery.id, 2)
                                    alertMessage = "¡Masa introducida al horno #2! Horneando a leña (20s)..."
                                }
                            }
                            distToExit <= 2.0f || indoorPlayerPos.y >= 8.2f -> {
                                insideBakeryId = null
                                sounds.resumeMeadowMusic()
                                if (isGuestMode) {
                                    world = world.copy(
                                        guestPlayer = (world.guestPlayer ?: world.player).copy(
                                            position = Vector2(activeBakery.collider.center.x, activeBakery.collider.bottom + 0.8f),
                                            facing = Facing.DOWN,
                                        )
                                    )
                                } else {
                                    world = world.copy(
                                        player = world.player.copy(
                                            position = Vector2(activeBakery.collider.center.x, activeBakery.collider.bottom + 0.8f),
                                            facing = Facing.DOWN,
                                        ),
                                    )
                                }
                                alertMessage = "Has salido a la granja"
                            }
                        }
                    }
                } else {
                    // Check if player approached bakery door with hands in the outdoor farm world
                    val approachingBakery = world.factories.firstOrNull {
                        it.type == FactoryType.BAKERY && it.phase == ConstructionPhase.OPERATIONAL &&
                            kotlin.math.abs(localPlayer.position.x - it.collider.center.x) <= 1.2f &&
                            localPlayer.position.y in (it.collider.bottom - 0.4f .. it.collider.bottom + 0.9f)
                    }
                    if (approachingBakery != null && !localPlayer.isMounted && !world.isPencilMode && selectedTool == FarmTool.HAND) {
                        insideBakeryId = approachingBakery.id
                        indoorPlayerPos = Vector2(8.0f, 7.2f)
                        indoorPlayerFacing = Facing.UP
                        indoorPlayerMoving = false
                        indoorPlayerMovementDir = Vector2.ZERO
                        sounds.playBakeryMusic()
                        alertMessage = "¡Entraste a la panadería artesanal!"
                    }
                }

                val frame = engine.update(
                    delta,
                    WorldInput(
                        movement = if (insideBakeryId != null) Vector2.ZERO else movement,
                        actionPressed = if (insideBakeryId != null) false else pressed,
                        actionHeld = if (insideBakeryId != null) false else actionHeld,
                        selectedTool = selectedTool.toEngineTool(),
                        emote = emote,
                        animalPurchase = purchase,
                        animalRename = rename,
                        petCommand = petCommand,
                        bicycleBellPressed = bell,
                        throwAnimalPressed = throwAnimal,
                        throwDirection = world.player.movementDirection,
                        grabAnimalPressed = grab,
                        purchaseFences = fences,
                        purchaseSeeds = seeds,
                        purchaseWheatSeeds = wheatSeeds,
                        purchaseGates = gates,
                        purchaseBicycle = buyBike,
                        purchaseChicken = buyChicken,
                        purchaseTool = buyTool,
                        petAnimalPressed = petAction,
                        debugCoinsPressed = debugCoins,
                        hireHelperName = hireHelper,
                        renameHelper = renameHelper,
                        dismissHelper = dismissHelper,
                        rotateBlueprintPressed = rotateBp,
                        togglePencilModePressed = togglePencil,
                        pencilMoveTargetCell = pencilTarget,
                        purchaseBlueprint = buyBlueprint,
                        completeMinigame = completeMinigameReq,
                        nudgeStructure = nudge,
                        confirmStructurePosition = confirmStructure,
                        rotateStructurePressed = rotateStructure,
                        depositAllToBarn = depositAllBarn,
                        depositItemToBarn = depositItemBarn,
                        withdrawBarnItem = withdrawItemBarn,
                        withdrawBarnItemAmount = withdrawItemBarnAmount,
                        withdrawBarnEggs = withdrawEggsBarn,
                        selectActiveItem = selectItem,
                        toggleGateId = toggleGate,
                        toggleWheelbarrow = toggleWb,
                        loadWheelbarrowItem = loadWbItem,
                        unloadWheelbarrowItem = unloadWbItem,
                        unloadWheelbarrowToBarn = unloadWbToBarn,
                        sellWheelbarrowCargo = sellWbCargo,
                        throwItemPressed = throwItem,
                        grindWheat = grindWheat,
                        kneadDough = kneadDough,
                        startBakeryOven = startOven,
                        collectBakeryOven = collectOven,
                    ),
                )
                world = frame.state
                saveStore.track(world)
                frame.events.forEach { event ->
                    playWorldEvent(event, sounds, world)
                    if (event is WorldEvent.ToolBroken) {
                        alertMessage = "¡Se ha roto tu ${event.tool.name.lowercase()}! Compra una nueva en el mercado"
                    }
                    if (event is WorldEvent.PetPetted) {
                        alertMessage = "Acariciaste a ${event.petName} ❤️"
                    }
                    if (event is WorldEvent.ChickenTakenByFox) {
                        val chicken = event.chickenName.trim().let { if (it.isBlank() || it == "Gallina") "Gallina" else "Gallina $it" }
                        alertMessage = "El zorro ha matado a $chicken"
                    }
                    if (event is WorldEvent.PlayerFainted) alertMessage = "El zorro te derribó"
                    if (event is WorldEvent.FoxScaredByDog) alertMessage = "Tu perro ahuyentó al zorro"
                    if (event is WorldEvent.CatCaughtFish) alertMessage = "${event.catName} trajo un pez"
                    if (event is WorldEvent.OrderPlaced) alertMessage = "Pedido confirmado · el camión está en camino"
                    if (event is WorldEvent.OrderDelivered) alertMessage = "Pedido entregado junto al granero"
                    if (event is WorldEvent.SlaughterFlash) {
                        slaughterFlashAlpha = 1f
                        sounds.playAxeHit()
                        alertMessage = "¡Carne procesada en el matadero!"
                    }
                    if (event is WorldEvent.FactoryMinigameTriggered) {
                        activeMinigameFactory = world.factories.firstOrNull { it.id == event.factoryId }
                        eggPackerPlacedCount = 0
                        veggiePackerPlacedCount = 0
                        bakeryStep = 1
                        bakeryGrindTaps = 0
                        fishProcessorTaps = 0
                    }
                    if (event is WorldEvent.GateToggled) {
                        alertMessage = if (event.isOpen) "Puerta abierta" else "Puerta cerrada"
                    }
                    if (event is WorldEvent.BarnStorageChanged) {
                        alertMessage = "Productos guardados en la bodega del granero"
                    }
                    if (event is WorldEvent.WheelbarrowCargoSold) {
                        alertMessage = "¡Carga vendida al camión por $${event.coinsEarned}!"
                    }
                    if (event is WorldEvent.AnimalBorn) {
                        alertMessage = "¡Ha nacido una cría!"
                    }
                    if (event is WorldEvent.FactoryPlaced) {
                        selectedTool = FarmTool.HAND
                        alertMessage = "¡Plano colocado! Añade madera y piedra para construir"
                    }
                    if (event is WorldEvent.CowMilked) {
                        selectedTool = FarmTool.HAND
                        alertMessage = "Leche extraída de ${event.name} · tarro lleno en la mochila"
                    }
                    if (event is WorldEvent.FactoryCompleted) {
                        alertMessage = "¡Fábrica ${event.type.displayName} completada y operativa!"
                    }
                    if (event is WorldEvent.WeedHarvested) {
                        alertMessage = "Recogiste hierba · semillas obtenidas"
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF5F963B))) {
        LaunchedEffect(maxWidth, maxHeight) {
            if (maxHeight.value > 0f) {
                engine.setViewportWorldSize(Vector2(18f * maxWidth.value / maxHeight.value, 18f))
                if (!isGuestMode) {
                    world = engine.state
                }
            }
        }
        val canvasWorld = if (isGuestMode && world.guestPlayer != null) {
            world.copy(camera = world.camera.copy(center = world.guestPlayer!!.position))
        } else {
            world
        }
        val currentBakery = world.factories.firstOrNull { it.id == insideBakeryId } ?: world.factories.firstOrNull { it.type == FactoryType.BAKERY }
        if (insideBakeryId != null && currentBakery != null) {
            val indoorPlayer = localPlayer.copy(
                position = indoorPlayerPos,
                facing = indoorPlayerFacing,
                isMoving = indoorPlayerMoving,
                movementDirection = indoorPlayerMovementDir,
            )
            BakeryInteriorCanvas(
                world = canvasWorld,
                player = indoorPlayer,
                bakery = currentBakery,
                modifier = Modifier.fillMaxSize(),
            )
            BakeryIndoorHud(
                bakery = currentBakery,
                wheatCount = localPlayer.inventory.count(ItemType.WHEAT),
                flourCount = localPlayer.inventory.count(ItemType.FLOUR),
                doughCount = localPlayer.inventory.count(ItemType.DOUGH),
                breadCount = localPlayer.inventory.count(ItemType.BREAD),
                onBackpack = { showBackpack = !showBackpack },
                onExit = {
                    insideBakeryId = null
                    sounds.resumeMeadowMusic()
                    if (isGuestMode) {
                        world = world.copy(
                            guestPlayer = (world.guestPlayer ?: world.player).copy(
                                position = Vector2(currentBakery.collider.center.x, currentBakery.collider.bottom + 0.8f),
                                facing = Facing.DOWN,
                            )
                        )
                    } else {
                        world = world.copy(
                            player = world.player.copy(
                                position = Vector2(currentBakery.collider.center.x, currentBakery.collider.bottom + 0.8f),
                                facing = Facing.DOWN,
                            ),
                        )
                    }
                    alertMessage = "Has salido a la granja"
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else {
            FarmWorldCanvas(
                world = canvasWorld,
                modifier = Modifier.fillMaxSize().pointerInput(engine, showMap, world.isPencilMode) {
                    if (world.isPencilMode) {
                        detectTapGestures { offset ->
                            val visibleHeight = world.camera.baseViewportWorldSize.y / world.camera.zoom.coerceAtLeast(0.1f)
                            val pixelsPerUnit = size.height / visibleHeight
                            val visibleWidth = size.width / pixelsPerUnit
                            val left = world.camera.center.x - visibleWidth * 0.5f
                            val top = world.camera.center.y - visibleHeight * 0.5f
                            val worldX = left + offset.x / pixelsPerUnit
                            val worldY = top + offset.y / pixelsPerUnit
                            pendingPencilTarget = Pair(kotlin.math.floor(worldX).toInt(), kotlin.math.floor(worldY).toInt())
                        }
                    } else if (!showMap) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            world = engine.setZoom(world.camera.zoom * zoomChange)
                        }
                    }
                },
            )

            FarmHud(
                world = world,
                player = localPlayer,
                showFps = showFpsCounter,
                fps = currentFps,
                onOpenSettings = { showSettings = true },
                onMap = {
                    movement = Vector2.ZERO
                    actionHeld = false
                    showMap = true
                },
                onShop = { movement = Vector2.ZERO; showShop = true },
                onBackpack = { showBackpack = !showBackpack },
                onPets = { showPets = !showPets },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        val isFishingLocked = !isGuestMode && world.fishing.phase in setOf(FishingPhase.REELING, FishingPhase.SUCCESS, FishingPhase.FAILED)
        val controlsEnabled = !showShop && namingTarget == null && !isFishingLocked

        if (showMap) {
            FullFarmMap(world = world, onClose = { showMap = false }, modifier = Modifier.fillMaxSize())
        }

        val engineTool = selectedTool.toEngineTool()
        val currentDurability = localPlayer.toolDurability[engineTool]

        GameControls(
            selectedTool = selectedTool,
            onToolSelected = {
                selectedTool = it
            },
            onMove = { x, y -> movement = Vector2(x, y) },
            onAction = { actionPressed = true },
            enabled = controlsEnabled,
            durability = currentDurability,
        )

        EmotePicker(
            onEmoteSelected = { emote ->
                pendingEmote = when (emote) {
                    FarmEmote.HEART -> EmoteType.HEART
                    FarmEmote.SMILE -> EmoteType.HAPPY
                    FarmEmote.WAVE -> EmoteType.WAVE
                    FarmEmote.SURPRISE -> EmoteType.SURPRISED
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 155.dp, bottom = 112.dp),
            enabled = controlsEnabled,
        )

        if (selectedTool == FarmTool.HAND && !localPlayer.isMounted && !showShop && namingTarget == null) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 22.dp, bottom = 118.dp),
            ) {
                if (localPlayer.carriedAnimal != null) {
                    PixelHudButton(
                        "ARROJAR",
                        onClick = { pendingThrow = true },
                    )
                    PixelHudButton(
                        "NOMBRAR",
                        onClick = {
                            val carried = localPlayer.carriedAnimal
                            if (carried != null) {
                                namingTarget = carried
                                nameDraft = animalName(world, carried)
                            }
                        },
                    )
                } else {
                    val nearbyPet = world.animals.firstOrNull {
                        it.type.isPet && it.handling == AnimalHandlingState.FREE &&
                            it.position.distanceTo(localPlayer.position) <= 2.2f
                    }
                    if (nearbyPet != null) {
                        PixelHudButton(
                            "❤️ ACARICIAR",
                            onClick = { pendingPetAction = true },
                        )
                    }
                }
                PixelHudButton(
                    if (localPlayer.carriedAnimal == null) "LEVANTAR" else "SOLTAR",
                    onClick = { pendingGrab = true },
                )
            }
        }

        val isBlueprintSelected = selectedTool in setOf(
            FarmTool.BLUEPRINT_DAIRY,
            FarmTool.BLUEPRINT_SLAUGHTERHOUSE,
            FarmTool.BLUEPRINT_EGG_PACKER,
            FarmTool.BLUEPRINT_VEGGIE_PACKER,
            FarmTool.BLUEPRINT_BAKERY,
            FarmTool.BLUEPRINT_FISH_PROCESSOR,
        )

        if (isBlueprintSelected && !localPlayer.isMounted && !showShop && namingTarget == null) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 22.dp, bottom = 118.dp),
            ) {
                HudLabel("ORIENTACIÓN: ${world.selectedBlueprintOrientation.name}")
                PixelHudButton(
                    "🔄 GIRAR",
                    onClick = { pendingRotateBlueprint = true },
                )
                PixelHudButton(
                    "CANCELAR",
                    onClick = { selectedTool = FarmTool.HAND },
                )
            }
        }

        if (selectedTool == FarmTool.ARCHITECT_PENCIL && !localPlayer.isMounted && !showShop && namingTarget == null) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 22.dp, bottom = 118.dp),
            ) {
                HudLabel(if (world.isPencilMode) "TOCA EDIFICIO Y LUEGO DESTINO" else "MODO REUBICACIÓN")
                PixelHudButton(
                    if (world.isPencilMode) "📐 SALIR DE DISEÑO" else "📐 ACTIVAR DISEÑO",
                    onClick = { pendingTogglePencil = true },
                )
            }
        }

        if (selectedTool in setOf(FarmTool.HOE, FarmTool.PATH, FarmTool.FENCE, FarmTool.GATE) &&
            !localPlayer.isMounted && !showShop && namingTarget == null
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 22.dp, bottom = 118.dp),
            ) {
                HudLabel(
                    if (selectedTool in setOf(FarmTool.FENCE, FarmTool.GATE)) "SOMBRA · COLOCAR" else "SOMBRA · PONER / QUITAR",
                )
                PixelHudButton(
                    "CANCELAR",
                    onClick = { selectedTool = FarmTool.HAND },
                )
            }
        }

        if (localPlayer.isMounted && !showShop) {
            PixelHudButton(
                "🔔 CAMPANA",
                onClick = { pendingBell = true },
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 22.dp, bottom = 118.dp),
            )
        }

        if (world.isPencilMode && world.selectedMoveStructureId != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .background(HudCream, RoundedCornerShape(14.dp))
                    .border(3.dp, HudBrown, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudLabel("MOVER ESTRUCTURA")
                PixelHudButton("⬆️ ARRIBA", onClick = { pendingNudge = Pair(0, -1) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelHudButton("⬅️", onClick = { pendingNudge = Pair(-1, 0) })
                    PixelHudButton("➡️", onClick = { pendingNudge = Pair(1, 0) })
                }
                PixelHudButton("⬇️ ABAJO", onClick = { pendingNudge = Pair(0, 1) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelHudButton("🔄 ROTAR", onClick = { pendingRotateStructure = true })
                    PixelHudButton("✅ FIJAR", onClick = { pendingConfirmStructure = true })
                }
            }
        }

        val distToWb = world.wheelbarrow.position.distanceTo(localPlayer.position)
        val isPushingWb = world.wheelbarrow.isBeingPushed
        if ((distToWb <= 2.8f || isPushingWb) && !localPlayer.isMounted && !showShop && namingTarget == null && !world.isPencilMode) {
            val distWbToBarn = world.wheelbarrow.position.distanceTo(world.barn.doorPosition)
            val distWbToTruck = world.wheelbarrow.position.distanceTo(world.truck.position)
            val nearBarn = distWbToBarn <= 4.0f || localPlayer.position.distanceTo(world.barn.doorPosition) <= 3.5f
            val nearTruck = distWbToTruck <= 4.2f && world.truck.phase == TruckPhase.PARKED

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 22.dp, bottom = 170.dp)
                    .background(HudCream.copy(alpha = 0.94f), RoundedCornerShape(12.dp))
                    .border(2.dp, HudBrown, RoundedCornerShape(12.dp))
                    .padding(8.dp),
            ) {
                Text(
                    "🛒 CARRETILLA (${world.wheelbarrow.totalCargo}/${world.wheelbarrow.capacity})",
                    color = HudBrown,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isPushingWb) {
                        PixelHudButton("🛑 SOLTAR", onClick = { pendingToggleWheelbarrow = true })
                    } else {
                        PixelHudButton("🛒 EMPUJAR", onClick = { pendingToggleWheelbarrow = true })
                    }
                    if (world.wheelbarrow.cargo.isNotEmpty()) {
                        if (nearBarn) {
                            PixelHudButton("🌾 GUARDAR EN BODEGA", onClick = { pendingUnloadWheelbarrowToBarn = true })
                        }
                        if (nearTruck) {
                            PixelHudButton("💰 VENDER AL CAMIÓN", onClick = { pendingSellWheelbarrowCargo = true })
                        }
                    }
                }

                if (world.wheelbarrow.cargo.isNotEmpty()) {
                    world.wheelbarrow.cargo.forEach { (item, qty) ->
                        Row(
                            modifier = Modifier
                                .widthIn(min = 160.dp, max = 220.dp)
                                .background(Color(0xFFEFE2BC), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${item.displayName} ×$qty", color = HudBrown, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            PixelHudButton("SACAR", onClick = { pendingUnloadWheelbarrowItem = item })
                        }
                    }
                }

                val activeItem = localPlayer.activeItemId
                if (!world.wheelbarrow.isFull && activeItem != null && localPlayer.inventory.count(activeItem) > 0) {
                    PixelHudButton(
                        "📦 CARGAR ${activeItem.displayName.uppercase()}",
                        onClick = { pendingLoadWheelbarrowItem = activeItem },
                    )
                }
            }
        }

        val distToBarn = localPlayer.position.distanceTo(world.barn.doorPosition)
        if (distToBarn <= 3.5f && !localPlayer.isMounted && !showShop && namingTarget == null && !world.isPencilMode) {
            PixelHudButton(
                "🌾 BODEGA GRANERO",
                onClick = { showBarnBodega = true },
                modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(start = 22.dp, bottom = 118.dp),
            )
        }

        val activeItem = localPlayer.activeItemId
        if (activeItem in setOf(ItemType.EGG, ItemType.STONE) && !localPlayer.isMounted && !showShop && namingTarget == null && !world.isPencilMode) {
            PixelHudButton(
                if (activeItem == ItemType.EGG) "🎯 LANZAR HUEVO" else "🎯 LANZAR SAPITO",
                onClick = { pendingThrowItem = true },
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 92.dp, bottom = 118.dp),
            )
        }

        val nearbyGate = world.gates.firstOrNull { it.position.distanceTo(localPlayer.position) <= 2.0f }
        if (nearbyGate != null && !localPlayer.isMounted && !showShop && namingTarget == null && !world.isPencilMode) {
            PixelHudButton(
                if (nearbyGate.isOpen) "🚪 CERRAR PUERTA" else "🚪 ABRIR PUERTA",
                onClick = { pendingToggleGateId = nearbyGate.id },
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 90.dp),
            )
        }

        val nearbySlaughterhouse = world.factories.firstOrNull { it.type == FactoryType.SLAUGHTERHOUSE && it.position.distanceTo(localPlayer.position) <= 3.2f }
        if (nearbySlaughterhouse != null && (nearbySlaughterhouse.hasPigInChamber || nearbySlaughterhouse.hasCowInChamber) && localPlayer.carriedAnimal == null && !localPlayer.isMounted && !showShop && !world.isPencilMode) {
            PixelHudButton(
                "🔓 SACAR ANIMAL DEL MATADERO",
                onClick = { actionPressed = true },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
            )
        }

        val effectiveActiveItem = localPlayer.activeItemId
        if (effectiveActiveItem != null && !showShop && !world.isPencilMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(start = 16.dp, top = 65.dp)
                    .background(Color(0xDD3B2818), RoundedCornerShape(10.dp))
                    .border(2.dp, Color(0xFFF3B83E), RoundedCornerShape(10.dp))
                    .clickable { pendingSelectActiveItem = effectiveActiveItem }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("🖐️ EN MANO:", color = Color(0xFFFFE7A0), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(effectiveActiveItem.displayName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("×", color = Color(0xFFFF8888), fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }

        if (onlineRoomCode != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 8.dp)
                    .background(Color(0xDD1B3322), RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color(0xFFA8E6CF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🌐 INVITADO ONLINE · SALA $onlineRoomCode",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else if (lanJoinHost != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 8.dp)
                    .background(Color(0xDD1B3322), RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color(0xFFA8E6CF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "📡 INVITADO LAN · CONECTADO A $lanJoinHost",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else if (onlineHostEnabled) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 8.dp)
                    .background(Color(0xDD182E3D), RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color(0xFF82B1FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val friendText = if (onlineConnectedFriendName != null) "1 amigo: $onlineConnectedFriendName" else "Esperando amigo..."
                Text(
                    "🌐 SALA ONLINE: $onlineRoomCodeGenerated · $friendText",
                    color = Color(0xFFD4E4FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else if (lanHostEnabled) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 8.dp)
                    .background(Color(0xDD3E2716), RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color(0xFFF3B83E), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val friendCount = if (lanConnectedFriendName != null) "1 amigo ($lanConnectedFriendName)" else "0 amigos"
                Text(
                    "📡 HOST LAN · $friendCount · IP: $lanHostIp",
                    color = Color(0xFFFFE7A0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        val effectivePlayerPos = localPlayer.position
        val nearbyPigForBreed = world.animals.firstOrNull {
            it.type == DomesticAnimalType.PIG && it.lifeStage == AnimalLifeStage.ADULT &&
                it.position.distanceTo(effectivePlayerPos) <= 2.8f
        }
        if (effectiveActiveItem == ItemType.CARROT && nearbyPigForBreed != null && !localPlayer.isMounted && !world.isPencilMode) {
            PixelHudButton(
                "🥕 ALIMENTAR CERDO",
                onClick = { actionPressed = true },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
            )
        }

        val nearbyCowForBreed = world.animals.firstOrNull {
            it.type == DomesticAnimalType.COW && it.lifeStage == AnimalLifeStage.ADULT &&
                it.position.distanceTo(effectivePlayerPos) <= 2.8f
        }
        val nearbyCowToMilk = world.animals.firstOrNull {
            it.type == DomesticAnimalType.COW &&
                it.lifeStage == AnimalLifeStage.ADULT &&
                it.handling == AnimalHandlingState.FREE &&
                it.position.distanceTo(effectivePlayerPos) <= 2.8f
        }
        val hasMilkPail = effectiveActiveItem == ItemType.MILK_PAIL || localPlayer.inventory.count(ItemType.MILK_PAIL) > 0
        if (nearbyCowToMilk != null && hasMilkPail && !localPlayer.isMounted && !world.isPencilMode) {
            if (nearbyCowToMilk.milkReadyInSeconds <= 0f) {
                PixelHudButton(
                    "🥛 EXTRAER LECHE",
                    onClick = {
                        pendingSelectActiveItem = ItemType.MILK_PAIL
                        selectedTool = FarmTool.MILK_PAIL
                        actionPressed = true
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                )
            } else {
                HudLabel(
                    "⏳ LECHE LISTA EN ${nearbyCowToMilk.milkReadyInSeconds.toInt()}s",
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                )
            }
        }

        val nearbyDairy = world.factories.firstOrNull {
            it.type == FactoryType.DAIRY && it.phase == ConstructionPhase.OPERATIONAL &&
                it.collider.distanceTo(effectivePlayerPos) <= 3.2f
        }
        if (nearbyDairy != null && !localPlayer.isMounted && !world.isPencilMode) {
            if (nearbyDairy.storedOutputs > 0) {
                PixelHudButton(
                    "🧀 RECOGER QUESO (${nearbyDairy.storedOutputs})",
                    onClick = { actionPressed = true },
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                )
            } else if (localPlayer.inventory.count(ItemType.MILK) > 0 || effectiveActiveItem == ItemType.MILK) {
                val milkCount = localPlayer.inventory.count(ItemType.MILK)
                PixelHudButton(
                    "🥛 ENTREGAR LECHE A PROCESADORA ($milkCount)",
                    onClick = { actionPressed = true },
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                )
            }
        }

        if (insideBakeryId != null && currentBakery != null) {
            val distToMolcajete = indoorPlayerPos.distanceTo(Vector2(3.2f, 5.4f))
            val distToKnead = indoorPlayerPos.distanceTo(Vector2(12.8f, 5.4f))
            val distToOven1 = indoorPlayerPos.distanceTo(Vector2(6.75f, 3.8f))
            val distToOven2 = indoorPlayerPos.distanceTo(Vector2(9.25f, 3.8f))
            val distToExit = indoorPlayerPos.distanceTo(Vector2(8.0f, 8.2f))

            when {
                distToMolcajete <= 2.2f -> {
                    val hasWheat = localPlayer.inventory.count(ItemType.WHEAT) > 0 || effectiveActiveItem == ItemType.WHEAT
                    val label = if (hasWheat) "🌾 MOLER TRIGO EN MOLCAJETE" else "🌾 MOLIENDA (Trae trigo de la granja)"
                    PixelHudButton(
                        label,
                        onClick = { showMolcajeteMinigame = true },
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                    )
                }
                distToKnead <= 2.2f -> {
                    val hasFlour = localPlayer.inventory.count(ItemType.FLOUR) > 0 || effectiveActiveItem == ItemType.FLOUR
                    val label = if (hasFlour) "💧 AMASAR HARINA (MEZCLAR AGUA)" else "💧 AMASADO (Trae harina molida)"
                    PixelHudButton(
                        label,
                        onClick = { showKneadMinigame = true },
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                    )
                }
                distToOven1 <= 2.2f -> {
                    if (currentBakery.oven1Ready) {
                        PixelHudButton(
                            "🍞 SACAR PAN DEL HORNO 1 (+2 Panes)",
                            onClick = {
                                if (isGuestMode) {
                                    val currentG = world.guestPlayer ?: world.player
                                    val updatedG = currentG.copy(
                                        inventory = currentG.inventory.add(ItemType.BREAD, 2),
                                        activeItemId = ItemType.BREAD,
                                    )
                                    world = world.copy(guestPlayer = updatedG)
                                    lanClient?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.BREAD.name, false)
                                    onlineGuestSession?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.BREAD.name, false)
                                } else {
                                    pendingCollectBakeryOven = Pair(currentBakery.id, 1)
                                }
                                selectedTool = FarmTool.HAND
                                alertMessage = "¡Pan recién horneado recogido del horno #1! (+2 Panes en mano y mochila) 🍞"
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                        )
                    } else if (currentBakery.oven1Active) {
                        val s = currentBakery.oven1Timer.toInt().coerceAtLeast(1)
                        PixelHudButton(
                            "🔥 HORNO 1 HORNEANDO... ${s}s",
                            onClick = {},
                            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                        )
                    } else {
                        val hasDough = localPlayer.inventory.count(ItemType.DOUGH) > 0 || effectiveActiveItem == ItemType.DOUGH
                        val label = if (hasDough) "🥖 METER MASA AL HORNO 1" else "🥖 HORNO 1 VACÍO (Trae masa fresca)"
                        PixelHudButton(
                            label,
                            onClick = {
                                if (hasDough) {
                                    if (isGuestMode) {
                                        val currentG = world.guestPlayer ?: world.player
                                        val updatedG = currentG.copy(
                                            inventory = currentG.inventory.remove(ItemType.DOUGH, 1),
                                            activeItemId = if (currentG.activeItemId == ItemType.DOUGH) null else currentG.activeItemId,
                                        )
                                        world = world.copy(guestPlayer = updatedG)
                                        lanClient?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, updatedG.activeItemId?.name, false)
                                        onlineGuestSession?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, updatedG.activeItemId?.name, false)
                                    } else {
                                        pendingStartBakeryOven = Pair(currentBakery.id, 1)
                                    }
                                    alertMessage = "¡Masa introducida al horno #1! Horneando a leña (20s)..."
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                        )
                    }
                }
                distToOven2 <= 2.2f -> {
                    if (currentBakery.oven2Ready) {
                        PixelHudButton(
                            "🍞 SACAR PAN DEL HORNO 2 (+2 Panes)",
                            onClick = {
                                if (isGuestMode) {
                                    val currentG = world.guestPlayer ?: world.player
                                    val updatedG = currentG.copy(
                                        inventory = currentG.inventory.add(ItemType.BREAD, 2),
                                        activeItemId = ItemType.BREAD,
                                    )
                                    world = world.copy(guestPlayer = updatedG)
                                    lanClient?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.BREAD.name, false)
                                    onlineGuestSession?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.BREAD.name, false)
                                } else {
                                    pendingCollectBakeryOven = Pair(currentBakery.id, 2)
                                }
                                selectedTool = FarmTool.HAND
                                alertMessage = "¡Pan recién horneado recogido del horno #2! (+2 Panes en mano y mochila) 🍞"
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                        )
                    } else if (currentBakery.oven2Active) {
                        val s = currentBakery.oven2Timer.toInt().coerceAtLeast(1)
                        PixelHudButton(
                            "🔥 HORNO 2 HORNEANDO... ${s}s",
                            onClick = {},
                            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                        )
                    } else {
                        val hasDough = localPlayer.inventory.count(ItemType.DOUGH) > 0 || effectiveActiveItem == ItemType.DOUGH
                        val label = if (hasDough) "🥖 METER MASA AL HORNO 2" else "🥖 HORNO 2 VACÍO (Trae masa fresca)"
                        PixelHudButton(
                            label,
                            onClick = {
                                if (hasDough) {
                                    if (isGuestMode) {
                                        val currentG = world.guestPlayer ?: world.player
                                        val updatedG = currentG.copy(
                                            inventory = currentG.inventory.remove(ItemType.DOUGH, 1),
                                            activeItemId = if (currentG.activeItemId == ItemType.DOUGH) null else currentG.activeItemId,
                                        )
                                        world = world.copy(guestPlayer = updatedG)
                                        lanClient?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, updatedG.activeItemId?.name, false)
                                        onlineGuestSession?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, updatedG.activeItemId?.name, false)
                                    } else {
                                        pendingStartBakeryOven = Pair(currentBakery.id, 2)
                                    }
                                    alertMessage = "¡Masa introducida al horno #2! Horneando a leña (20s)..."
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                        )
                    }
                }
                distToExit <= 2.0f || indoorPlayerPos.y >= 8.2f -> {
                    PixelHudButton(
                        "🚪 SALIR A LA GRANJA",
                        onClick = {
                            insideBakeryId = null
                            sounds.resumeMeadowMusic()
                            if (isGuestMode) {
                                world = world.copy(
                                    guestPlayer = (world.guestPlayer ?: world.player).copy(
                                        position = Vector2(currentBakery.collider.center.x, currentBakery.collider.bottom + 0.8f),
                                        facing = Facing.DOWN,
                                    )
                                )
                            } else {
                                world = world.copy(
                                    player = world.player.copy(
                                        position = Vector2(currentBakery.collider.center.x, currentBakery.collider.bottom + 0.8f),
                                        facing = Facing.DOWN,
                                    ),
                                )
                            }
                            alertMessage = "Has salido a la granja"
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
                    )
                }
            }
        }

        if (insideBakeryId == null && effectiveActiveItem == ItemType.WHEAT && nearbyCowForBreed != null && !localPlayer.isMounted && !world.isPencilMode) {
            PixelHudButton(
                "🌾 ALIMENTAR VACA",
                onClick = { actionPressed = true },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
            )
        }

        val nearbyChickenForBreed = world.chickens.firstOrNull {
            it.lifeStage == ChickenLifeStage.ADULT && it.position.distanceTo(effectivePlayerPos) <= 2.8f
        }
        if (insideBakeryId == null && effectiveActiveItem in setOf(ItemType.SEED, ItemType.WHEAT_SEED) && nearbyChickenForBreed != null && !localPlayer.isMounted && !world.isPencilMode) {
            PixelHudButton(
                "🌱 ALIMENTAR GALLINA",
                onClick = { actionPressed = true },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp),
            )
        }

        if (showBackpack) {
            BackpackPanel(
                world = world,
                player = localPlayer,
                onSelectActiveItem = {
                    pendingSelectActiveItem = it
                    selectedTool = it.inventoryFarmTool()
                },
                onEquipTool = {
                    selectedTool = it
                    showBackpack = false
                },
                onOpenBodega = { showBarnBodega = true },
                onClose = { showBackpack = false },
                modifier = Modifier.align(Alignment.CenterStart).padding(12.dp),
            )
        }

        if (showBarnBodega) {
            BarnBodegaDialog(
                world = world,
                player = localPlayer,
                onDepositAll = { pendingDepositAllToBarn = true },
                onDepositItem = { pendingDepositItemToBarn = it },
                onWithdrawItem = { item, amount ->
                    pendingWithdrawBarnItem = item
                    pendingWithdrawBarnItemAmount = amount
                },
                onWithdrawEggs = { count ->
                    pendingWithdrawBarnEggs = count
                },
                onClose = { showBarnBodega = false },
            )
        }

        if (showPets) {
            PetPanel(
                world = world,
                onCommand = { pendingPetCommand = it },
                onEquipBall = {
                    selectedTool = FarmTool.PET_BALL
                    showPets = false
                },
                onClose = { showPets = false },
                modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            )
        }

        if (!isGuestMode) {
            FishingStatusOverlay(
                fishing = world.fishing,
                onHook = { actionPressed = true },
                onHeld = { actionHeld = it },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (showShop) {
            PhoneShop(
                world = world,
                onBuy = { pendingPurchase = it },
                onBuySeeds = { pendingSeedPurchase = 12 },
                onBuyWheatSeeds = { pendingWheatSeedPurchase = 10 },
                onBuyFences = { pendingFencePurchase = 8 },
                onBuyGates = { pendingGatePurchase = 2 },
                onBuyBike = { pendingBikePurchase = true },
                onBuyChicken = { pendingChickenPurchase = true },
                onBuyTool = { pendingToolPurchase = it },
                onBuyBlueprint = { pendingBlueprintPurchase = it },
                onHireHelper = { pendingHireHelperName = it },
                onRenameHelper = { pendingRenameHelper = it },
                onDismissHelper = { pendingDismissHelper = true },
                onDebugCoins = { pendingDebugCoins = true },
                onClose = { showShop = false },
            )
        }

        namingTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { namingTarget = null },
                title = { Text("Nombrar animal") },
                text = { OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it.take(18) }, singleLine = true) },
                confirmButton = { TextButton(onClick = {
                    pendingRename = AnimalRenameRequest(target, nameDraft)
                    namingTarget = null
                }) { Text("GUARDAR") } },
                dismissButton = { TextButton(onClick = { namingTarget = null }) { Text("AHORA NO") } },
            )
        }

        if (showSettings) {
            SettingsPanel(
                soundEffectsEnabled = soundEffectsEnabled,
                musicEnabled = musicEnabled,
                showFpsCounter = showFpsCounter,
                lanHostEnabled = lanHostEnabled,
                lanHostIp = lanHostIp,
                connectedFriendName = lanConnectedFriendName,
                onlineHostEnabled = onlineHostEnabled,
                onlineRoomCode = onlineRoomCodeGenerated,
                onlineConnectedFriendName = onlineConnectedFriendName,
                isGuestMode = isGuestMode,
                guestHostAddress = lanJoinHost ?: (onlineRoomCode?.let { "Sala Online #$it" }),
                onToggleSoundEffects = {
                    soundEffectsEnabled = !soundEffectsEnabled
                    sounds.enabled = soundEffectsEnabled
                },
                onToggleMusic = {
                    musicEnabled = !musicEnabled
                    sounds.musicEnabled = musicEnabled
                },
                onToggleFpsCounter = {
                    showFpsCounter = !showFpsCounter
                },
                onToggleLanHost = {
                    lanHostEnabled = !lanHostEnabled
                },
                onToggleOnlineHost = {
                    onlineHostEnabled = !onlineHostEnabled
                },
                onRegenerateOnlineCode = {
                    onlineRoomCodeGenerated = (1000..9999).random().toString()
                },
                onExitToMenu = {
                    if (!isGuestMode) {
                        saveStore.saveNow(world)
                    }
                    onBack()
                },
                onClose = { showSettings = false },
            )
        }

        if (slaughterFlashAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = slaughterFlashAlpha))
            )
        }

        activeMinigameFactory?.let { factory ->
            when (factory.type) {
                FactoryType.EGG_PACKER -> {
                    EggPackerMinigameDialog(
                        inventoryEggs = world.player.inventory.count(ItemType.EGG),
                        placedEggs = eggPackerPlacedCount,
                        onAddEgg = {
                            if (world.player.inventory.count(ItemType.EGG) > eggPackerPlacedCount && eggPackerPlacedCount < 6) {
                                eggPackerPlacedCount++
                            }
                        },
                        onPack = {
                            pendingCompleteMinigame = FactoryType.EGG_PACKER
                            activeMinigameFactory = null
                            alertMessage = "¡Cartón de 6 huevos empaquetado!"
                        },
                        onClose = { activeMinigameFactory = null },
                    )
                }
                FactoryType.VEGGIE_PACKER -> {
                    VeggiePackerMinigameDialog(
                        inventoryCarrots = world.player.inventory.count(ItemType.CARROT),
                        placedCarrots = veggiePackerPlacedCount,
                        onAddCarrot = {
                            if (world.player.inventory.count(ItemType.CARROT) > veggiePackerPlacedCount && veggiePackerPlacedCount < 5) {
                                veggiePackerPlacedCount++
                            }
                        },
                        onPack = {
                            pendingCompleteMinigame = FactoryType.VEGGIE_PACKER
                            activeMinigameFactory = null
                            alertMessage = "¡Caja de 5 verduras empaquetada!"
                        },
                        onClose = { activeMinigameFactory = null },
                    )
                }
                FactoryType.BAKERY -> {
                    activeMinigameFactory = null
                    insideBakeryId = factory.id
                    indoorPlayerPos = Vector2(8.0f, 7.5f)
                    indoorPlayerFacing = Facing.UP
                    indoorPlayerMoving = false
                    indoorPlayerMovementDir = Vector2.ZERO
                    sounds.playBakeryMusic()
                }
                FactoryType.FISH_PROCESSOR -> {
                    FishProcessorMinigameDialog(
                        inventoryFish = world.player.inventory.count(ItemType.FISH),
                        taps = fishProcessorTaps,
                        onTapCut = { fishProcessorTaps++ },
                        onPack = {
                            pendingCompleteMinigame = FactoryType.FISH_PROCESSOR
                            activeMinigameFactory = null
                            alertMessage = "¡Filete de pescado fresco obtenido!"
                        },
                        onClose = { activeMinigameFactory = null },
                    )
                }
                else -> Unit
            }
        }

        if (showMolcajeteMinigame) {
            MolcajeteGrindDialog(
                wheatCount = localPlayer.inventory.count(ItemType.WHEAT),
                onGrindWheat = {
                    if (isGuestMode) {
                        val currentG = world.guestPlayer ?: world.player
                        val inv = currentG.inventory
                        if (inv.count(ItemType.WHEAT) > 0) {
                            val updatedG = currentG.copy(
                                inventory = inv.remove(ItemType.WHEAT, 1).add(ItemType.FLOUR, 1),
                                activeItemId = ItemType.FLOUR,
                            )
                            world = world.copy(guestPlayer = updatedG)
                            lanClient?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.FLOUR.name, false)
                            onlineGuestSession?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.FLOUR.name, false)
                        }
                    } else {
                        pendingGrindWheat = true
                    }
                    selectedTool = FarmTool.HAND
                    alertMessage = "¡Molienda completada! +1 Harina en mano y mochila 🥡"
                },
                onClose = { showMolcajeteMinigame = false },
            )
        }

        if (showKneadMinigame) {
            DoughKneadDialog(
                flourCount = localPlayer.inventory.count(ItemType.FLOUR),
                onKneadDough = {
                    if (isGuestMode) {
                        val currentG = world.guestPlayer ?: world.player
                        val inv = currentG.inventory
                        if (inv.count(ItemType.FLOUR) > 0) {
                            val updatedG = currentG.copy(
                                inventory = inv.remove(ItemType.FLOUR, 1).add(ItemType.DOUGH, 1),
                                activeItemId = ItemType.DOUGH,
                            )
                            world = world.copy(guestPlayer = updatedG)
                            lanClient?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.DOUGH.name, false)
                            onlineGuestSession?.sendMove(updatedG.position.x, updatedG.position.y, updatedG.facing.name, 0f, 0f, false, selectedTool.toEngineTool().name, ItemType.DOUGH.name, false)
                        }
                    } else {
                        pendingKneadDough = true
                    }
                    selectedTool = FarmTool.HAND
                    alertMessage = "¡Masa amasada y elástica! +1 Masa fresca en mano y mochila 🥟"
                },
                onClose = { showKneadMinigame = false },
            )
        }

        alertMessage?.let { message ->
            LaunchedEffect(message) { delay(4_000); if (alertMessage == message) alertMessage = null }
            HudLabel(message, Modifier.align(Alignment.Center).padding(bottom = 180.dp))
        }
    }
}

@Composable
private fun FarmHud(
    world: FarmWorldState,
    player: PlayerState = world.player,
    showFps: Boolean = false,
    fps: Int = 60,
    onOpenSettings: () -> Unit,
    onMap: () -> Unit,
    onShop: () -> Unit,
    onBackpack: () -> Unit,
    onPets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inventory = player.inventory
    val hours = (world.secondsOfDay / 3600f).toInt() % 24
    val minutes = ((world.secondsOfDay % 3600f) / 60f).toInt()
    val truck = when (world.truck.phase) {
        TruckPhase.ABSENT -> "Camión en ${world.truck.phaseTimer.toInt()}s"
        TruckPhase.ARRIVING -> "Camión llegando"
        TruckPhase.PARKED -> "Camión esperando"
        TruckPhase.LEAVING -> "Camión saliendo"
    }
    Row(
        modifier.safeDrawingPadding().fillMaxWidth().padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelHudIconButton(onClick = onOpenSettings) {
                    GearIcon(Modifier.size(16.dp))
                }
                PixelHudButton("MAPA", onMap)
                PixelHudButton("TIENDA", onShop)
            }
            HudLabel("$ ${inventory.coins}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelHudIconButton(onClick = onBackpack) {
                    BackpackIcon(Modifier.size(17.dp))
                }
                PixelHudIconButton(onClick = onPets) {
                    BoneIcon(Modifier.size(17.dp))
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudLabel("DÍA ${world.day}  %02d:%02d".format(hours, minutes))
            if (showFps) {
                HudLabel("$fps FPS")
            }
            if (world.fox.phase == FoxPhase.HUNTING) {
                BlinkingFoxWarning(world = world)
            }
            if (world.deliveries.isNotEmpty()) HudLabel("PEDIDOS EN CAMINO ${world.deliveries.size}")
            world.helper?.let { helper ->
                val status = if (hours in 8 until 17) helperActivityLabel(helper.behavior.name) else "DESCANSANDO"
                HudLabel("AYUD. ${helper.name.take(12).uppercase()} · $status")
            }
            HudLabel(truck)
        }
    }
}

@Composable
private fun FullFarmMap(
    world: FarmWorldState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color(0xE51B2B22))
            .clickable(onClick = {}),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .padding(14.dp)
                .background(Color(0xFF263B2D), RoundedCornerShape(13.dp))
                .border(4.dp, HudCream, RoundedCornerShape(13.dp))
                .padding(7.dp),
        ) {
            FarmWorldCanvas(world = world, modifier = Modifier.fillMaxSize(), overview = true)
            Text(
                "MAPA COMPLETO",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .background(HudCream, RoundedCornerShape(6.dp))
                    .border(2.dp, HudBrown, RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        PixelHudButton(
            text = "CERRAR ×",
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(13.dp),
        )
    }
}

private enum class PhoneStore(val label: String) {
    PETS("MASCOTAS"),
    FARM("ANIMALES DE GRANJA"),
    SEEDS("SEMILLAS Y CAMPO"),
    BIKES("BICICLETAS"),
    TOOLS("HERRAMIENTAS"),
    HELPERS("AYUDANTE"),
    FACTORIES("FÁBRICAS"),
}

private enum class HelperNameMode { HIRE, RENAME }

@Composable
private fun PhoneShop(
    world: FarmWorldState,
    onBuy: (AnimalPurchaseRequest) -> Unit,
    onBuySeeds: () -> Unit,
    onBuyWheatSeeds: () -> Unit,
    onBuyFences: () -> Unit,
    onBuyGates: () -> Unit,
    onBuyBike: () -> Unit,
    onBuyChicken: () -> Unit,
    onBuyTool: (ToolType) -> Unit,
    onBuyBlueprint: (FactoryType) -> Unit,
    onHireHelper: (String) -> Unit,
    onRenameHelper: (String) -> Unit,
    onDismissHelper: () -> Unit,
    onDebugCoins: () -> Unit,
    onClose: () -> Unit,
) {
    var store by remember { mutableStateOf<PhoneStore?>(null) }
    var helperNameMode by remember { mutableStateOf<HelperNameMode?>(null) }
    var helperNameDraft by remember { mutableStateOf("") }
    var confirmDismissHelper by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xE51B2B22))) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(.94f)
                .fillMaxHeight(.88f)
                .background(Color(0xFFF7EBC5), RoundedCornerShape(18.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(18.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF6F9C63), RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("COUPLE MARKET", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("PEDIDOS PARA TU GRANJA", color = Color(0xFFE9F4D7), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
                Text("$ ${world.player.inventory.coins}", color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                    modifier = Modifier.background(HudCream, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
            }

            if (store == null) {
                Text("ELIGE UNA TIENDA", color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorefrontCard(com.example.couplefarm.R.drawable.puppy_gold_sit_down_v7, "MASCOTAS", "Perros y gatos", Color(0xFFE8B9A5)) { store = PhoneStore.PETS }
                    StorefrontCard(com.example.couplefarm.R.drawable.calf_sit_down_v7, "LA GRANJA", "Vacas, cerdos y aves", Color(0xFFBED7A7)) { store = PhoneStore.FARM }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorefrontCard(com.example.couplefarm.R.drawable.tool_seeds_v3, "EL SEMILLERO", "Cultivos y vallas", Color(0xFFF2D690)) { store = PhoneStore.SEEDS }
                    StorefrontCard(com.example.couplefarm.R.drawable.bicycle_parked_v8, "CICLO CIUDAD", "Bicicletas", Color(0xFFAFCFD0)) { store = PhoneStore.BIKES }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorefrontCard(com.example.couplefarm.R.drawable.tool_axe_v3, "FERRETERÍA", "Herramientas de trabajo", Color(0xFFE0C3A0)) { store = PhoneStore.TOOLS }
                    StorefrontCard(
                        com.example.couplefarm.R.drawable.farmer_down_idle_v4,
                        "MANOS EXTRA",
                        world.helper?.name ?: "Contrata un ayudante",
                        Color(0xFFBFD4B1),
                    ) { store = PhoneStore.HELPERS }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorefrontCard(com.example.couplefarm.R.drawable.workshop_bakery_v2, "FÁBRICAS", "Lácteos, pan y empaque", Color(0xFFC7B6DC)) { store = PhoneStore.FACTORIES }
                }
                Text(
                    "El repartidor llevará cada compra hasta el granero.",
                    color = HudGreen,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    PixelHudButton("‹ TIENDAS", onClick = { store = null })
                    Text(store!!.label, color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    when (store) {
                        PhoneStore.PETS -> {
                            Text("CACHORROS", color = HudGreen, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                            PetBreedCatalog.dogs.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    pair.forEach { breed ->
                                        ShopItemButton(breed.previewResource, "${breed.displayName.uppercase()} · $${breed.price}") {
                                            onBuy(AnimalPurchaseRequest(breed.type, breedIndex = breed.breedIndex))
                                        }
                                    }
                                }
                            }
                            Text("GATITOS", color = HudGreen, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                            PetBreedCatalog.cats.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    pair.forEach { breed ->
                                        ShopItemButton(breed.previewResource, "${breed.displayName.uppercase()} · $${breed.price}") {
                                            onBuy(AnimalPurchaseRequest(breed.type, breedIndex = breed.breedIndex))
                                        }
                                    }
                                }
                            }
                        }
                        PhoneStore.FARM -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.calf_sit_down_v7, "TERNERO · $500") { onBuy(AnimalPurchaseRequest(DomesticAnimalType.COW)) }
                                ShopItemButton(com.example.couplefarm.R.drawable.piglet_sit_down_v7, "CERDITO · $260") { onBuy(AnimalPurchaseRequest(DomesticAnimalType.PIG)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.chicken_idle_v2, "GALLINA · $150", onBuyChicken)
                            }
                        }
                        PhoneStore.SEEDS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_seeds_v3, "12 SEM. ZANAHORIA · $48", onBuySeeds)
                                ShopItemButton(com.example.couplefarm.R.drawable.fence_horizontal_v6, "8 VALLAS · $120", onBuyFences)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.item_wheat_seed_v1, "10 SEM. TRIGO · $35", onBuyWheatSeeds)
                                ShopItemButton(com.example.couplefarm.R.drawable.fence_gate_closed_v8, "2 PUERTAS · $60", onBuyGates)
                            }
                        }
                        PhoneStore.BIKES -> ShopItemButton(
                            com.example.couplefarm.R.drawable.bicycle_parked_v8,
                            if (world.bicycle.owned) "COMPRADA" else "BICICLETA · $700",
                            onBuyBike,
                        )
                        PhoneStore.TOOLS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_axe_v3, "HACHA · $120") { onBuyTool(ToolType.AXE) }
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_pickaxe_v4, "PICO · $140") { onBuyTool(ToolType.PICKAXE) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_hoe_v3, "AZADA · $80") { onBuyTool(ToolType.HOE) }
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_watering_v3, "REGADERA · $70") { onBuyTool(ToolType.WATERING_CAN) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_rod_v3, "CAÑA · $150") { onBuyTool(ToolType.FISHING_ROD) }
                                ShopItemButton(com.example.couplefarm.R.drawable.item_machete_v5, "MACHETE · $90") { onBuyTool(ToolType.MACHETE) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_milk_pail_v1, "TARRO VACÍO · $90") { onBuyTool(ToolType.MILK_PAIL) }
                                ShopItemButton(com.example.couplefarm.R.drawable.tool_pencil_v1, "LÁPIZ DISEÑO · $100") { onBuyTool(ToolType.ARCHITECT_PENCIL) }
                            }
                        }
                        PhoneStore.FACTORIES -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.workshop_dairy_v2, "PLANO LÁCTEOS ×1 · $600") { onBuyBlueprint(FactoryType.DAIRY) }
                                ShopItemButton(com.example.couplefarm.R.drawable.workshop_slaughterhouse_v2, "PLANO MATADERO ×1 · $750") { onBuyBlueprint(FactoryType.SLAUGHTERHOUSE) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.workshop_egg_packer_v2, "PLANO EMPAQ. HUEVOS ×1 · $400") { onBuyBlueprint(FactoryType.EGG_PACKER) }
                                ShopItemButton(com.example.couplefarm.R.drawable.workshop_veggie_packer_v2, "PLANO EMPAQ. VERDURAS ×1 · $400") { onBuyBlueprint(FactoryType.VEGGIE_PACKER) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopItemButton(com.example.couplefarm.R.drawable.workshop_bakery_v2, "PLANO PANADERÍA ×1 · $350") { onBuyBlueprint(FactoryType.BAKERY) }
                                ShopItemButton(com.example.couplefarm.R.drawable.workshop_fish_processor_v2, "PLANO FILETEADORA ×1 · $500") { onBuyBlueprint(FactoryType.FISH_PROCESSOR) }
                            }
                        }
                        PhoneStore.HELPERS -> {
                            val helper = world.helper
                            if (helper == null) {
                                Text(
                                    "Contrata a alguien para recoger los huevos y llevarlos al granero durante su jornada.",
                                    color = HudGreen,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                ShopItemButton(
                                    com.example.couplefarm.R.drawable.farmer_down_idle_v4,
                                    "CONTRATAR · $600",
                                ) {
                                    helperNameDraft = ""
                                    helperNameMode = HelperNameMode.HIRE
                                }
                                Text(
                                    "JORNADA · 08:00–17:00",
                                    color = HudBrown,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                )
                            } else {
                                HelperShopStatus(world)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelHudButton("RENOMBRAR", onClick = {
                                        helperNameDraft = helper.name
                                        helperNameMode = HelperNameMode.RENAME
                                    })
                                    PixelHudButton("DESPEDIR", onClick = { confirmDismissHelper = true })
                                }
                            }
                        }
                        null -> Unit
                    }
                }
            }
            if (store == null) PixelHudButton("+ $1000 · PRUEBAS", onClick = onDebugCoins)
        }
        PixelHudButton("CERRAR ×", onClose, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp))

        helperNameMode?.let { mode ->
            AlertDialog(
                onDismissRequest = { helperNameMode = null },
                title = { Text(if (mode == HelperNameMode.HIRE) "Contratar ayudante" else "Cambiar nombre") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (mode == HelperNameMode.HIRE) "Trabajará de 08:00 a 17:00 recogiendo huevos." else "Elige cómo quieres llamarle.",
                        )
                        OutlinedTextField(
                            value = helperNameDraft,
                            onValueChange = { helperNameDraft = it.take(18) },
                            label = { Text("Nombre") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = helperNameDraft.isNotBlank(),
                        onClick = {
                            val name = helperNameDraft.trim()
                            if (name.isNotEmpty()) {
                                if (mode == HelperNameMode.HIRE) onHireHelper(name) else onRenameHelper(name)
                                helperNameMode = null
                            }
                        },
                    ) { Text(if (mode == HelperNameMode.HIRE) "CONTRATAR · $600" else "GUARDAR") }
                },
                dismissButton = { TextButton(onClick = { helperNameMode = null }) { Text("CANCELAR") } },
            )
        }

        if (confirmDismissHelper) {
            AlertDialog(
                onDismissRequest = { confirmDismissHelper = false },
                title = { Text("Despedir ayudante") },
                text = { Text("Dejará de trabajar al confirmar. Esta acción no devuelve el costo de contratación.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDismissHelper = false
                        onDismissHelper()
                    }) { Text("DESPEDIR") }
                },
                dismissButton = { TextButton(onClick = { confirmDismissHelper = false }) { Text("CANCELAR") } },
            )
        }
    }
}

@Composable
private fun HelperShopStatus(world: FarmWorldState) {
    val helper = world.helper ?: return
    val hour = (world.secondsOfDay / 3600f).toInt() % 24
    val onShift = hour in 8 until 17
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8E2B8), RoundedCornerShape(12.dp))
            .border(2.dp, HudBrown, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Image(
            painterResource(com.example.couplefarm.R.drawable.farmer_down_idle_v4),
            contentDescription = null,
            modifier = Modifier.size(62.dp),
        )
        Text(helper.name.uppercase(), color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
        Text(
            if (onShift) "EN TURNO · ${helperActivityLabel(helper.behavior.name)}" else "FUERA DE TURNO",
            color = if (onShift) HudGreen else Color(0xFF7D6549),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
        Text(
            "JORNADA 08:00–17:00 · HUEVOS ${helper.carriedEggs}",
            color = HudBrown,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun StorefrontCard(
    drawable: Int,
    title: String,
    subtitle: String,
    background: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(146.dp)
            .height(112.dp)
            .background(background, RoundedCornerShape(13.dp))
            .border(2.dp, HudBrown, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(drawable), null, Modifier.size(56.dp))
        Text(title, color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 10.sp)
        Text(subtitle, color = HudGreen, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

@Composable
private fun ShopItemButton(drawable: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(122.dp).background(Color(0xFFF9EDC4), RoundedCornerShape(9.dp))
            .border(2.dp, HudBrown, RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(drawable), null, Modifier.size(50.dp))
        Text(label, color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BackpackPanel(
    world: FarmWorldState,
    player: PlayerState = world.player,
    onSelectActiveItem: (ItemType) -> Unit,
    onEquipTool: (FarmTool) -> Unit,
    onOpenBodega: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inventory = player.inventory
    val essentialItems = setOf(ItemType.EGG, ItemType.SEED, ItemType.WHEAT_SEED)
    val entries = ItemType.values().map { item ->
        val icon = when (item) {
            ItemType.EGG -> "🥚"
            ItemType.APPLE -> "🍎"
            ItemType.WOOD -> "🪵"
            ItemType.STONE -> "🪨"
            ItemType.FISH -> "🐟"
            ItemType.FISH_FILLET -> "🐟"
            ItemType.MILK -> "🥛"
            ItemType.MILK_PAIL -> "🪣"
            ItemType.SEED -> "🌱"
            ItemType.WHEAT_SEED -> "🌾"
            ItemType.WHEAT -> "🌾"
            ItemType.CARROT -> "🥕"
            ItemType.FLOUR -> "🥡"
            ItemType.DOUGH -> "🥟"
            ItemType.BREAD -> "🍞"
            ItemType.CHEESE -> "🧀"
            ItemType.MEAT -> "🥩"
            ItemType.EGG_CARTON -> "📦"
            ItemType.VEGGIE_BOX -> "📦"
            ItemType.FENCE -> "🪵"
            ItemType.GATE -> "🚪"
        }
        item to "$icon ${item.displayName}"
    }.filter { (item, _) -> inventory.count(item) > 0 || item in essentialItems }
    val equipment = buildList {
        add(BackpackEquipment(FarmTool.PATH, "Pala de caminos", "🪏", 1))
        listOf(
            FarmTool.BLUEPRINT_DAIRY,
            FarmTool.BLUEPRINT_SLAUGHTERHOUSE,
            FarmTool.BLUEPRINT_EGG_PACKER,
            FarmTool.BLUEPRINT_VEGGIE_PACKER,
            FarmTool.BLUEPRINT_BAKERY,
            FarmTool.BLUEPRINT_FISH_PROCESSOR,
        ).forEach { tool ->
            val count = world.player.toolDurability[tool.toEngineTool()] ?: 0
            if (count > 0) add(BackpackEquipment(tool, tool.displayName, "📐", count))
        }
    }
    Column(
        modifier
            .background(HudCream, RoundedCornerShape(14.dp))
            .border(3.dp, HudBrown, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .widthIn(max = 354.dp)
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("MOCHILA (TOCA PARA EQUIPAR)", color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp)
        entries.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { (itemType, label) ->
                    BackpackItemTile(
                        label = label,
                        count = inventory.count(itemType),
                        selected = world.player.activeItemId == itemType,
                        onClick = { onSelectActiveItem(itemType) },
                    )
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.size(76.dp)) }
            }
        }
        if (equipment.isNotEmpty()) {
            Text("EQUIPO Y PLANOS", color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 10.sp)
            equipment.chunked(4).forEach { rowEquipment ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowEquipment.forEach { entry ->
                        BackpackEquipmentTile(
                            entry = entry,
                            selected = world.player.selectedTool == entry.tool.toEngineTool(),
                            onClick = { onEquipTool(entry.tool) },
                        )
                    }
                    repeat(4 - rowEquipment.size) { Spacer(Modifier.size(76.dp)) }
                }
            }
        }
        Text("MONEDAS  $ ${inventory.coins}", color = HudBrown, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelHudButton("🌾 BODEGA", onOpenBodega)
            PixelHudButton("CERRAR", onClose)
        }
    }
}

private data class BackpackEquipment(
    val tool: FarmTool,
    val label: String,
    val icon: String,
    val count: Int,
)

@Composable
private fun BackpackItemTile(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val split = label.indexOf(' ')
    val icon = if (split > 0) label.substring(0, split) else "📦"
    val name = if (split > 0) label.substring(split + 1) else label
    Column(
        modifier = Modifier
            .size(76.dp)
            .background(if (selected) Color(0xFFCBEBB5) else Color(0xFFF9EDC4), RoundedCornerShape(8.dp))
            .border(if (selected) 3.dp else 2.dp, if (count > 0) HudBrown else Color(0x887A6B55), RoundedCornerShape(8.dp))
            .alpha(if (count > 0) 1f else 0.55f)
            .clickable(enabled = count > 0, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 23.sp)
        Text(name, color = HudGreen, fontWeight = FontWeight.Bold, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 2)
        Text("×$count", color = HudBrown, fontWeight = FontWeight.Black, fontSize = 9.sp)
    }
}

@Composable
private fun BackpackEquipmentTile(
    entry: BackpackEquipment,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(76.dp)
            .background(if (selected) Color(0xFFCBEBB5) else Color(0xFFF9EDC4), RoundedCornerShape(8.dp))
            .border(if (selected) 3.dp else 2.dp, HudBrown, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(entry.icon, fontSize = 22.sp)
        Text(entry.label, color = HudGreen, fontWeight = FontWeight.Bold, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 2)
        Text("×${entry.count}", color = HudBrown, fontWeight = FontWeight.Black, fontSize = 9.sp)
    }
}

@Composable
private fun PetPanel(
    world: FarmWorldState,
    onCommand: (PetCommandRequest) -> Unit,
    onEquipBall: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var petToDelete by remember { mutableStateOf<com.example.couplefarm.game.DomesticAnimalState?>(null) }
    val pets = world.animals.filter { it.type.isPet }
    Column(
        modifier = modifier
            .background(HudCream, RoundedCornerShape(12.dp))
            .border(3.dp, HudBrown, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("MASCOTAS", color = HudBrown, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9EDC4), RoundedCornerShape(8.dp))
                .border(2.dp, HudBrown, RoundedCornerShape(8.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(painterResource(com.example.couplefarm.R.drawable.item_ball_v5), null, Modifier.size(42.dp))
            PixelHudButton(
                if (world.player.selectedTool == ToolType.PET_BALL) "PELOTA EQUIPADA" else "EQUIPAR PELOTA",
                onEquipBall,
            )
        }
        pets.forEach { pet ->
            val petPhoto = when (pet.type) {
                DomesticAnimalType.DOG -> PetBreedCatalog.dogs.firstOrNull { it.breedIndex == pet.breedIndex }?.previewResource
                    ?: com.example.couplefarm.R.drawable.puppy_gold_sit_down_v7
                DomesticAnimalType.CAT -> PetBreedCatalog.cats.firstOrNull { it.breedIndex == pet.breedIndex }?.previewResource
                    ?: com.example.couplefarm.R.drawable.kitten_orange_sit_down_v7
                else -> com.example.couplefarm.R.drawable.puppy_gold_sit_down_v7
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9EDC4), RoundedCornerShape(8.dp))
                    .border(2.dp, HudBrown, RoundedCornerShape(8.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(petPhoto), pet.name, Modifier.size(54.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pet.name, color = HudGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PixelHudButton("SEGUIR", onClick = { onCommand(PetCommandRequest(pet.id, PetCommandType.FOLLOW)) })
                        PixelHudButton("QUIETO", onClick = { onCommand(PetCommandRequest(pet.id, PetCommandType.STAY)) })
                        PixelHudButton("LLAMAR", onClick = { onCommand(PetCommandRequest(pet.id, PetCommandType.CALL)) })
                    }
                    PixelDeleteButton("✕ ELIMINAR", onClick = { petToDelete = pet })
                }
            }
        }
        if (pets.isEmpty()) Text("Aún no tienes mascotas", color = HudBrown, fontSize = 11.sp)
        PixelHudButton("OCULTAR", onClose)
    }

    petToDelete?.let { pet ->
        AlertDialog(
            onDismissRequest = { petToDelete = null },
            title = { Text("Eliminar mascota") },
            text = { Text("¿Deseas despedir y eliminar a ${pet.name} de tu granja? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onCommand(PetCommandRequest(pet.id, PetCommandType.RELEASE))
                    petToDelete = null
                }) {
                    Text("ELIMINAR", color = Color(0xFFC44D34), fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { petToDelete = null }) { Text("CANCELAR") }
            },
        )
    }
}

private fun animalName(world: FarmWorldState, reference: AnimalReference): String =
    if (reference.kind == com.example.couplefarm.game.AnimalKind.CHICKEN) {
        world.chickens.firstOrNull { it.id == reference.id }?.name.orEmpty()
    } else {
        world.animals.firstOrNull { it.id == reference.id }?.name.orEmpty()
    }

@Composable
private fun HudLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = HudBrown,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        modifier = modifier.background(Color(0xEAF8E9B8), RoundedCornerShape(7.dp))
            .border(2.dp, HudBrown, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

@Composable
private fun PixelHudButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        color = HudBrown,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.background(HudCream, RoundedCornerShape(7.dp)).border(2.dp, HudBrown, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp),
    )
}

@Composable
private fun PixelHudIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(HudCream, RoundedCornerShape(7.dp))
            .border(2.dp, HudBrown, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        content()
    }
}

@Composable
private fun PixelDeleteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color(0xFF8D2B1B),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 9.sp,
        modifier = modifier
            .background(Color(0xFFFDE8E4), RoundedCornerShape(6.dp))
            .border(2.dp, Color(0xFFC44D34), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun GearIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 16f
        // 4 cardinal teeth
        drawRect(HudBrown, Offset(6f * u, 1f * u), Size(4f * u, 14f * u))
        drawRect(HudBrown, Offset(1f * u, 6f * u), Size(14f * u, 4f * u))
        // 4 diagonal teeth
        drawRect(HudBrown, Offset(3f * u, 3f * u), Size(10f * u, 10f * u))
        // Inner gear body
        drawCircle(Color(0xFF735639), radius = 5.5f * u, center = center)
        drawCircle(HudBrown, radius = 4f * u, center = center)
        // Center hole
        drawCircle(HudCream, radius = 2f * u, center = center)
    }
}

@Composable
private fun BackpackIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 16f
        // Top handle
        drawRect(HudBrown, Offset(6f * u, 1f * u), Size(4f * u, 2f * u))
        drawRect(HudCream, Offset(7f * u, 2f * u), Size(2f * u, u))
        // Outline of main backpack
        drawRect(HudBrown, Offset(3f * u, 3f * u), Size(10f * u, 12f * u))
        // Pack body
        drawRect(Color(0xFF9E6536), Offset(4f * u, 4f * u), Size(8f * u, 10f * u))
        // Top flap
        drawRect(Color(0xFF7A4A24), Offset(3f * u, 3f * u), Size(10f * u, 4f * u))
        drawRect(HudBrown, Offset(3f * u, 7f * u), Size(10f * u, u))
        // Straps
        drawRect(HudBrown, Offset(5f * u, 4f * u), Size(u, 7f * u))
        drawRect(HudBrown, Offset(10f * u, 4f * u), Size(u, 7f * u))
        // Buckles
        drawRect(Color(0xFFF3C444), Offset(5f * u, 7f * u), Size(u, u))
        drawRect(Color(0xFFF3C444), Offset(10f * u, 7f * u), Size(u, u))
        // Front pocket
        drawRect(HudBrown, Offset(5f * u, 9f * u), Size(6f * u, 4f * u))
        drawRect(Color(0xFF86552B), Offset(6f * u, 10f * u), Size(4f * u, 2f * u))
    }
}

@Composable
private fun BoneIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 16f
        val boneColor = Color(0xFFFBF8EE)
        val boneShadow = Color(0xFFDCD2B6)
        // Shaft outline
        drawRect(HudBrown, Offset(3f * u, 6f * u), Size(10f * u, 4f * u))
        // Left knobs outline
        drawRect(HudBrown, Offset(1f * u, 4f * u), Size(4f * u, 4f * u))
        drawRect(HudBrown, Offset(1f * u, 8f * u), Size(4f * u, 4f * u))
        // Right knobs outline
        drawRect(HudBrown, Offset(11f * u, 4f * u), Size(4f * u, 4f * u))
        drawRect(HudBrown, Offset(11f * u, 8f * u), Size(4f * u, 4f * u))

        // Shaft fill
        drawRect(boneColor, Offset(3f * u, 7f * u), Size(10f * u, 2f * u))
        // Left knobs fill
        drawRect(boneColor, Offset(2f * u, 5f * u), Size(2f * u, 2f * u))
        drawRect(boneColor, Offset(2f * u, 9f * u), Size(2f * u, 2f * u))
        // Right knobs fill
        drawRect(boneColor, Offset(12f * u, 5f * u), Size(2f * u, 2f * u))
        drawRect(boneColor, Offset(12f * u, 9f * u), Size(2f * u, 2f * u))
        // Shadow line
        drawRect(boneShadow, Offset(4f * u, 8f * u), Size(8f * u, u))
    }
}

@Composable
private fun BlinkingFoxWarning(world: FarmWorldState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "foxBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "foxAlpha"
    )
    val dx = world.fox.position.x - world.player.position.x
    val dy = world.fox.position.y - world.player.position.y
    val angleDegrees = kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI.toFloat()

    Box(
        modifier = modifier
            .alpha(alpha)
            .background(Color(0xFFFDE8E4), RoundedCornerShape(7.dp))
            .border(2.dp, Color(0xFFC44D34), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Image(
                painter = painterResource(com.example.couplefarm.R.drawable.fox_warning_v5),
                contentDescription = "¡Zorro en la granja!",
                modifier = Modifier.size(20.dp)
            )
            Text(
                "¡ZORRO!",
                color = Color(0xFF8D2B1B),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp
            )
            FoxDirectionArrow(
                angleDegrees = angleDegrees,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun FoxDirectionArrow(angleDegrees: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.rotate(angleDegrees)) {
        val w = size.width
        val h = size.height
        val arrowColor = Color(0xFFC44D34)
        val path = Path().apply {
            moveTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.38f, h * 0.12f)
            lineTo(w * 0.46f, h * 0.35f)
            lineTo(w * 0.1f, h * 0.35f)
            lineTo(w * 0.1f, h * 0.65f)
            lineTo(w * 0.46f, h * 0.65f)
            lineTo(w * 0.38f, h * 0.88f)
            close()
        }
        drawPath(path, arrowColor)
    }
}

@Composable
private fun SettingsPanel(
    soundEffectsEnabled: Boolean,
    musicEnabled: Boolean,
    showFpsCounter: Boolean,
    lanHostEnabled: Boolean,
    lanHostIp: String,
    connectedFriendName: String?,
    onlineHostEnabled: Boolean,
    onlineRoomCode: String,
    onlineConnectedFriendName: String?,
    isGuestMode: Boolean,
    guestHostAddress: String?,
    onToggleSoundEffects: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleFpsCounter: () -> Unit,
    onToggleLanHost: () -> Unit,
    onToggleOnlineHost: () -> Unit,
    onRegenerateOnlineCode: () -> Unit,
    onExitToMenu: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1B2B22))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(330.dp)
                .background(HudCream, RoundedCornerShape(16.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(16.dp))
                .clickable(enabled = false, onClick = {})
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "⚙ AJUSTES",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
            )

            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFD4C19C)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Música",
                    color = HudBrown,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                AudioHudButton(
                    text = if (musicEnabled) "♪ ENCENDIDA" else "♪ APAGADA",
                    enabled = musicEnabled,
                    onClick = onToggleMusic,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Efectos SFX",
                    color = HudBrown,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                AudioHudButton(
                    text = if (soundEffectsEnabled) "🔊 ACTIVADOS" else "× SILENCIADOS",
                    enabled = soundEffectsEnabled,
                    onClick = onToggleSoundEffects,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mostrar contador de fps",
                        color = HudBrown,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Rendimiento en pantalla",
                        color = Color(0xFF795548),
                        fontSize = 10.sp,
                    )
                }
                AudioHudButton(
                    text = if (showFpsCounter) "✓ VISIBLE" else "× OCULTO",
                    enabled = showFpsCounter,
                    onClick = onToggleFpsCounter,
                )
            }

            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFD4C19C)))

            if (!isGuestMode) {
                // Online 4-digit room section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x221B3322), RoundedCornerShape(10.dp))
                        .border(1.5.dp, if (onlineHostEnabled) Color(0xFF2E6332) else Color(0x44D4C19C), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🌐 Granja Online",
                                color = HudBrown,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                if (onlineHostEnabled) "Sala activa para todo el mundo" else "Juega a distancia sin servidores",
                                color = Color(0xFF795548),
                                fontSize = 10.sp,
                            )
                        }
                        AudioHudButton(
                            text = if (onlineHostEnabled) "🟢 SALA ABIERTA" else "⚪ ABRIR SALA",
                            enabled = onlineHostEnabled,
                            onClick = onToggleOnlineHost,
                        )
                    }

                    if (onlineHostEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E3524), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("CÓDIGO DE TU SALA:", color = Color(0xFFA8E6CF), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text(
                                    onlineRoomCode,
                                    color = Color(0xFFF3B83E),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 3.sp,
                                )
                            }
                            Text(
                                text = "🎲 NUEVO",
                                color = HudBrown,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .background(Color(0xFFFFF4D6), RoundedCornerShape(6.dp))
                                    .clickable(onClick = onRegenerateOnlineCode)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                        val friendStatus = if (onlineConnectedFriendName != null) {
                            "🟢 Amigo conectado: $onlineConnectedFriendName"
                        } else {
                            "⚪ Esperando a que tu amigo ingrese el código..."
                        }
                        Text(
                            friendStatus,
                            color = if (onlineConnectedFriendName != null) Color(0xFF2E6332) else Color(0xFF795548),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFD4C19C)))

                // LAN section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📡 Juego LAN (Wi-Fi)",
                            color = HudBrown,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (lanHostEnabled) {
                            val statusText = if (connectedFriendName != null) "1 amigo: $connectedFriendName" else "0 amigos"
                            Text("IP: $lanHostIp · $statusText", color = Color(0xFF2E6332), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Para jugar en la misma casa", color = Color(0xFF795548), fontSize = 10.sp)
                        }
                    }
                    AudioHudButton(
                        text = if (lanHostEnabled) "🟢 ACTIVO" else "⚪ APAGADO",
                        enabled = lanHostEnabled,
                        onClick = onToggleLanHost,
                    )
                }
            } else {
                Text(
                    "Conectado como Invitado a $guestHostAddress",
                    color = Color(0xFF2E6332),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFD4C19C)))

            PixelHudButton(
                text = "‹ SALIR AL MENÚ PRINCIPAL",
                onClick = onExitToMenu,
                modifier = Modifier.fillMaxWidth(),
            )

            PixelHudButton(
                text = "VOLVER A LA GRANJA",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AudioHudButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (enabled) HudBrown else Color(0xFF846F5D),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 9.sp,
        modifier = Modifier
            .background(if (enabled) Color(0xFFE4F0C5) else Color(0xFFE2D8BD), RoundedCornerShape(7.dp))
            .border(2.dp, HudBrown, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun FishingStatusOverlay(
    fishing: FishingState,
    onHook: () -> Unit,
    onHeld: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (fishing.phase) {
        FishingPhase.NONE -> Unit
        FishingPhase.CASTING, FishingPhase.WAITING_FOR_BITE -> {
            Text(
                if (fishing.phase == FishingPhase.CASTING) "Lanzando…" else "Esperando una picada…",
                color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                modifier = modifier.background(HudCream, RoundedCornerShape(9.dp)).border(3.dp, HudBrown, RoundedCornerShape(9.dp)).padding(14.dp),
            )
        }
        FishingPhase.BITE -> {
            Text(
                "¡PICA!  TOCÁ AQUÍ",
                color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 18.sp,
                modifier = modifier.background(Color(0xFFE06B43), RoundedCornerShape(10.dp)).border(4.dp, HudBrown, RoundedCornerShape(10.dp))
                    .clickable(onClick = onHook).padding(horizontal = 22.dp, vertical = 18.dp),
            )
        }
        FishingPhase.REELING -> {
            Column(
                modifier.background(Color(0xF2F5E5AE), RoundedCornerShape(12.dp)).border(4.dp, HudBrown, RoundedCornerShape(12.dp)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("¡PESCANDO!", color = HudGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                FishingMeter(fishing, onHeld)
                Spacer(Modifier.height(7.dp))
                Text("MANTÉN PARA SUBIR", color = HudBrown, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        FishingPhase.SUCCESS -> HudResult("¡PEZ ATRAPADO!", Color(0xFF5F9348), modifier)
        FishingPhase.FAILED -> HudResult("El pez escapó…", Color(0xFFB85C45), modifier)
    }
}

@Composable
private fun FishingMeter(fishing: FishingState, onHeld: (Boolean) -> Unit) {
    Canvas(
        Modifier.size(width = 92.dp, height = 280.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                onHeld(true)
                try { tryAwaitRelease() } finally { onHeld(false) }
            })
        },
    ) {
        drawRect(HudBrown)
        drawRect(Color(0xFF70B6CF), Offset(7f, 7f), Size(size.width - 14f, size.height - 14f))
        val innerTop = 9f
        val innerHeight = size.height - 18f
        val zoneHeight = innerHeight * .31f
        val zoneY = innerTop + innerHeight * (1f - fishing.catchBarPosition) - zoneHeight / 2f
        drawRect(Color(0xC49ACA5B), Offset(10f, zoneY.coerceIn(innerTop, innerTop + innerHeight - zoneHeight)), Size(size.width - 20f, zoneHeight))
        val fishY = innerTop + innerHeight * (1f - fishing.fishPosition)
        drawOval(Color(0xFFFFC44F), Offset(21f, fishY - 9f), Size(45f, 18f))
        drawCircle(Color(0xFF513822), 2.5f, Offset(57f, fishY - 2f))
        val progressH = innerHeight * fishing.catchProgress
        drawRect(Color(0xFF6BB54B), Offset(size.width - 9f, size.height - 9f - progressH), Size(5f, progressH))
    }
}

@Composable
private fun HudResult(message: String, color: Color, modifier: Modifier) {
    Text(
        message, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
        modifier = modifier.background(color, RoundedCornerShape(9.dp)).border(3.dp, HudBrown, RoundedCornerShape(9.dp)).padding(16.dp),
    )
}

private fun helperActivityLabel(rawBehavior: String): String = when (rawBehavior) {
    "SEEKING_EGG", "TO_EGG", "WALK_TO_EGG" -> "BUSCANDO HUEVOS"
    "CARRYING_EGG", "TO_BARN", "WALK_TO_BARN" -> "AL GRANERO"
    "COLLECTING_EGG", "PICKING_UP" -> "RECOGIENDO"
    "DEPOSITING_EGGS", "DEPOSITING" -> "GUARDANDO"
    "OFF_SHIFT" -> "DESCANSANDO"
    else -> "EN TURNO"
}

private fun ItemType.inventoryFarmTool(): FarmTool = when (this) {
    ItemType.SEED, ItemType.WHEAT_SEED -> FarmTool.SEEDS
    ItemType.FENCE -> FarmTool.FENCE
    ItemType.GATE -> FarmTool.GATE
    ItemType.MILK_PAIL -> FarmTool.MILK_PAIL
    else -> FarmTool.HAND
}

private fun FarmTool.toEngineTool(): ToolType = when (this) {
    FarmTool.HAND -> ToolType.HANDS
    FarmTool.FISHING_ROD -> ToolType.FISHING_ROD
    FarmTool.AXE -> ToolType.AXE
    FarmTool.PICKAXE -> ToolType.PICKAXE
    FarmTool.MACHETE -> ToolType.MACHETE
    FarmTool.PET_BALL -> ToolType.PET_BALL
    FarmTool.PATH -> ToolType.PATH_TOOL
    FarmTool.FENCE -> ToolType.FENCE
    FarmTool.HOE -> ToolType.HOE
    FarmTool.WATERING_CAN -> ToolType.WATERING_CAN
    FarmTool.SEEDS -> ToolType.SEED_BAG
    FarmTool.MILK_PAIL -> ToolType.MILK_PAIL
    FarmTool.ARCHITECT_PENCIL -> ToolType.ARCHITECT_PENCIL
    FarmTool.BLUEPRINT_DAIRY -> ToolType.BLUEPRINT_DAIRY
    FarmTool.BLUEPRINT_SLAUGHTERHOUSE -> ToolType.BLUEPRINT_SLAUGHTERHOUSE
    FarmTool.BLUEPRINT_EGG_PACKER -> ToolType.BLUEPRINT_EGG_PACKER
    FarmTool.BLUEPRINT_VEGGIE_PACKER -> ToolType.BLUEPRINT_VEGGIE_PACKER
    FarmTool.BLUEPRINT_BAKERY -> ToolType.BLUEPRINT_BAKERY
    FarmTool.BLUEPRINT_FISH_PROCESSOR -> ToolType.BLUEPRINT_FISH_PROCESSOR
    FarmTool.GATE -> ToolType.GATE
}

private fun playWorldEvent(event: WorldEvent, sounds: FarmSoundManager, world: FarmWorldState) {
    if (event is WorldEvent.AnimalGrabbed) {
        when (event.animal.kind) {
            com.example.couplefarm.game.AnimalKind.CHICKEN -> sounds.playChickenPickup()
            com.example.couplefarm.game.AnimalKind.COW -> sounds.playCow()
            com.example.couplefarm.game.AnimalKind.PIG -> sounds.playPig()
            com.example.couplefarm.game.AnimalKind.DOG -> sounds.playDog()
            com.example.couplefarm.game.AnimalKind.CAT -> sounds.playCat()
        }
        return
    }
    if (event is WorldEvent.ItemCollected) {
        when (event.item) {
            ItemType.EGG -> sounds.playPickup(FarmPickup.EGG)
            ItemType.APPLE -> sounds.playPickup(FarmPickup.APPLE)
            else -> Unit
        }
        return
    }
    val soundEvent = event as? WorldEvent.Sound ?: return
    val cue = soundEvent.cue
    when (cue) {
        SoundCue.FOOTSTEP_GRASS -> if (world.player.isMounted) sounds.playBicycleRoll(FarmSurface.GRASS) else sounds.playFootstep(FarmSurface.GRASS)
        SoundCue.FOOTSTEP_DIRT, SoundCue.FOOTSTEP_SAND -> if (world.player.isMounted) sounds.playBicycleRoll(FarmSurface.DIRT) else sounds.playFootstep(FarmSurface.DIRT)
        SoundCue.ITEM_PICKUP -> Unit
        SoundCue.TREE_RUSTLE -> sounds.playTreeShake()
        SoundCue.AXE_SWING -> sounds.playAxeSwing()
        SoundCue.AXE_HIT -> sounds.playAxeHit()
        SoundCue.TREE_FALL -> sounds.playTreeFall()
        SoundCue.WATER_BLOCKED, SoundCue.BOBBER_SPLASH -> sounds.playWaterSplash()
        SoundCue.CHICKEN_CLUCK, SoundCue.CHICKEN_FLAP, SoundCue.EGG_LAID, SoundCue.EGG_HATCH -> Unit
        SoundCue.BARN_DEPOSIT -> sounds.playStoreItem()
        SoundCue.TRUCK_ARRIVE -> sounds.playTruckArrive()
        SoundCue.TRUCK_ENGINE -> if (world.truck.phase == TruckPhase.LEAVING) sounds.playTruckDepart() else sounds.playTruckArrive()
        SoundCue.COINS -> sounds.playSale()
        SoundCue.FISHING_CAST -> sounds.playFishingCast()
        SoundCue.FISH_BITE -> sounds.playFishingBite()
        SoundCue.FISHING_REEL, SoundCue.FISH_CAUGHT -> sounds.playFishingReel()
        SoundCue.CROP_HARVEST -> sounds.playHarvest()
        SoundCue.PICKAXE_SWING -> sounds.playPickaxeSwing()
        SoundCue.ROCK_HIT -> sounds.playPickaxeHit()
        SoundCue.ROCK_BREAK -> sounds.playRockBreak()
        SoundCue.SOIL_TILL -> sounds.playHoe()
        SoundCue.SEED_PLANT -> sounds.playPlantSeed()
        SoundCue.WATER_CROP -> sounds.playWatering()
        SoundCue.TOOL_SELECT -> sounds.playToolSelect()
        SoundCue.BICYCLE_BELL -> sounds.playBicycleBell()
        SoundCue.BIRD_CHIRP -> sounds.playBirdChirp(soundEvent.volume)
        SoundCue.BUTTERFLY_FLUTTER -> sounds.playButterflyFlutter(soundEvent.volume)
        else -> Unit
    }
}

@Composable
private fun EggPackerMinigameDialog(
    inventoryEggs: Int,
    placedEggs: Int,
    onAddEgg: () -> Unit,
    onPack: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(HudCream, RoundedCornerShape(16.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "EMPACADORA DE HUEVOS",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
            Text(
                "Coloca 6 huevos en el cartón y séllalo para vender al camión ($120).",
                color = HudGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier
                    .background(Color(0xFFDCC89E), RoundedCornerShape(10.dp))
                    .border(2.dp, HudBrown, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val index = row * 3 + col
                            val filled = index < placedEggs
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(if (filled) Color(0xFFF9EDC4) else Color(0xFFBCA77A), RoundedCornerShape(8.dp))
                                    .border(2.dp, if (filled) Color(0xFFBFA267) else Color(0xFF8F7B54), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (filled) {
                                    Image(
                                        painter = painterResource(com.example.couplefarm.R.drawable.egg_v2),
                                        contentDescription = "Huevo",
                                        modifier = Modifier.size(32.dp),
                                    )
                                } else {
                                    Text("○", color = Color(0xFF8F7B54), fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Huevos en mochila: $inventoryEggs (Colocados: $placedEggs/6)",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )

            if (placedEggs < 6) {
                val canAdd = inventoryEggs > placedEggs
                PixelHudButton(
                    text = if (canAdd) "+ COLOCAR HUEVO" else "FALTAN HUEVOS EN MOCHILA",
                    onClick = { if (canAdd) onAddEgg() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Image(
                    painter = painterResource(com.example.couplefarm.R.drawable.item_egg_carton_v1),
                    contentDescription = "Cartón de huevos",
                    modifier = Modifier.size(54.dp),
                )
                PixelHudButton(
                    text = "📦 SELLAR CARTÓN DE HUEVOS",
                    onClick = onPack,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFC7E294), RoundedCornerShape(7.dp)),
                )
            }

            PixelHudButton(
                text = "CERRAR",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VeggiePackerMinigameDialog(
    inventoryCarrots: Int,
    placedCarrots: Int,
    onAddCarrot: () -> Unit,
    onPack: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(HudCream, RoundedCornerShape(16.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "EMPACADORA DE VERDURAS",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
            Text(
                "Llena el cajón con 5 zanahorias y colócale el fleje para vender al camión ($150).",
                color = HudGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier
                    .background(Color(0xFFBF9C6E), RoundedCornerShape(10.dp))
                    .border(2.dp, HudBrown, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (index in 0 until 5) {
                    val filled = index < placedCarrots
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(if (filled) Color(0xFFE5B581) else Color(0xFF9E7E54), RoundedCornerShape(8.dp))
                            .border(2.dp, if (filled) Color(0xFF8F6536) else Color(0xFF735633), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            Image(
                                painter = painterResource(com.example.couplefarm.R.drawable.item_carrot_v5),
                                contentDescription = "Zanahoria",
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Text("□", color = Color(0xFF735633), fontSize = 16.sp)
                        }
                    }
                }
            }

            Text(
                "Zanahorias en mochila: $inventoryCarrots (Colocadas: $placedCarrots/5)",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )

            if (placedCarrots < 5) {
                val canAdd = inventoryCarrots > placedCarrots
                PixelHudButton(
                    text = if (canAdd) "+ COLOCAR ZANAHORIA" else "FALTAN ZANAHORIAS",
                    onClick = { if (canAdd) onAddCarrot() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Image(
                    painter = painterResource(com.example.couplefarm.R.drawable.item_veggie_box_v1),
                    contentDescription = "Cajón de verduras",
                    modifier = Modifier.size(54.dp),
                )
                PixelHudButton(
                    text = "🏷️ FLEJAR CAJÓN DE VERDURAS",
                    onClick = onPack,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFC7E294), RoundedCornerShape(7.dp)),
                )
            }

            PixelHudButton(
                text = "CERRAR",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BakeryIndoorHud(
    bakery: FactoryBuildingState,
    wheatCount: Int,
    flourCount: Int,
    doughCount: Int,
    breadCount: Int,
    onBackpack: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bakery title & live items pill
        Row(
            modifier = Modifier
                .background(HudCream, RoundedCornerShape(12.dp))
                .border(3.dp, HudBrown, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "🥖 PANADERÍA",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
            )
            Box(Modifier.width(1.dp).height(16.dp).background(HudBrown.copy(alpha = 0.3f)))
            Text("🌾 $wheatCount", color = HudBrown, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("🥣 $flourCount", color = HudBrown, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("🥟 $doughCount", color = HudBrown, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("🍞 $breadCount", color = Color(0xFF8B4513), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }

        // Backpack & Exit buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelHudButton("🎒 MOCHILA", onClick = onBackpack)
            PixelHudButton("🚪 SALIR", onClick = onExit)
        }
    }
}

@Composable
private fun MolcajeteGrindDialog(
    wheatCount: Int,
    onGrindWheat: () -> Unit,
    onClose: () -> Unit,
) {
    var grindProgress by remember { mutableFloatStateOf(0f) }
    var pestleAngle by remember { mutableFloatStateOf(0f) }
    var lastTouchAngle by remember { mutableStateOf<Float?>(null) }

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(HudCream, RoundedCornerShape(16.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "🌾 ESTACIÓN DE MOLIENDA",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
            )

            if (wheatCount <= 0) {
                Text(
                    "⚠️ No tienes trigo en tu inventario. Trae trigo de la granja para molerlo.",
                    color = Color(0xFFC44D34),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    "Gira el palo (tejolote) en círculos para moler el trigo en harina.",
                    color = HudGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(75.dp))
                    .background(Color(0xFF8B857B))
                    .pointerInput(wheatCount) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                lastTouchAngle = kotlin.math.atan2(offset.y - cy, offset.x - cx)
                            },
                            onDragEnd = { lastTouchAngle = null },
                            onDragCancel = { lastTouchAngle = null },
                            onDrag = { change, _ ->
                                if (wheatCount > 0) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val current = kotlin.math.atan2(change.position.y - cy, change.position.x - cx)
                                    lastTouchAngle?.let { prev ->
                                        var diff = current - prev
                                        if (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
                                        if (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
                                        val deg = kotlin.math.abs(diff) * (180f / Math.PI.toFloat())
                                        pestleAngle = (pestleAngle + diff * (180f / Math.PI.toFloat())) % 360f
                                        grindProgress += deg / 6.5f
                                        if (grindProgress >= 100f) {
                                            grindProgress = 0f
                                            onGrindWheat()
                                            onClose()
                                        }
                                    }
                                    lastTouchAngle = current
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.example.couplefarm.R.drawable.minigame_molcajete_v2),
                    contentDescription = "Molcajete de piedra",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(pestleAngle)
                        .background(Color(0xFFD4AF37), RoundedCornerShape(12.dp))
                        .border(2.dp, HudBrown, RoundedCornerShape(12.dp)),
                )
            }

            // Progress bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(Color(0xFFE2D8BD), RoundedCornerShape(6.dp))
                        .border(1.dp, HudBrown, RoundedCornerShape(6.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (grindProgress / 100f).coerceIn(0f, 1f))
                            .background(Color(0xFFDE9833), RoundedCornerShape(6.dp))
                    )
                }
                Text(
                    "Molienda: ${grindProgress.toInt()}%",
                    color = HudBrown,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (wheatCount > 0) {
                PixelHudButton(
                    text = "↺ REVOLVER PALO (+25%)",
                    onClick = {
                        pestleAngle = (pestleAngle + 90f) % 360f
                        grindProgress += 25f
                        if (grindProgress >= 100f) {
                            grindProgress = 0f
                            onGrindWheat()
                            onClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PixelHudButton(
                text = "VOLVER AL TALLER",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DoughKneadDialog(
    flourCount: Int,
    onKneadDough: () -> Unit,
    onClose: () -> Unit,
) {
    var kneadProgress by remember { mutableFloatStateOf(0f) }
    var doughSquishX by remember { mutableFloatStateOf(1f) }
    var doughSquishY by remember { mutableFloatStateOf(1f) }

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(HudCream, RoundedCornerShape(16.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "💧 MESA DE AMASADO",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
            )

            if (flourCount <= 0) {
                Text(
                    "⚠️ No tienes harina. Muele trigo en el molcajete de la izquierda.",
                    color = Color(0xFFC44D34),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    "Desliza tus dedos sobre la masa para amasar y mezclar con agua.",
                    color = HudGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFB88E5E))
                    .pointerInput(flourCount) {
                        detectDragGestures { _, dragAmount ->
                            if (flourCount > 0) {
                                val dist = dragAmount.getDistance()
                                kneadProgress += dist / 5.5f
                                doughSquishX = (1f + (dragAmount.x / 40f)).coerceIn(0.7f, 1.3f)
                                doughSquishY = (1f + (dragAmount.y / 40f)).coerceIn(0.7f, 1.3f)
                                if (kneadProgress >= 100f) {
                                    kneadProgress = 0f
                                    onKneadDough()
                                    onClose()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.example.couplefarm.R.drawable.dough_knead_station_v1),
                    contentDescription = "Mesa de amasado",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Image(
                    painter = painterResource(com.example.couplefarm.R.drawable.item_dough_v1),
                    contentDescription = "Masa elástica",
                    modifier = Modifier
                        .size(50.dp)
                        .scale(doughSquishX, doughSquishY),
                )
            }

            // Progress bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(Color(0xFFE2D8BD), RoundedCornerShape(6.dp))
                        .border(1.dp, HudBrown, RoundedCornerShape(6.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (kneadProgress / 100f).coerceIn(0f, 1f))
                            .background(Color(0xFF6DA5C0), RoundedCornerShape(6.dp))
                    )
                }
                Text(
                    "Elasticidad de masa: ${kneadProgress.toInt()}%",
                    color = HudBrown,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (flourCount > 0) {
                PixelHudButton(
                    text = "💧 AMASAR Y MEZCLAR AGUA (+25%)",
                    onClick = {
                        kneadProgress += 25f
                        if (kneadProgress >= 100f) {
                            kneadProgress = 0f
                            onKneadDough()
                            onClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PixelHudButton(
                text = "VOLVER AL TALLER",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FishProcessorMinigameDialog(
    inventoryFish: Int,
    taps: Int,
    onTapCut: () -> Unit,
    onPack: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(HudCream, RoundedCornerShape(16.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "FILETEADORA DE PESCADO",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )

            Text(
                "Peces en mochila: $inventoryFish",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )

            if (inventoryFish <= 0 && taps == 0) {
                Text(
                    "Necesitas al menos 1 pez fresco para procesar filetes.",
                    color = Color(0xFFC44D34),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            } else {
                val stepName = when {
                    taps < 2 -> "PASO 1: ESCAMAR Y LIMPIAR"
                    taps < 4 -> "PASO 2: CORTE Y FILETEADO"
                    else -> "PASO 3: EMPACAR FILETE FRESCO"
                }
                Text(
                    stepName,
                    color = HudBrown,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Image(
                    painter = painterResource(
                        if (taps < 4) com.example.couplefarm.R.drawable.item_fish_v3 else com.example.couplefarm.R.drawable.item_fish_fillet_v1
                    ),
                    contentDescription = "Fileteadora",
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    if (taps < 4) "Toca para descamar y extraer los lomos ($taps/4)." else "¡Filete limpio y listo para vender al camión ($150)!",
                    color = HudGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Color(0xFFE2D8BD), RoundedCornerShape(7.dp))
                        .border(1.dp, HudBrown, RoundedCornerShape(7.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (taps / 4f).coerceIn(0f, 1f))
                            .background(Color(0xFF4EA5D9), RoundedCornerShape(7.dp))
                    )
                }
                if (taps < 4) {
                    PixelHudButton(
                        text = "🔪 CORTAR Y LIMPIAR ($taps/4)",
                        onClick = onTapCut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PixelHudButton(
                        text = "📦 OBTENER FILETE DE PESCADO",
                        onClick = onPack,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFBCE3F5), RoundedCornerShape(7.dp)),
                    )
                }
            }

            PixelHudButton(
                text = "CERRAR",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BarnBodegaDialog(
    world: FarmWorldState,
    player: PlayerState = world.player,
    onDepositAll: () -> Unit,
    onDepositItem: (ItemType) -> Unit,
    onWithdrawItem: (ItemType, Int) -> Unit,
    onWithdrawEggs: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = 560.dp)
                .background(HudCream, RoundedCornerShape(16.dp))
                .border(4.dp, HudBrown, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "🌾 BODEGA DEL GRANERO",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
            Text(
                "Almacén seguro para todos tus productos y cosechas.",
                color = HudGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            PixelHudButton(
                text = "📥 GUARDAR TODO EN BODEGA",
                onClick = onDepositAll,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFC7E294), RoundedCornerShape(7.dp)),
            )

            // Huevos Sueltos Almacenados en Granero
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFD4C19C)))
            Text(
                "🥚 HUEVOS EN GRANERO (${world.barn.storedEggs}/${world.barn.eggCapacity})",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
            )
            if (world.barn.storedEggs > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF9C4), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "🥚 Huevos: ${world.barn.storedEggs}",
                        color = HudBrown,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (world.barn.storedEggs >= 6) {
                            PixelHudButton("SACAR 6", onClick = { onWithdrawEggs(6) })
                        }
                        if (world.barn.storedEggs >= 10) {
                            PixelHudButton("SACAR 10", onClick = { onWithdrawEggs(10) })
                        }
                        PixelHudButton("SACAR TODO", onClick = { onWithdrawEggs(world.barn.storedEggs) })
                    }
                }
            } else {
                Text(
                    "No hay huevos sueltos en el granero.",
                    color = Color(0xFF7D6549),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Mercaderías y Artículos en Bodega
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFD4C19C)))
            Text(
                "📦 ARTÍCULOS Y CAJAS EN BODEGA",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
            )
            val storedItems = world.barn.storage.filter { it.value > 0 }
            if (storedItems.isEmpty()) {
                Text(
                    "La bodega está vacía.",
                    color = Color(0xFF7D6549),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                for (entry in storedItems.entries) {
                    val item = entry.key
                    val qty = entry.value
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFE2BC), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${item.displayName} ×$qty",
                                color = HudBrown,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PixelHudButton("SACAR 1", onClick = { onWithdrawItem(item, 1) })
                                if (qty >= 5) {
                                    PixelHudButton("SACAR 5", onClick = { onWithdrawItem(item, 5) })
                                }
                                PixelHudButton("TODO", onClick = { onWithdrawItem(item, qty) })
                            }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFD4C19C)))

            Text(
                "🎒 TU MOCHILA",
                color = HudBrown,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
            )
            val backpackItems = player.inventory.items.filter { it.value > 0 }
            if (backpackItems.isEmpty()) {
                Text(
                    "Mochila sin artículos guardables.",
                    color = Color(0xFF7D6549),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                for (entry in backpackItems.entries) {
                    val item = entry.key
                    val qty = entry.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE5D5AA), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${item.displayName} ×$qty",
                            color = HudBrown,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                        PixelHudButton(
                            text = "GUARDAR",
                            onClick = { onDepositItem(item) },
                        )
                    }
                }
            }

            PixelHudButton(
                text = "CERRAR",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

