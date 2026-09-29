package com.example.couplefarm.persistence

import com.example.couplefarm.game.AmbientAnimationState
import com.example.couplefarm.game.AmbientCreatureState
import com.example.couplefarm.game.AmbientCreatureType
import com.example.couplefarm.game.AnimalHandlingState
import com.example.couplefarm.game.AnimalKind
import com.example.couplefarm.game.AnimalReference
import com.example.couplefarm.game.BallPhase
import com.example.couplefarm.game.BallState
import com.example.couplefarm.game.BarnState
import com.example.couplefarm.game.BicycleState
import com.example.couplefarm.game.CameraState
import com.example.couplefarm.game.ChickenBehavior
import com.example.couplefarm.game.ChickenLifeStage
import com.example.couplefarm.game.ChickenState
import com.example.couplefarm.game.CollisionCircle
import com.example.couplefarm.game.CollisionRect
import com.example.couplefarm.game.CollisionShape
import com.example.couplefarm.game.BuildingOrientation
import com.example.couplefarm.game.ConstructionPhase
import com.example.couplefarm.game.CropStage
import com.example.couplefarm.game.CropState
import com.example.couplefarm.game.CropType
import com.example.couplefarm.game.CourierPhase
import com.example.couplefarm.game.DomesticAnimalBehavior
import com.example.couplefarm.game.DomesticAnimalState
import com.example.couplefarm.game.DomesticAnimalType
import com.example.couplefarm.game.DeliveryKind
import com.example.couplefarm.game.DeliveryOrderState
import com.example.couplefarm.game.EggState
import com.example.couplefarm.game.EmoteType
import com.example.couplefarm.game.Facing
import com.example.couplefarm.game.FactoryBuildingState
import com.example.couplefarm.game.FactoryType
import com.example.couplefarm.game.FarmWorldState
import com.example.couplefarm.game.FishingPhase
import com.example.couplefarm.game.FishingState
import com.example.couplefarm.game.FoxPhase
import com.example.couplefarm.game.FoxState
import com.example.couplefarm.game.GrassTuftState
import com.example.couplefarm.game.GroundItemState
import com.example.couplefarm.game.InventoryState
import com.example.couplefarm.game.ItemType
import com.example.couplefarm.game.ObstacleKind
import com.example.couplefarm.game.PathStyle
import com.example.couplefarm.game.PathTileState
import com.example.couplefarm.game.PetFollowState
import com.example.couplefarm.game.PlayerActionAnimationState
import com.example.couplefarm.game.PlayerEmoteState
import com.example.couplefarm.game.PlayerState
import com.example.couplefarm.game.RockSpawnState
import com.example.couplefarm.game.SoilPlotState
import com.example.couplefarm.game.SoilState
import com.example.couplefarm.game.StaticObstacleState
import com.example.couplefarm.game.TerrainRegion
import com.example.couplefarm.game.TerrainType
import com.example.couplefarm.game.ToolType
import com.example.couplefarm.game.TreeLifeState
import com.example.couplefarm.game.TreeSpawnState
import com.example.couplefarm.game.TreeState
import com.example.couplefarm.game.TruckPhase
import com.example.couplefarm.game.TruckState
import com.example.couplefarm.game.Vector2
import com.example.couplefarm.game.WaterBodyState
import com.example.couplefarm.game.WorkerBehavior
import com.example.couplefarm.game.WorkerState
import com.example.couplefarm.game.FenceGateState
import com.example.couplefarm.game.WheelbarrowState
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

internal data class DecodedFarmSave(
    val state: FarmWorldState,
    val savedAtEpochMillis: Long,
    val sourceVersion: Int,
)

/** Explicit, version-tolerant JSON codec. Unknown fields/enums are safely ignored. */
internal object FarmWorldJsonCodec {
    private const val FORMAT = "couplefarm.world"
    private const val CURRENT_VERSION = 4
    private const val MAX_NAME_CHARS = 48

    fun encode(state: FarmWorldState, savedAtEpochMillis: Long): ByteArray {
        val payload = encodeWorld(state).toString()
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("version", CURRENT_VERSION)
            .put("savedAtEpochMillis", savedAtEpochMillis)
            .put("crc32", checksum(payloadBytes))
            .put("payload", payload)
        return envelope.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray, fallback: FarmWorldState): DecodedFarmSave {
        require(bytes.isNotEmpty()) { "Farm save is empty" }
        val envelope = JSONObject(String(bytes, StandardCharsets.UTF_8))
        val isEnvelope = envelope.stringValue("format", "") == FORMAT

        val sourceVersion: Int
        val savedAt: Long
        val worldJson: JSONObject
        if (isEnvelope) {
            sourceVersion = envelope.intValue("version", 0).coerceAtLeast(0)
            savedAt = envelope.longValue("savedAtEpochMillis", 0L).coerceAtLeast(0L)
            val payload = envelope.stringValue("payload", "")
            require(payload.isNotEmpty()) { "Farm save payload is missing" }
            val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
            val expectedChecksum = envelope.stringValue("crc32", "")
            if (expectedChecksum.isNotEmpty()) {
                require(expectedChecksum.equals(checksum(payloadBytes), ignoreCase = true)) {
                    "Farm save checksum mismatch"
                }
            }
            worldJson = JSONObject(payload)
        } else {
            // Migration path for early/raw JSON snapshots and { world: ... } wrappers.
            sourceVersion = envelope.intValue("version", 0).coerceAtLeast(0)
            savedAt = envelope.longValue("savedAtEpochMillis", 0L).coerceAtLeast(0L)
            worldJson = envelope.objectValue("world") ?: envelope
        }

        return DecodedFarmSave(
            state = decodeWorld(worldJson, fallback),
            savedAtEpochMillis = savedAt,
            sourceVersion = sourceVersion,
        )
    }

    private fun checksum(bytes: ByteArray): String = CRC32().run {
        update(bytes)
        value.toString(16).padStart(8, '0')
    }

    private fun encodeWorld(world: FarmWorldState) = JSONObject()
        .put("bounds", encodeRect(world.bounds))
        .put("player", encodePlayer(world.player))
        .put("camera", encodeCamera(world.camera))
        .put("terrainRegions", world.terrainRegions.toJsonArray(::encodeTerrainRegion))
        .put("waterBodies", world.waterBodies.toJsonArray(::encodeWaterBody))
        .put("staticObstacles", world.staticObstacles.toJsonArray(::encodeStaticObstacle))
        .put("grassTufts", world.grassTufts.toJsonArray(::encodeGrassTuft))
        .put("pathTiles", world.pathTiles.toJsonArray(::encodePathTile))
        .put("trees", world.trees.toJsonArray(::encodeTree))
        .put("plots", world.plots.toJsonArray(::encodePlot))
        .put("chickens", world.chickens.toJsonArray(::encodeChicken))
        .put("animals", world.animals.toJsonArray(::encodeDomesticAnimal))
        .put("eggs", world.eggs.toJsonArray(::encodeEgg))
        .put("groundItems", world.groundItems.toJsonArray(::encodeGroundItem))
        .put("barn", encodeBarn(world.barn))
        .put("factories", world.factories.toJsonArray(::encodeFactory))
        .putNullable("helper", world.helper?.let(::encodeWorker))
        .put("truck", encodeTruck(world.truck))
        .put("fishing", encodeFishing(world.fishing))
        .put("ball", encodeBall(world.ball))
        .put("bicycle", encodeBicycle(world.bicycle))
        .put("rockSpawning", encodeRockSpawning(world.rockSpawning))
        .put("treeSpawning", encodeTreeSpawning(world.treeSpawning))
        .put("fox", encodeFox(world.fox))
        .put("deliveries", world.deliveries.toJsonArray(::encodeDelivery))
        .put("ambientCreatures", world.ambientCreatures.toJsonArray(::encodeAmbientCreature))
        .putFloat("ambientCreatureSpawnTimer", world.ambientCreatureSpawnTimer)
        .put("animation", encodeAnimation(world.animation))
        .put("day", world.day)
        .putFloat("secondsOfDay", world.secondsOfDay)
        .put("nextEntityId", world.nextEntityId)
        .put("gates", world.gates.toJsonArray(::encodeGate))
        .put("wheelbarrow", encodeWheelbarrow(world.wheelbarrow))
        .putNullable("guestPlayer", world.guestPlayer?.let(::encodePlayer))
        .putNullable("guestFarmerName", world.guestFarmerName)

    private fun decodeWorld(json: JSONObject, fallback: FarmWorldState): FarmWorldState =
        FarmWorldState(
            bounds = decodeRect(json.objectValue("bounds"), fallback.bounds),
            player = decodePlayer(json.objectValue("player"), fallback.player),
            camera = decodeCamera(json.objectValue("camera"), fallback.camera),
            terrainRegions = json.decodeIdList("terrainRegions", fallback.terrainRegions, { it.id }, ::decodeTerrainRegion)
                .filterNot { it.terrain == TerrainType.DIRT },
            waterBodies = json.decodeIdList("waterBodies", fallback.waterBodies, { it.id }, ::decodeWaterBody),
            staticObstacles = json.decodeIdList("staticObstacles", fallback.staticObstacles, { it.id }, ::decodeStaticObstacle),
            grassTufts = json.decodeIdList("grassTufts", fallback.grassTufts, { it.id }, ::decodeGrassTuft),
            pathTiles = json.decodeIdList("pathTiles", fallback.pathTiles, { it.id }, ::decodePathTile),
            trees = json.decodeIdList("trees", fallback.trees, { it.id }, ::decodeTree),
            plots = json.decodeIdList("plots", fallback.plots, { it.id }, ::decodePlot),
            chickens = json.decodeIdList("chickens", fallback.chickens, { it.id }, ::decodeChicken),
            animals = json.decodeIdList("animals", fallback.animals, { it.id }, ::decodeDomesticAnimal),
            eggs = json.decodeIdList("eggs", fallback.eggs, { it.id }, ::decodeEgg),
            groundItems = json.decodeIdList("groundItems", fallback.groundItems, { it.id }, ::decodeGroundItem),
            barn = decodeBarn(json.objectValue("barn"), fallback.barn),
            factories = json.decodeIdList("factories", fallback.factories, { it.id }, ::decodeFactory),
            helper = json.decodeNullableObject("helper", fallback.helper, ::decodeWorker),
            truck = decodeTruck(json.objectValue("truck"), fallback.truck),
            fishing = decodeFishing(json.objectValue("fishing"), fallback.fishing),
            ball = decodeBall(json.objectValue("ball"), fallback.ball),
            bicycle = decodeBicycle(json.objectValue("bicycle"), fallback.bicycle),
            rockSpawning = decodeRockSpawning(json.objectValue("rockSpawning"), fallback.rockSpawning),
            treeSpawning = decodeTreeSpawning(json.objectValue("treeSpawning"), fallback.treeSpawning),
            fox = decodeFox(json.objectValue("fox"), fallback.fox),
            deliveries = json.decodeIdList("deliveries", fallback.deliveries, { it.id }, ::decodeDelivery),
            ambientCreatures = json.decodeIdList(
                "ambientCreatures",
                fallback.ambientCreatures,
                { it.id },
                ::decodeAmbientCreature,
            ),
            ambientCreatureSpawnTimer = json.finiteFloat(
                "ambientCreatureSpawnTimer",
                fallback.ambientCreatureSpawnTimer,
            ).coerceAtLeast(0f),
            animation = decodeAnimation(json.objectValue("animation"), fallback.animation),
            day = json.intValue("day", fallback.day).coerceAtLeast(1),
            secondsOfDay = json.finiteFloat("secondsOfDay", fallback.secondsOfDay).coerceAtLeast(0f),
            nextEntityId = json.intValue("nextEntityId", fallback.nextEntityId).coerceAtLeast(1),
            gates = json.decodeIdList("gates", fallback.gates, { it.id }, ::decodeGate),
            wheelbarrow = decodeWheelbarrow(json.optJSONObject("wheelbarrow"), fallback.wheelbarrow),
            guestPlayer = json.optJSONObject("guestPlayer")?.let { decodePlayer(it, fallback.player) },
            guestFarmerName = json.optString("guestFarmerName").takeIf { it.isNotBlank() },
        )

    private fun encodeVector(vector: Vector2) = JSONObject()
        .putFloat("x", vector.x)
        .putFloat("y", vector.y)

    private fun decodeVector(json: JSONObject?, fallback: Vector2): Vector2 {
        if (json == null) return fallback
        return Vector2(
            json.finiteFloat("x", fallback.x),
            json.finiteFloat("y", fallback.y),
        )
    }

    private fun encodeShape(shape: CollisionShape): JSONObject = when (shape) {
        is CollisionCircle -> JSONObject()
            .put("type", "circle")
            .put("center", encodeVector(shape.center))
            .putFloat("radius", shape.radius)

        is CollisionRect -> JSONObject()
            .put("type", "rect")
            .putFloat("left", shape.left)
            .putFloat("top", shape.top)
            .putFloat("right", shape.right)
            .putFloat("bottom", shape.bottom)
    }

    private fun decodeShape(json: JSONObject?, fallback: CollisionShape): CollisionShape {
        if (json == null) return fallback
        return when (json.stringValue("type", "")) {
            "circle" -> {
                val old = fallback as? CollisionCircle ?: CollisionCircle(Vector2.ZERO, 0.1f)
                CollisionCircle(
                    decodeVector(json.objectValue("center"), old.center),
                    json.finiteFloat("radius", old.radius).coerceAtLeast(0.001f),
                )
            }

            "rect" -> decodeRect(json, fallback as? CollisionRect ?: CollisionRect(0f, 0f, 0f, 0f))
            else -> fallback
        }
    }

    private fun encodeRect(rect: CollisionRect): JSONObject = encodeShape(rect)

    private fun decodeRect(json: JSONObject?, fallback: CollisionRect): CollisionRect {
        if (json == null) return fallback
        val left = json.finiteFloat("left", fallback.left)
        val top = json.finiteFloat("top", fallback.top)
        val right = json.finiteFloat("right", fallback.right)
        val bottom = json.finiteFloat("bottom", fallback.bottom)
        return if (right >= left && bottom >= top) CollisionRect(left, top, right, bottom) else fallback
    }

    private fun encodeTerrainRegion(value: TerrainRegion) = JSONObject()
        .put("id", value.id)
        .put("terrain", value.terrain.name)
        .put("shape", encodeShape(value.shape))
        .putFloat("visualPhase", value.visualPhase)

    private fun decodeTerrainRegion(json: JSONObject, fallback: TerrainRegion?): TerrainRegion {
        val base = fallback ?: TerrainRegion(0, TerrainType.GRASS, CollisionCircle(Vector2.ZERO, 0.1f))
        return TerrainRegion(
            json.intValue("id", base.id),
            json.enumValue("terrain", base.terrain),
            decodeShape(json.objectValue("shape"), base.shape),
            json.finiteFloat("visualPhase", base.visualPhase),
        )
    }

    private fun encodeWaterBody(value: WaterBodyState) = JSONObject()
        .put("id", value.id)
        .put("shape", encodeShape(value.shape))
        .putFloat("ripplePhase", value.ripplePhase)

    private fun decodeWaterBody(json: JSONObject, fallback: WaterBodyState?): WaterBodyState {
        val base = fallback ?: WaterBodyState(0, CollisionCircle(Vector2.ZERO, 0.1f), 0f)
        return WaterBodyState(
            json.intValue("id", base.id),
            decodeShape(json.objectValue("shape"), base.shape),
            json.finiteFloat("ripplePhase", base.ripplePhase),
        )
    }

    private fun encodeStaticObstacle(value: StaticObstacleState) = JSONObject()
        .put("id", value.id)
        .put("kind", value.kind.name)
        .put("collider", encodeShape(value.collider))
        .putFloat("sortY", value.sortY)
        .put("hitPoints", value.hitPoints)

    private fun decodeStaticObstacle(json: JSONObject, fallback: StaticObstacleState?): StaticObstacleState {
        val base = fallback ?: StaticObstacleState(
            0,
            ObstacleKind.DECORATION,
            CollisionCircle(Vector2.ZERO, 0.1f),
            0f,
        )
        val kind = json.enumValue("kind", base.kind)
        return StaticObstacleState(
            id = json.intValue("id", base.id),
            kind = kind,
            collider = decodeShape(json.objectValue("collider"), base.collider),
            sortY = json.finiteFloat("sortY", base.sortY),
            hitPoints = json.intValue("hitPoints", base.hitPoints).coerceAtLeast(0),
        )
    }

    private fun encodeInventory(value: InventoryState): JSONObject {
        val items = JSONObject()
        ItemType.entries.forEach { item ->
            value.items[item]?.let { items.put(item.name, it) }
        }
        return JSONObject()
            .put("items", items)
            .put("coins", value.coins)
    }

    private fun decodeInventory(json: JSONObject?, fallback: InventoryState): InventoryState {
        if (json == null) return fallback
        val itemObject = json.objectValue("items")
        val items = if (itemObject == null) fallback.items else buildMap {
            ItemType.entries.forEach { item ->
                if (itemObject.has(item.name)) {
                    val count = itemObject.intValue(item.name, 0).coerceAtLeast(0)
                    if (count > 0) put(item, count)
                }
            }
        }
        return InventoryState(
            items = items,
            coins = json.intValue("coins", fallback.coins).coerceAtLeast(0),
        )
    }

    private fun encodeAction(value: PlayerActionAnimationState) = JSONObject()
        .put("tool", value.tool.name)
        .put("targetPosition", encodeVector(value.targetPosition))
        .putFloat("durationSeconds", value.durationSeconds)
        .putFloat("remainingSeconds", value.remainingSeconds)

    private fun decodeAction(json: JSONObject, fallback: PlayerActionAnimationState?): PlayerActionAnimationState {
        val base = fallback ?: PlayerActionAnimationState(ToolType.HANDS, Vector2.ZERO, 0f)
        return PlayerActionAnimationState(
            tool = json.enumValue("tool", base.tool),
            targetPosition = decodeVector(json.objectValue("targetPosition"), base.targetPosition),
            durationSeconds = json.finiteFloat("durationSeconds", base.durationSeconds).coerceAtLeast(0f),
            remainingSeconds = json.finiteFloat("remainingSeconds", base.remainingSeconds).coerceAtLeast(0f),
        )
    }

    private fun encodeEmote(value: PlayerEmoteState) = JSONObject()
        .put("type", value.type.name)
        .putFloat("durationSeconds", value.durationSeconds)
        .putFloat("remainingSeconds", value.remainingSeconds)

    private fun decodeEmote(json: JSONObject, fallback: PlayerEmoteState?): PlayerEmoteState {
        val base = fallback ?: PlayerEmoteState(EmoteType.HAPPY)
        return PlayerEmoteState(
            type = json.enumValue("type", base.type),
            durationSeconds = json.finiteFloat("durationSeconds", base.durationSeconds).coerceAtLeast(0f),
            remainingSeconds = json.finiteFloat("remainingSeconds", base.remainingSeconds).coerceAtLeast(0f),
        )
    }

    private fun encodeAnimalReference(value: AnimalReference) = JSONObject()
        .put("kind", value.kind.name)
        .put("id", value.id)

    private fun decodeAnimalReference(json: JSONObject, fallback: AnimalReference?): AnimalReference {
        val base = fallback ?: AnimalReference(AnimalKind.CHICKEN, 0)
        return AnimalReference(
            kind = json.enumValue("kind", base.kind),
            id = json.intValue("id", base.id),
        )
    }

    private fun encodePlayer(value: PlayerState) = JSONObject()
        .put("position", encodeVector(value.position))
        .put("facing", value.facing.name)
        .put("movementDirection", encodeVector(value.movementDirection))
        .put("isMoving", value.isMoving)
        .put("selectedTool", value.selectedTool.name)
        .put("inventory", encodeInventory(value.inventory))
        .putFloat("actionCooldown", value.actionCooldown)
        .putFloat("footstepTimer", value.footstepTimer)
        .putFloat("blockedSoundCooldown", value.blockedSoundCooldown)
        .putNullable("actionAnimation", value.actionAnimation?.let(::encodeAction))
        .putNullable("activeEmote", value.activeEmote?.let(::encodeEmote))
        .putNullable("carriedAnimal", value.carriedAnimal?.let(::encodeAnimalReference))
        .put("foxBites", value.foxBites)
        .putFloat("faintTimer", value.faintTimer)
        .put("isMounted", value.isMounted)
        .putNullable("activeItemId", value.activeItemId?.name)
        .put("toolDurability", JSONObject().apply {
            value.toolDurability.forEach { (tool, uses) -> put(tool.name, uses) }
        })

    private fun decodePlayer(json: JSONObject?, fallback: PlayerState): PlayerState {
        if (json == null) return fallback
        val action = json.decodeNullableObject("actionAnimation", fallback.actionAnimation, ::decodeAction)
        val emote = json.decodeNullableObject("activeEmote", fallback.activeEmote, ::decodeEmote)
        val carried = json.decodeNullableObject("carriedAnimal", fallback.carriedAnimal, ::decodeAnimalReference)
        val activeItem = json.optString("activeItemId").takeIf { it.isNotBlank() }?.let { runCatching { ItemType.valueOf(it) }.getOrNull() }
        val durabilityObj = json.optJSONObject("toolDurability")
        val toolDurability = if (durabilityObj != null) {
            com.example.couplefarm.game.defaultToolDurabilities.mapValues { (tool, defaultUses) ->
                durabilityObj.optInt(tool.name, defaultUses)
            }
        } else {
            fallback.toolDurability
        }
        return PlayerState(
            position = decodeVector(json.objectValue("position"), fallback.position),
            facing = json.enumValue("facing", fallback.facing),
            movementDirection = decodeVector(json.objectValue("movementDirection"), fallback.movementDirection),
            isMoving = json.booleanValue("isMoving", fallback.isMoving),
            selectedTool = json.enumValue("selectedTool", fallback.selectedTool),
            inventory = decodeInventory(json.objectValue("inventory"), fallback.inventory),
            actionCooldown = json.finiteFloat("actionCooldown", fallback.actionCooldown).coerceAtLeast(0f),
            footstepTimer = json.finiteFloat("footstepTimer", fallback.footstepTimer).coerceAtLeast(0f),
            blockedSoundCooldown = json.finiteFloat("blockedSoundCooldown", fallback.blockedSoundCooldown).coerceAtLeast(0f),
            actionAnimation = action,
            activeEmote = emote,
            carriedAnimal = carried,
            foxBites = json.intValue("foxBites", fallback.foxBites).coerceAtLeast(0),
            faintTimer = json.finiteFloat("faintTimer", fallback.faintTimer).coerceAtLeast(0f),
            isMounted = json.booleanValue("isMounted", fallback.isMounted),
            toolDurability = toolDurability,
            activeItemId = activeItem ?: fallback.activeItemId,
        )
    }

    private fun encodeCamera(value: CameraState) = JSONObject()
        .put("center", encodeVector(value.center))
        .putFloat("zoom", value.zoom)
        .put("baseViewportWorldSize", encodeVector(value.baseViewportWorldSize))

    private fun decodeCamera(json: JSONObject?, fallback: CameraState): CameraState {
        if (json == null) return fallback
        return CameraState(
            center = decodeVector(json.objectValue("center"), fallback.center),
            zoom = json.finiteFloat("zoom", fallback.zoom).coerceAtLeast(0.01f),
            baseViewportWorldSize = decodeVector(
                json.objectValue("baseViewportWorldSize"),
                fallback.baseViewportWorldSize,
            ),
        )
    }

    private fun encodeTree(value: TreeState) = JSONObject()
        .put("id", value.id)
        .put("position", encodeVector(value.position))
        .putFloat("trunkRadius", value.trunkRadius)
        .putFloat("canopyRadius", value.canopyRadius)
        .put("lifeState", value.lifeState.name)
        .put("health", value.health)
        .putFloat("shakeTimer", value.shakeTimer)
        .putFloat("shakeCooldown", value.shakeCooldown)
        .putFloat("fallProgress", value.fallProgress)
        .put("fallDirection", encodeVector(value.fallDirection))
        .putFloat("windPhase", value.windPhase)

    private fun decodeTree(json: JSONObject, fallback: TreeState?): TreeState {
        val base = fallback ?: TreeState(0, Vector2.ZERO)
        return TreeState(
            id = json.intValue("id", base.id),
            position = decodeVector(json.objectValue("position"), base.position),
            trunkRadius = json.finiteFloat("trunkRadius", base.trunkRadius).coerceAtLeast(0.01f),
            canopyRadius = json.finiteFloat("canopyRadius", base.canopyRadius).coerceAtLeast(0.01f),
            lifeState = json.enumValue("lifeState", base.lifeState),
            health = json.intValue("health", base.health).coerceAtLeast(0),
            shakeTimer = json.finiteFloat("shakeTimer", base.shakeTimer).coerceAtLeast(0f),
            shakeCooldown = json.finiteFloat("shakeCooldown", base.shakeCooldown).coerceAtLeast(0f),
            fallProgress = json.finiteFloat("fallProgress", base.fallProgress).coerceIn(0f, 1f),
            fallDirection = decodeVector(json.objectValue("fallDirection"), base.fallDirection),
            windPhase = json.finiteFloat("windPhase", base.windPhase),
        )
    }

    private fun encodeCrop(value: CropState) = JSONObject()
        .put("type", value.type.name)
        .putFloat("growthSeconds", value.growthSeconds)
        .put("stage", value.stage.name)

    private fun decodeCrop(json: JSONObject, fallback: CropState?): CropState {
        val base = fallback ?: CropState(CropType.CARROT)
        return CropState(
            type = json.enumValue("type", base.type),
            growthSeconds = json.finiteFloat("growthSeconds", base.growthSeconds).coerceAtLeast(0f),
            stage = json.enumValue("stage", base.stage),
        )
    }

    private fun encodePlot(value: SoilPlotState) = JSONObject()
        .put("id", value.id)
        .put("position", encodeVector(value.position))
        .putFloat("size", value.size)
        .put("soil", value.soil.name)
        .putFloat("moisture", value.moisture)
        .putNullable("crop", value.crop?.let(::encodeCrop))
        .putFloat("tramplingSeconds", value.tramplingSeconds)

    private fun decodePlot(json: JSONObject, fallback: SoilPlotState?): SoilPlotState {
        val base = fallback ?: SoilPlotState(0, Vector2.ZERO)
        return SoilPlotState(
            id = json.intValue("id", base.id),
            position = decodeVector(json.objectValue("position"), base.position),
            size = json.finiteFloat("size", base.size).coerceAtLeast(0.05f),
            soil = json.enumValue("soil", base.soil),
            moisture = json.finiteFloat("moisture", base.moisture).coerceIn(0f, 1f),
            crop = json.decodeNullableObject("crop", base.crop, ::decodeCrop),
            tramplingSeconds = json.finiteFloat("tramplingSeconds", base.tramplingSeconds).coerceAtLeast(0f),
        )
    }

    private fun encodeChicken(value: ChickenState) = JSONObject()
        .put("id", value.id)
        .put("position", encodeVector(value.position))
        .put("name", value.name)
        .put("velocity", encodeVector(value.velocity))
        .put("facing", value.facing.name)
        .put("lifeStage", value.lifeStage.name)
        .putFloat("ageSeconds", value.ageSeconds)
        .put("behavior", value.behavior.name)
        .putFloat("behaviorTimer", value.behaviorTimer)
        .put("desiredDirection", encodeVector(value.desiredDirection))
        .putFloat("animationTime", value.animationTime)
        .putFloat("animationPhase", value.animationPhase)
        .putFloat("eggTimer", value.eggTimer)
        .putFloat("cluckTimer", value.cluckTimer)
        .put("handling", value.handling.name)
        .putFloat("thrownTimer", value.thrownTimer)

    private fun decodeChicken(json: JSONObject, fallback: ChickenState?): ChickenState {
        val base = fallback ?: ChickenState(0, Vector2.ZERO)
        return ChickenState(
            id = json.intValue("id", base.id),
            position = decodeVector(json.objectValue("position"), base.position),
            name = json.stringValue("name", base.name).take(MAX_NAME_CHARS),
            velocity = decodeVector(json.objectValue("velocity"), base.velocity),
            facing = json.enumValue("facing", base.facing),
            lifeStage = json.enumValue("lifeStage", base.lifeStage),
            ageSeconds = json.finiteFloat("ageSeconds", base.ageSeconds).coerceAtLeast(0f),
            behavior = json.enumValue("behavior", base.behavior),
            behaviorTimer = json.finiteFloat("behaviorTimer", base.behaviorTimer).coerceAtLeast(0f),
            desiredDirection = decodeVector(json.objectValue("desiredDirection"), base.desiredDirection),
            animationTime = json.finiteFloat("animationTime", base.animationTime).coerceAtLeast(0f),
            animationPhase = json.finiteFloat("animationPhase", base.animationPhase),
            eggTimer = json.finiteFloat("eggTimer", base.eggTimer).coerceAtLeast(0f),
            cluckTimer = json.finiteFloat("cluckTimer", base.cluckTimer).coerceAtLeast(0f),
            handling = json.enumValue("handling", base.handling),
            thrownTimer = json.finiteFloat("thrownTimer", base.thrownTimer).coerceAtLeast(0f),
        )
    }

    private fun encodeDomesticAnimal(value: DomesticAnimalState) = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("type", value.type.name)
        .put("position", encodeVector(value.position))
        .put("velocity", encodeVector(value.velocity))
        .put("facing", value.facing.name)
        .put("behavior", value.behavior.name)
        .putFloat("behaviorTimer", value.behaviorTimer)
        .put("desiredDirection", encodeVector(value.desiredDirection))
        .putFloat("animationTime", value.animationTime)
        .putFloat("animationPhase", value.animationPhase)
        .put("handling", value.handling.name)
        .putFloat("thrownTimer", value.thrownTimer)
        .putNullable("petFollowState", value.petFollowState?.name)
        .putFloat("petCallTimer", value.petCallTimer)
        .putFloat("milkReadyInSeconds", value.milkReadyInSeconds)
        .put("breedIndex", value.breedIndex)
        .put("lifeStage", value.lifeStage.name)
        .putFloat("ageSeconds", value.ageSeconds)
        .putFloat("specialCooldown", value.specialCooldown)

    private fun decodeDomesticAnimal(json: JSONObject, fallback: DomesticAnimalState?): DomesticAnimalState {
        val base = fallback ?: DomesticAnimalState(0, "Animal", DomesticAnimalType.COW, Vector2.ZERO)
        return DomesticAnimalState(
            id = json.intValue("id", base.id),
            name = json.stringValue("name", base.name).take(MAX_NAME_CHARS),
            type = json.enumValue("type", base.type),
            position = decodeVector(json.objectValue("position"), base.position),
            velocity = decodeVector(json.objectValue("velocity"), base.velocity),
            facing = json.enumValue("facing", base.facing),
            behavior = json.enumValue("behavior", base.behavior),
            behaviorTimer = json.finiteFloat("behaviorTimer", base.behaviorTimer).coerceAtLeast(0f),
            desiredDirection = decodeVector(json.objectValue("desiredDirection"), base.desiredDirection),
            animationTime = json.finiteFloat("animationTime", base.animationTime).coerceAtLeast(0f),
            animationPhase = json.finiteFloat("animationPhase", base.animationPhase),
            handling = json.enumValue("handling", base.handling),
            thrownTimer = json.finiteFloat("thrownTimer", base.thrownTimer).coerceAtLeast(0f),
            petFollowState = json.nullableEnumValue("petFollowState", base.petFollowState),
            petCallTimer = json.finiteFloat("petCallTimer", base.petCallTimer).coerceAtLeast(0f),
            milkReadyInSeconds = json.floatValue("milkReadyInSeconds", base.milkReadyInSeconds),
            breedIndex = json.intValue("breedIndex", base.breedIndex).coerceAtLeast(0),
            lifeStage = json.enumValue("lifeStage", base.lifeStage),
            ageSeconds = json.finiteFloat("ageSeconds", base.ageSeconds).coerceAtLeast(0f),
            specialCooldown = json.finiteFloat("specialCooldown", base.specialCooldown).coerceAtLeast(0f),
        )
    }

    private fun encodeDelivery(value: DeliveryOrderState) = JSONObject()
        .put("id", value.id)
        .put("kind", value.kind.name)
        .putNullable("animalType", value.animalType?.name)
        .putNullable("toolType", value.toolType?.name)
        .putNullable("factoryType", value.factoryType?.name)
        .put("breedIndex", value.breedIndex)
        .put("amount", value.amount)
        .putFloat("secondsRemaining", value.secondsRemaining)
        .put("delivered", value.delivered)
        .put("courierPhase", value.courierPhase.name)
        .put("courierPosition", encodeVector(value.courierPosition))
        .put("courierFacing", value.courierFacing.name)
        .put("courierRouteIndex", value.courierRouteIndex)
        .putFloat("courierAnimationTime", value.courierAnimationTime)

    private fun decodeDelivery(json: JSONObject, fallback: DeliveryOrderState?): DeliveryOrderState {
        val base = fallback ?: DeliveryOrderState(0, DeliveryKind.SEEDS)
        return DeliveryOrderState(
            id = json.intValue("id", base.id),
            kind = json.enumValue("kind", base.kind),
            animalType = json.nullableEnumValue("animalType", base.animalType),
            toolType = json.nullableEnumValue("toolType", base.toolType),
            factoryType = json.nullableEnumValue("factoryType", base.factoryType),
            breedIndex = json.intValue("breedIndex", base.breedIndex).coerceAtLeast(0),
            amount = json.intValue("amount", base.amount).coerceAtLeast(1),
            secondsRemaining = json.finiteFloat("secondsRemaining", base.secondsRemaining),
            delivered = json.booleanValue("delivered", base.delivered),
            courierPhase = json.enumValue("courierPhase", base.courierPhase),
            courierPosition = decodeVector(json.objectValue("courierPosition"), base.courierPosition),
            courierFacing = json.enumValue("courierFacing", base.courierFacing),
            courierRouteIndex = json.intValue("courierRouteIndex", base.courierRouteIndex),
            courierAnimationTime = json.finiteFloat("courierAnimationTime", base.courierAnimationTime).coerceAtLeast(0f),
        )
    }

    private fun encodeAmbientCreature(value: AmbientCreatureState) = JSONObject()
        .put("id", value.id)
        .put("type", value.type.name)
        .put("position", encodeVector(value.position))
        .put("velocity", encodeVector(value.velocity))
        .put("facing", value.facing.name)
        .putFloat("animationTime", value.animationTime)
        .putFloat("lifeTimer", value.lifeTimer)
        .putFloat("movementTimer", value.movementTimer)
        .putFloat("soundCooldown", value.soundCooldown)

    private fun decodeAmbientCreature(json: JSONObject, fallback: AmbientCreatureState?): AmbientCreatureState {
        val base = fallback ?: AmbientCreatureState(
            id = 0,
            type = AmbientCreatureType.BIRD,
            position = Vector2.ZERO,
        )
        return AmbientCreatureState(
            id = json.intValue("id", base.id),
            type = json.enumValue("type", base.type),
            position = decodeVector(json.objectValue("position"), base.position),
            velocity = decodeVector(json.objectValue("velocity"), base.velocity),
            facing = json.enumValue("facing", base.facing),
            animationTime = json.finiteFloat("animationTime", base.animationTime).coerceAtLeast(0f),
            lifeTimer = json.finiteFloat("lifeTimer", base.lifeTimer).coerceAtLeast(0f),
            movementTimer = json.finiteFloat("movementTimer", base.movementTimer).coerceAtLeast(0f),
            soundCooldown = json.finiteFloat("soundCooldown", base.soundCooldown).coerceAtLeast(0f),
        )
    }

    private fun encodeEgg(value: EggState) = JSONObject()
        .put("id", value.id)
        .put("position", encodeVector(value.position))
        .putFloat("ageSeconds", value.ageSeconds)
        .put("fertile", value.fertile)
        .putFloat("hatchAtSeconds", value.hatchAtSeconds)
        .putFloat("animationPhase", value.animationPhase)

    private fun decodeEgg(json: JSONObject, fallback: EggState?): EggState {
        val base = fallback ?: EggState(0, Vector2.ZERO)
        return EggState(
            id = json.intValue("id", base.id),
            position = decodeVector(json.objectValue("position"), base.position),
            ageSeconds = json.finiteFloat("ageSeconds", base.ageSeconds).coerceAtLeast(0f),
            fertile = json.booleanValue("fertile", base.fertile),
            hatchAtSeconds = json.floatValue("hatchAtSeconds", base.hatchAtSeconds),
            animationPhase = json.finiteFloat("animationPhase", base.animationPhase),
        )
    }

    private fun encodeGroundItem(value: GroundItemState) = JSONObject()
        .put("id", value.id)
        .put("item", value.item.name)
        .put("position", encodeVector(value.position))
        .put("amount", value.amount)
        .putFloat("animationPhase", value.animationPhase)

    private fun decodeGroundItem(json: JSONObject, fallback: GroundItemState?): GroundItemState {
        val base = fallback ?: GroundItemState(0, ItemType.WOOD, Vector2.ZERO)
        return GroundItemState(
            id = json.intValue("id", base.id),
            item = json.enumValue("item", base.item),
            position = decodeVector(json.objectValue("position"), base.position),
            amount = json.intValue("amount", base.amount).coerceAtLeast(1),
            animationPhase = json.finiteFloat("animationPhase", base.animationPhase),
        )
    }

    private fun encodeBarn(value: BarnState): JSONObject {
        val storageObj = JSONObject()
        value.storage.forEach { (item, count) ->
            storageObj.put(item.name, count)
        }
        return JSONObject()
            .put("id", value.id)
            .put("collider", encodeRect(value.collider))
            .put("doorPosition", encodeVector(value.doorPosition))
            .put("storedEggs", value.storedEggs)
            .put("eggCapacity", value.eggCapacity)
            .put("storage", storageObj)
    }

    private fun decodeBarn(json: JSONObject?, fallback: BarnState): BarnState {
        if (json == null) return fallback
        val capacity = json.intValue("eggCapacity", fallback.eggCapacity).coerceAtLeast(0)
        val decodedCollider = decodeRect(json.objectValue("collider"), fallback.collider)
        val decodedDoor = decodeVector(json.objectValue("doorPosition"), fallback.doorPosition)
        val isOldPosition = decodedCollider.left < 20f
        val finalCollider = if (isOldPosition) CollisionRect(26.75f, 4.5f, 37.25f, 13.2f) else decodedCollider
        val finalDoor = if (isOldPosition) Vector2(32f, 13.75f) else decodedDoor
        val storageObj = json.optJSONObject("storage")
        val storageMap = mutableMapOf<ItemType, Int>()
        if (storageObj != null) {
            val keys = storageObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = runCatching { ItemType.valueOf(key) }.getOrNull()
                if (item != null) {
                    storageMap[item] = storageObj.optInt(key, 0)
                }
            }
        }
        return BarnState(
            id = json.intValue("id", fallback.id),
            collider = finalCollider,
            doorPosition = finalDoor,
            storedEggs = json.intValue("storedEggs", fallback.storedEggs).coerceIn(0, capacity),
            eggCapacity = capacity,
            storage = if (storageMap.isNotEmpty()) storageMap else fallback.storage,
        )
    }

    private fun encodePathTile(value: PathTileState) = JSONObject()
        .put("id", value.id)
        .put("cellX", value.cellX)
        .put("cellY", value.cellY)
        .put("style", value.style.name)

    private fun decodePathTile(json: JSONObject, fallback: PathTileState?): PathTileState {
        val base = fallback ?: PathTileState(0, 0, 0)
        return PathTileState(
            id = json.intValue("id", base.id),
            cellX = json.intValue("cellX", base.cellX),
            cellY = json.intValue("cellY", base.cellY),
            style = json.enumValue("style", base.style),
        )
    }

    private fun encodeGate(value: FenceGateState) = JSONObject()
        .put("id", value.id)
        .put("cellX", value.cellX)
        .put("cellY", value.cellY)
        .put("isOpen", value.isOpen)

    private fun decodeGate(json: JSONObject, fallback: FenceGateState?): FenceGateState {
        val base = fallback ?: FenceGateState(0, 0, 0)
        return FenceGateState(
            id = json.intValue("id", base.id),
            cellX = json.intValue("cellX", base.cellX),
            cellY = json.intValue("cellY", base.cellY),
            isOpen = json.booleanValue("isOpen", base.isOpen),
        )
    }

    private fun encodeWheelbarrow(value: WheelbarrowState): JSONObject {
        val cargoObj = JSONObject()
        value.cargo.forEach { (item, count) -> cargoObj.put(item.name, count) }
        return JSONObject()
            .put("id", value.id)
            .put("position", encodeVector(value.position))
            .put("isBeingPushed", value.isBeingPushed)
            .put("cargo", cargoObj)
            .put("capacity", value.capacity)
    }

    private fun decodeWheelbarrow(json: JSONObject?, fallback: WheelbarrowState): WheelbarrowState {
        if (json == null) return fallback
        val cargoObj = json.optJSONObject("cargo")
        val cargoMap = mutableMapOf<ItemType, Int>()
        if (cargoObj != null) {
            val keys = cargoObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = runCatching { ItemType.valueOf(key) }.getOrNull()
                if (item != null) cargoMap[item] = cargoObj.optInt(key, 0)
            }
        }
        return WheelbarrowState(
            id = json.intValue("id", fallback.id),
            position = decodeVector(json.optJSONObject("position"), fallback.position),
            isBeingPushed = json.booleanValue("isBeingPushed", fallback.isBeingPushed),
            cargo = if (cargoMap.isNotEmpty()) cargoMap else fallback.cargo,
            capacity = json.intValue("capacity", fallback.capacity),
        )
    }

    private fun encodeFactory(value: FactoryBuildingState) = JSONObject()
        .put("id", value.id)
        .put("type", value.type.name)
        .put("cellX", value.cellX)
        .put("cellY", value.cellY)
        .put("widthCells", value.widthCells)
        .put("heightCells", value.heightCells)
        .put("orientation", value.orientation.name)
        .put("phase", value.phase.name)
        .put("woodContributed", value.woodContributed)
        .put("stoneContributed", value.stoneContributed)
        .putFloat("productionTimer", value.productionTimer)
        .put("hasPigInChamber", value.hasPigInChamber)
        .put("hasCowInChamber", value.hasCowInChamber)
        .put("storedInputs", value.storedInputs)
        .put("storedOutputs", value.storedOutputs)
        .putFloat("oven1Timer", value.oven1Timer)
        .put("oven1Active", value.oven1Active)
        .put("oven1Ready", value.oven1Ready)
        .putFloat("oven2Timer", value.oven2Timer)
        .put("oven2Active", value.oven2Active)
        .put("oven2Ready", value.oven2Ready)

    private fun decodeFactory(json: JSONObject, fallback: FactoryBuildingState?): FactoryBuildingState {
        val base = fallback ?: FactoryBuildingState(0, FactoryType.DAIRY, 0, 0)
        val type = json.enumValue("type", base.type)
        return FactoryBuildingState(
            id = json.intValue("id", base.id),
            type = type,
            cellX = json.intValue("cellX", base.cellX),
            cellY = json.intValue("cellY", base.cellY),
            widthCells = json.intValue("widthCells", type.defaultWidth),
            heightCells = json.intValue("heightCells", type.defaultHeight),
            orientation = json.enumValue("orientation", base.orientation),
            phase = json.enumValue("phase", base.phase),
            woodContributed = json.intValue("woodContributed", base.woodContributed),
            stoneContributed = json.intValue("stoneContributed", base.stoneContributed),
            productionTimer = json.finiteFloat("productionTimer", base.productionTimer),
            hasPigInChamber = json.booleanValue("hasPigInChamber", base.hasPigInChamber),
            hasCowInChamber = json.booleanValue("hasCowInChamber", base.hasCowInChamber),
            storedInputs = json.intValue("storedInputs", base.storedInputs),
            storedOutputs = json.intValue("storedOutputs", base.storedOutputs),
            oven1Timer = json.finiteFloat("oven1Timer", base.oven1Timer),
            oven1Active = json.booleanValue("oven1Active", base.oven1Active),
            oven1Ready = json.booleanValue("oven1Ready", base.oven1Ready),
            oven2Timer = json.finiteFloat("oven2Timer", base.oven2Timer),
            oven2Active = json.booleanValue("oven2Active", base.oven2Active),
            oven2Ready = json.booleanValue("oven2Ready", base.oven2Ready),
        )
    }

    private fun encodeWorker(value: WorkerState) = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("position", encodeVector(value.position))
        .put("velocity", encodeVector(value.velocity))
        .put("facing", value.facing.name)
        .put("behavior", value.behavior.name)
        .putNullable("targetEggId", value.targetEggId)
        .put("carriedEggs", value.carriedEggs)
        .put("shiftStartHour", value.shiftStartHour)
        .put("shiftEndHour", value.shiftEndHour)
        .putFloat("animationTime", value.animationTime)

    private fun decodeWorker(json: JSONObject, fallback: WorkerState?): WorkerState {
        val base = fallback ?: WorkerState(0, "Ayudante", Vector2.ZERO)
        return WorkerState(
            id = json.intValue("id", base.id),
            name = json.stringValue("name", base.name).take(MAX_NAME_CHARS),
            position = decodeVector(json.objectValue("position"), base.position),
            velocity = decodeVector(json.objectValue("velocity"), base.velocity),
            facing = json.enumValue("facing", base.facing),
            behavior = json.enumValue("behavior", base.behavior),
            targetEggId = json.nullableInt("targetEggId", base.targetEggId),
            carriedEggs = json.intValue("carriedEggs", base.carriedEggs).coerceAtLeast(0),
            shiftStartHour = json.intValue("shiftStartHour", base.shiftStartHour).coerceIn(0, 23),
            shiftEndHour = json.intValue("shiftEndHour", base.shiftEndHour).coerceIn(0, 23),
            animationTime = json.finiteFloat("animationTime", base.animationTime).coerceAtLeast(0f),
        )
    }

    private fun encodeTruck(value: TruckState) = JSONObject()
        .put("id", value.id)
        .put("phase", value.phase.name)
        .put("position", encodeVector(value.position))
        .putFloat("phaseTimer", value.phaseTimer)
        .put("eggsLoaded", value.eggsLoaded)
        .put("saleCompletedThisVisit", value.saleCompletedThisVisit)
        .put("lastSaleCoins", value.lastSaleCoins)

    private fun decodeTruck(json: JSONObject?, fallback: TruckState): TruckState {
        if (json == null) return fallback
        return TruckState(
            id = json.intValue("id", fallback.id),
            phase = json.enumValue("phase", fallback.phase),
            position = decodeVector(json.objectValue("position"), fallback.position),
            phaseTimer = json.finiteFloat("phaseTimer", fallback.phaseTimer).coerceAtLeast(0f),
            eggsLoaded = json.intValue("eggsLoaded", fallback.eggsLoaded).coerceAtLeast(0),
            saleCompletedThisVisit = json.booleanValue("saleCompletedThisVisit", fallback.saleCompletedThisVisit),
            lastSaleCoins = json.intValue("lastSaleCoins", fallback.lastSaleCoins).coerceAtLeast(0),
        )
    }

    private fun encodeFishing(value: FishingState) = JSONObject()
        .put("phase", value.phase.name)
        .putNullable("waterBodyId", value.waterBodyId)
        .put("bobberPosition", encodeVector(value.bobberPosition))
        .putFloat("phaseTimer", value.phaseTimer)
        .putFloat("biteDelay", value.biteDelay)
        .putFloat("fishPosition", value.fishPosition)
        .putFloat("fishVelocity", value.fishVelocity)
        .putFloat("catchBarPosition", value.catchBarPosition)
        .putFloat("catchBarVelocity", value.catchBarVelocity)
        .putFloat("catchProgress", value.catchProgress)

    private fun decodeFishing(json: JSONObject?, fallback: FishingState): FishingState {
        if (json == null) return fallback
        return FishingState(
            phase = json.enumValue("phase", fallback.phase),
            waterBodyId = json.nullableInt("waterBodyId", fallback.waterBodyId),
            bobberPosition = decodeVector(json.objectValue("bobberPosition"), fallback.bobberPosition),
            phaseTimer = json.finiteFloat("phaseTimer", fallback.phaseTimer).coerceAtLeast(0f),
            biteDelay = json.finiteFloat("biteDelay", fallback.biteDelay).coerceAtLeast(0f),
            fishPosition = json.finiteFloat("fishPosition", fallback.fishPosition).coerceIn(0f, 1f),
            fishVelocity = json.finiteFloat("fishVelocity", fallback.fishVelocity),
            catchBarPosition = json.finiteFloat("catchBarPosition", fallback.catchBarPosition).coerceIn(0f, 1f),
            catchBarVelocity = json.finiteFloat("catchBarVelocity", fallback.catchBarVelocity),
            catchProgress = json.finiteFloat("catchProgress", fallback.catchProgress).coerceIn(0f, 1f),
        )
    }

    private fun encodeGrassTuft(value: GrassTuftState) = JSONObject()
        .put("id", value.id)
        .put("position", encodeVector(value.position))
        .putFloat("phase", value.phase)

    private fun decodeGrassTuft(json: JSONObject, fallback: GrassTuftState?): GrassTuftState {
        val base = fallback ?: GrassTuftState(0, Vector2.ZERO, 0f)
        return GrassTuftState(
            id = json.intValue("id", base.id),
            position = decodeVector(json.objectValue("position"), base.position),
            phase = json.finiteFloat("phase", base.phase),
        )
    }

    private fun encodeRockSpawning(value: RockSpawnState) = JSONObject()
        .putFloat("secondsUntilNext", value.secondsUntilNext)
        .put("totalSpawned", value.totalSpawned)

    private fun decodeRockSpawning(json: JSONObject?, fallback: RockSpawnState): RockSpawnState {
        if (json == null) return fallback
        return RockSpawnState(
            secondsUntilNext = json.finiteFloat("secondsUntilNext", fallback.secondsUntilNext).coerceAtLeast(0f),
            totalSpawned = json.intValue("totalSpawned", fallback.totalSpawned).coerceAtLeast(0),
        )
    }

    private fun encodeTreeSpawning(value: TreeSpawnState) = JSONObject()
        .putFloat("secondsUntilNext", value.secondsUntilNext)
        .put("totalSpawned", value.totalSpawned)

    private fun decodeTreeSpawning(json: JSONObject?, fallback: TreeSpawnState): TreeSpawnState {
        if (json == null) return fallback
        return TreeSpawnState(
            secondsUntilNext = json.finiteFloat("secondsUntilNext", fallback.secondsUntilNext).coerceAtLeast(0f),
            totalSpawned = json.intValue("totalSpawned", fallback.totalSpawned).coerceAtLeast(0),
        )
    }

    private fun encodeAnimation(value: AmbientAnimationState) = JSONObject()
        .putFloat("elapsedSeconds", value.elapsedSeconds)
        .putFloat("grassPhase", value.grassPhase)
        .putFloat("waterPhase", value.waterPhase)
        .putFloat("breezePhase", value.breezePhase)
        .putFloat("breezeStrength", value.breezeStrength)

    private fun decodeAnimation(json: JSONObject?, fallback: AmbientAnimationState): AmbientAnimationState {
        if (json == null) return fallback
        return AmbientAnimationState(
            elapsedSeconds = json.finiteFloat("elapsedSeconds", fallback.elapsedSeconds).coerceAtLeast(0f),
            grassPhase = json.finiteFloat("grassPhase", fallback.grassPhase),
            waterPhase = json.finiteFloat("waterPhase", fallback.waterPhase),
            breezePhase = json.finiteFloat("breezePhase", fallback.breezePhase),
            breezeStrength = json.finiteFloat("breezeStrength", fallback.breezeStrength),
        )
    }

    private fun encodeBall(value: BallState) = JSONObject()
        .put("id", value.id)
        .put("phase", value.phase.name)
        .put("position", encodeVector(value.position))
        .put("velocity", encodeVector(value.velocity))
        .putFloat("flightTimer", value.flightTimer)
        .putNullable("holderAnimalId", value.holderAnimalId)
        .putNullable("targetPetId", value.targetPetId)
        .putFloat("animationTime", value.animationTime)

    private fun decodeBall(json: JSONObject?, fallback: BallState): BallState {
        if (json == null) return fallback
        return BallState(
            id = json.intValue("id", fallback.id),
            phase = json.enumValue("phase", fallback.phase),
            position = decodeVector(json.objectValue("position"), fallback.position),
            velocity = decodeVector(json.objectValue("velocity"), fallback.velocity),
            flightTimer = json.finiteFloat("flightTimer", fallback.flightTimer).coerceAtLeast(0f),
            holderAnimalId = json.nullableInt("holderAnimalId", fallback.holderAnimalId),
            targetPetId = json.nullableInt("targetPetId", fallback.targetPetId),
            animationTime = json.finiteFloat("animationTime", fallback.animationTime).coerceAtLeast(0f),
        )
    }

    private fun encodeBicycle(value: BicycleState) = JSONObject()
        .put("id", value.id)
        .put("position", encodeVector(value.position))
        .put("isMounted", value.isMounted)
        .put("owned", value.owned)

    private fun decodeBicycle(json: JSONObject?, fallback: BicycleState): BicycleState {
        if (json == null) return fallback
        return BicycleState(
            id = json.intValue("id", fallback.id),
            position = decodeVector(json.objectValue("position"), fallback.position),
            isMounted = json.booleanValue("isMounted", fallback.isMounted),
            owned = json.booleanValue("owned", fallback.owned),
        )
    }

    private fun encodeFox(value: FoxState) = JSONObject()
        .put("id", value.id)
        .put("phase", value.phase.name)
        .put("position", encodeVector(value.position))
        .put("velocity", encodeVector(value.velocity))
        .put("facing", value.facing.name)
        .put("health", value.health)
        .putNullable("targetChickenId", value.targetChickenId)
        .putFloat("phaseTimer", value.phaseTimer)
        .putFloat("biteCooldown", value.biteCooldown)
        .putFloat("animationTime", value.animationTime)

    private fun decodeFox(json: JSONObject?, fallback: FoxState): FoxState {
        if (json == null) return fallback
        return FoxState(
            id = json.intValue("id", fallback.id),
            phase = json.enumValue("phase", fallback.phase),
            position = decodeVector(json.objectValue("position"), fallback.position),
            velocity = decodeVector(json.objectValue("velocity"), fallback.velocity),
            facing = json.enumValue("facing", fallback.facing),
            health = json.intValue("health", fallback.health).coerceAtLeast(0),
            targetChickenId = json.nullableInt("targetChickenId", fallback.targetChickenId),
            phaseTimer = json.finiteFloat("phaseTimer", fallback.phaseTimer).coerceAtLeast(0f),
            biteCooldown = json.finiteFloat("biteCooldown", fallback.biteCooldown).coerceAtLeast(0f),
            animationTime = json.finiteFloat("animationTime", fallback.animationTime).coerceAtLeast(0f),
        )
    }

    private inline fun <T> List<T>.toJsonArray(encode: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(encode(it)) } }

    private inline fun <T> JSONObject.decodeIdList(
        key: String,
        fallback: List<T>,
        idOf: (T) -> Int,
        decode: (JSONObject, T?) -> T,
    ): List<T> {
        val array = arrayValue(key) ?: return fallback
        val fallbacks = fallback.associateBy(idOf)
        val seenIds = HashSet<Int>()
        return buildList {
            for (index in 0 until array.length()) {
                val element = array.opt(index) as? JSONObject ?: continue
                val hintedFallback = fallbacks[element.intValue("id", Int.MIN_VALUE)]
                val decoded = runCatching { decode(element, hintedFallback) }.getOrNull() ?: continue
                if (seenIds.add(idOf(decoded))) add(decoded)
            }
        }
    }

    private fun JSONObject.putFloat(key: String, value: Float): JSONObject =
        put(key, if (value.isFinite()) value.toDouble() else value.toString())

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.objectValue(key: String): JSONObject? = opt(key) as? JSONObject
    private fun JSONObject.arrayValue(key: String): JSONArray? = opt(key) as? JSONArray

    private fun JSONObject.stringValue(key: String, fallback: String): String = when (val value = opt(key)) {
        null, JSONObject.NULL -> fallback
        is String -> value
        else -> value.toString()
    }

    private fun JSONObject.intValue(key: String, fallback: Int): Int = when (val value = opt(key)) {
        is Number -> value.toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        is String -> value.toLongOrNull()?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: fallback
        else -> fallback
    }

    private fun JSONObject.longValue(key: String, fallback: Long): Long = when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: fallback
        else -> fallback
    }

    private fun JSONObject.floatValue(key: String, fallback: Float): Float = when (val value = opt(key)) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: fallback
        else -> fallback
    }

    private fun JSONObject.finiteFloat(key: String, fallback: Float): Float =
        floatValue(key, fallback).takeIf(Float::isFinite) ?: fallback

    private fun JSONObject.booleanValue(key: String, fallback: Boolean): Boolean = when (val value = opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> fallback
        }
        else -> fallback
    }

    private fun JSONObject.nullableInt(key: String, fallback: Int?): Int? {
        if (!has(key)) return fallback
        return when (val value = opt(key)) {
            null, JSONObject.NULL -> null
            is Number -> value.toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            is String -> value.toLongOrNull()?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: fallback
            else -> fallback
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String, fallback: T): T {
        val name = stringValue(key, fallback.name)
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }

    private inline fun <reified T : Enum<T>> JSONObject.nullableEnumValue(key: String, fallback: T?): T? {
        if (!has(key)) return fallback
        val value = opt(key)
        if (value == null || value === JSONObject.NULL) return null
        val name = value.toString()
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }

    private inline fun <T> JSONObject.decodeNullableObject(
        key: String,
        fallback: T?,
        decode: (JSONObject, T?) -> T,
    ): T? {
        if (!has(key)) return fallback
        val value = opt(key)
        if (value == null || value === JSONObject.NULL) return null
        return (value as? JSONObject)?.let { runCatching { decode(it, fallback) }.getOrNull() } ?: fallback
    }
}
