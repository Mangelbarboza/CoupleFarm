package com.example.couplefarm.game

/** Surface under an actor's feet; the UI can map it directly to a looping footstep sound. */
enum class TerrainType { GRASS, DIRT, SAND, WATER, WOOD }

data class TerrainRegion(
    val id: Int,
    val terrain: TerrainType,
    val shape: CollisionShape,
    val visualPhase: Float = 0f,
)

data class WaterBodyState(
    val id: Int,
    val shape: CollisionShape,
    val ripplePhase: Float,
)

enum class ObstacleKind { FENCE, ROCK, BUILDING, DECORATION }

data class StaticObstacleState(
    val id: Int,
    val kind: ObstacleKind,
    val collider: CollisionShape,
    /** The Y coordinate of the object's feet, used for top-down painter ordering. */
    val sortY: Float,
    /** Only destructible obstacles consume this value. Rocks are currently the only example. */
    val hitPoints: Int = if (kind == ObstacleKind.ROCK) 2 else Int.MAX_VALUE,
)

enum class Facing { UP, DOWN, LEFT, RIGHT }

enum class ToolType {
    HANDS,
    FISHING_ROD,
    AXE,
    PICKAXE,
    MACHETE,
    PET_BALL,
    CARROT_BAIT,
    FENCE,
    GATE,
    HOE,
    WATERING_CAN,
    SEED_BAG,
    PATH_TOOL,
    MILK_PAIL,
    ARCHITECT_PENCIL,
    BLUEPRINT_DAIRY,
    BLUEPRINT_SLAUGHTERHOUSE,
    BLUEPRINT_EGG_PACKER,
    BLUEPRINT_VEGGIE_PACKER,
    BLUEPRINT_BAKERY,
    BLUEPRINT_FISH_PROCESSOR,
}

enum class ItemType(val displayName: String) {
    EGG("Huevo"),
    APPLE("Manzana"),
    WOOD("Madera"),
    STONE("Piedra"),
    MILK("Tarro de leche lleno"),
    SEED("Semillas de zanahoria"),
    CARROT("Zanahoria"),
    FISH("Pez"),
    FENCE("Valla"),
    GATE("Puerta"),
    WHEAT("Trigo"),
    WHEAT_SEED("Semillas de Trigo"),
    FLOUR("Harina"),
    DOUGH("Masa"),
    BREAD("Pan"),
    CHEESE("Queso"),
    MEAT("Carne"),
    EGG_CARTON("Cartón de Huevos"),
    VEGGIE_BOX("Caja de Verduras"),
    MILK_PAIL("Tarro vacío"),
    FISH_FILLET("Filete de Pescado"),
}

/** Short-lived pose request consumed by the renderer; [progress] always runs from 0 to 1. */
data class PlayerActionAnimationState(
    val tool: ToolType,
    val targetPosition: Vector2,
    val durationSeconds: Float,
    val remainingSeconds: Float = durationSeconds,
) {
    val progress: Float
        get() = if (durationSeconds <= 0f) 1f
        else (1f - remainingSeconds / durationSeconds).coerceIn(0f, 1f)
}

enum class EmoteType { HEART, HAPPY, WAVE, SURPRISED }

data class PlayerEmoteState(
    val type: EmoteType,
    val durationSeconds: Float = 1.6f,
    val remainingSeconds: Float = durationSeconds,
) {
    val progress: Float
        get() = if (durationSeconds <= 0f) 1f
        else (1f - remainingSeconds / durationSeconds).coerceIn(0f, 1f)
}

data class InventoryState(
    val items: Map<ItemType, Int> = emptyMap(),
    val coins: Int = 0,
) {
    fun count(item: ItemType): Int = items[item] ?: 0

    fun add(item: ItemType, amount: Int = 1): InventoryState {
        if (amount <= 0) return this
        return copy(items = items + (item to (count(item) + amount)))
    }

    fun remove(item: ItemType, amount: Int = 1): InventoryState {
        if (amount <= 0 || count(item) < amount) return this
        val newAmount = count(item) - amount
        return copy(items = if (newAmount == 0) items - item else items + (item to newAmount))
    }

    fun addCoins(amount: Int): InventoryState = copy(coins = (coins + amount).coerceAtLeast(0))
}

val defaultToolDurabilities = mapOf(
    ToolType.AXE to 25,
    ToolType.PICKAXE to 25,
    ToolType.HOE to 30,
    ToolType.WATERING_CAN to 35,
    ToolType.FISHING_ROD to 20,
    ToolType.MACHETE to 30,
    ToolType.ARCHITECT_PENCIL to 1000,
)

data class PlayerState(
    val position: Vector2,
    val facing: Facing = Facing.DOWN,
    /** Precise last non-zero joystick direction, including diagonals, for sprite selection. */
    val movementDirection: Vector2 = Vector2.DOWN,
    val isMoving: Boolean = false,
    val selectedTool: ToolType = ToolType.HANDS,
    val inventory: InventoryState = InventoryState(items = mapOf(ItemType.SEED to 12)),
    val actionCooldown: Float = 0f,
    val footstepTimer: Float = 0f,
    val blockedSoundCooldown: Float = 0f,
    val actionAnimation: PlayerActionAnimationState? = null,
    val activeEmote: PlayerEmoteState? = null,
    val carriedAnimal: AnimalReference? = null,
    val foxBites: Int = 0,
    val faintTimer: Float = 0f,
    val isMounted: Boolean = false,
    val toolDurability: Map<ToolType, Int> = defaultToolDurabilities,
    val activeItemId: ItemType? = null,
) {
    fun useTool(tool: ToolType): Pair<PlayerState, Boolean> {
        val maxUses = defaultToolDurabilities[tool] ?: return this to false
        val current = toolDurability[tool] ?: maxUses
        val next = (current - 1).coerceAtLeast(0)
        val updated = copy(toolDurability = toolDurability + (tool to next))
        return updated to (next == 0)
    }
}

data class CameraState(
    val center: Vector2,
    /** Values above 1 show a smaller world area and therefore make sprites appear larger. */
    val zoom: Float = 1.45f,
    val baseViewportWorldSize: Vector2 = Vector2(32f, 18f),
)

enum class TreeLifeState { STANDING, FALLING, STUMP }

data class TreeState(
    val id: Int,
    val position: Vector2,
    val trunkRadius: Float = 0.48f,
    val canopyRadius: Float = 2.2f,
    val lifeState: TreeLifeState = TreeLifeState.STANDING,
    val health: Int = 3,
    val shakeTimer: Float = 0f,
    val shakeCooldown: Float = 0f,
    val fallProgress: Float = 0f,
    val fallDirection: Vector2 = Vector2.RIGHT,
    val windPhase: Float = 0f,
) {
    val collider: CollisionCircle get() = CollisionCircle(position, trunkRadius)
    val blocksMovement: Boolean get() = lifeState != TreeLifeState.STUMP
}

enum class SoilState { RAW, TILLED }
enum class CropStage { SEED, SPROUT, LEAFY, MATURE }

enum class CropType(
    val seedItem: ItemType,
    val produceItem: ItemType,
    val totalGrowthSeconds: Float,
    val harvestYield: Int,
) {
    CARROT(ItemType.SEED, ItemType.CARROT, 95f, 2),
    WHEAT(ItemType.WHEAT_SEED, ItemType.WHEAT, 85f, 3),
}

data class CropState(
    val type: CropType,
    val growthSeconds: Float = 0f,
    val stage: CropStage = CropStage.SEED,
)

data class SoilPlotState(
    val id: Int,
    val position: Vector2,
    /** Exactly 2x2 cells on the invisible half-unit placement grid. */
    val size: Float = 1f,
    val soil: SoilState = SoilState.RAW,
    val moisture: Float = 0.2f,
    val crop: CropState? = null,
    val tramplingSeconds: Float = 0f,
) {
    val collider: CollisionRect
        get() = CollisionRect(
            position.x - size * 0.5f,
            position.y - size * 0.5f,
            position.x + size * 0.5f,
            position.y + size * 0.5f,
        )
}

enum class ChickenLifeStage { CHICK, ADULT }
enum class ChickenBehavior { IDLE, WANDER, PECK, FLEE, FOLLOW_BAIT }

enum class AnimalHandlingState { FREE, CARRIED, THROWN }

enum class AnimalKind { CHICKEN, COW, PIG, DOG, CAT }

data class AnimalReference(
    val kind: AnimalKind,
    val id: Int,
)

data class ChickenState(
    val id: Int,
    val position: Vector2,
    val name: String = "Gallina",
    val velocity: Vector2 = Vector2.ZERO,
    val facing: Facing = Facing.DOWN,
    val lifeStage: ChickenLifeStage = ChickenLifeStage.ADULT,
    val ageSeconds: Float = 0f,
    val behavior: ChickenBehavior = ChickenBehavior.IDLE,
    val behaviorTimer: Float = 0f,
    val desiredDirection: Vector2 = Vector2.ZERO,
    val animationTime: Float = 0f,
    val animationPhase: Float = 0f,
    val eggTimer: Float = 25f,
    val cluckTimer: Float = 5f,
    val handling: AnimalHandlingState = AnimalHandlingState.FREE,
    val thrownTimer: Float = 0f,
    val loveTimer: Float = 0f,
    val breedingCooldown: Float = 0f,
) {
    val collisionRadius: Float get() = if (lifeStage == ChickenLifeStage.CHICK) 0.2f else 0.34f
}

enum class DomesticAnimalType(val kind: AnimalKind, val isPet: Boolean) {
    COW(AnimalKind.COW, false),
    PIG(AnimalKind.PIG, false),
    DOG(AnimalKind.DOG, true),
    CAT(AnimalKind.CAT, true),
}

enum class DomesticAnimalBehavior {
    IDLE,
    WANDER,
    FOLLOW_PLAYER,
    FOLLOW_BAIT,
    FETCH_BALL,
    RETURN_BALL,
    CARRIED,
    THROWN,
    CHASE_FOX,
    FISHING,
    PLAY_WITH_PET,
    CHASE_CREATURE,
}

enum class AnimalLifeStage { BABY, ADULT }

enum class PetFollowState { FOLLOWING, STAYING, CALLED }

data class DomesticAnimalState(
    val id: Int,
    val name: String,
    val type: DomesticAnimalType,
    val position: Vector2,
    val velocity: Vector2 = Vector2.ZERO,
    val facing: Facing = Facing.DOWN,
    val behavior: DomesticAnimalBehavior = DomesticAnimalBehavior.IDLE,
    val behaviorTimer: Float = 0f,
    val desiredDirection: Vector2 = Vector2.ZERO,
    val animationTime: Float = 0f,
    val animationPhase: Float = 0f,
    val handling: AnimalHandlingState = AnimalHandlingState.FREE,
    val thrownTimer: Float = 0f,
    /** Null for livestock; always non-null for dogs and cats created by the engine. */
    val petFollowState: PetFollowState? = null,
    val petCallTimer: Float = 0f,
    /** Ready immediately for adult cows, refreshed on milk. */
    val milkReadyInSeconds: Float = 0f,
    val breedIndex: Int = 0,
    val lifeStage: AnimalLifeStage = AnimalLifeStage.BABY,
    val ageSeconds: Float = 0f,
    val specialCooldown: Float = 35f,
    /** Persisted target/cooldowns keep ambient-creature chases from flickering at range limits. */
    val chaseTargetCreatureId: Int? = null,
    val critterChaseCooldown: Float = 0f,
    /** A play partner is retained for one short encounter; failed 50/50 rolls also cool down. */
    val playMateId: Int? = null,
    val socialCooldown: Float = 0f,
    val loveTimer: Float = 0f,
    val breedingCooldown: Float = 0f,
) {
    val reference: AnimalReference get() = AnimalReference(type.kind, id)
    val collisionRadius: Float
        get() = (when (type) {
            DomesticAnimalType.COW -> 0.72f
            DomesticAnimalType.PIG -> 0.52f
            DomesticAnimalType.DOG -> 0.4f
            DomesticAnimalType.CAT -> 0.33f
        }) * if (lifeStage == AnimalLifeStage.BABY) 0.68f else 1f
}

enum class BallPhase { WITH_PLAYER, FLYING, ON_GROUND, WITH_PET }

data class BallState(
    val id: Int = 850,
    val phase: BallPhase = BallPhase.WITH_PLAYER,
    val position: Vector2,
    val velocity: Vector2 = Vector2.ZERO,
    val flightTimer: Float = 0f,
    val holderAnimalId: Int? = null,
    val targetPetId: Int? = null,
    val animationTime: Float = 0f,
)

data class BicycleState(
    val id: Int = 860,
    val position: Vector2,
    val isMounted: Boolean = false,
    val owned: Boolean = false,
)

data class EggState(
    val id: Int,
    val position: Vector2,
    val ageSeconds: Float = 0f,
    val fertile: Boolean = false,
    val hatchAtSeconds: Float = Float.POSITIVE_INFINITY,
    val animationPhase: Float = 0f,
)

data class GroundItemState(
    val id: Int,
    val item: ItemType,
    val position: Vector2,
    val amount: Int = 1,
    val animationPhase: Float = 0f,
)

enum class PathStyle { GRAVEL }

/**
 * One removable 1x1 road tile. [cellX]/[cellY] are stable integer coordinates over the same
 * invisible half-unit placement grid used by crops; one road tile occupies 2x2 mini-cells.
 */
data class PathTileState(
    val id: Int,
    val cellX: Int,
    val cellY: Int,
    val style: PathStyle = PathStyle.GRAVEL,
) {
    val position: Vector2 get() = Vector2(cellX + 0.5f, cellY + 0.5f)
    val collider: CollisionRect
        get() = CollisionRect(cellX.toFloat(), cellY.toFloat(), cellX + 1f, cellY + 1f)
}

data class BarnState(
    val id: Int = 1,
    val collider: CollisionRect,
    val doorPosition: Vector2,
    val storedEggs: Int = 0,
    val eggCapacity: Int = 999,
    val storage: Map<ItemType, Int> = emptyMap(),
)

enum class FactoryType(
    val displayName: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val woodRequired: Int,
    val stoneRequired: Int,
    val blueprintPrice: Int,
) {
    DAIRY("Fábrica de Lácteos", 4, 3, 18, 12, 600),
    SLAUGHTERHOUSE("Matadero", 4, 3, 20, 16, 750),
    EGG_PACKER("Empaquetadora de Huevos", 3, 3, 14, 10, 400),
    VEGGIE_PACKER("Empaquetadora de Verduras", 3, 3, 14, 10, 400),
    BAKERY("Panadería", 4, 3, 22, 14, 350),
    FISH_PROCESSOR("Fileteadora de Pescado", 4, 3, 16, 12, 500),
}

enum class BuildingOrientation(val angleDegrees: Float) {
    N(0f),
    NE(45f),
    E(90f),
    SE(135f),
    S(180f),
    SW(225f),
    W(270f),
    NW(315f);

    fun next(): BuildingOrientation {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

enum class ConstructionPhase { SCAFFOLD, OPERATIONAL }

data class FactoryBuildingState(
    val id: Int,
    val type: FactoryType,
    val cellX: Int,
    val cellY: Int,
    val widthCells: Int = type.defaultWidth,
    val heightCells: Int = type.defaultHeight,
    val orientation: BuildingOrientation = BuildingOrientation.S,
    val phase: ConstructionPhase = ConstructionPhase.SCAFFOLD,
    val woodContributed: Int = 0,
    val stoneContributed: Int = 0,
    val productionTimer: Float = 0f,
    val hasPigInChamber: Boolean = false,
    val hasCowInChamber: Boolean = false,
    val storedInputs: Int = 0,
    val storedOutputs: Int = 0,
    val oven1Timer: Float = 0f,
    val oven1Active: Boolean = false,
    val oven1Ready: Boolean = false,
    val oven2Timer: Float = 0f,
    val oven2Active: Boolean = false,
    val oven2Ready: Boolean = false,
) {
    val woodRequired: Int get() = type.woodRequired
    val stoneRequired: Int get() = type.stoneRequired
    val isComplete: Boolean get() = phase == ConstructionPhase.OPERATIONAL || (woodContributed >= woodRequired && stoneContributed >= stoneRequired)

    val collider: CollisionRect
        get() = CollisionRect(
            cellX.toFloat(),
            cellY.toFloat(),
            (cellX + widthCells).toFloat(),
            (cellY + heightCells).toFloat(),
        )

    val position: Vector2
        get() = Vector2(cellX + widthCells * 0.5f, cellY + heightCells * 0.5f)

    val doorPosition: Vector2
        get() = Vector2(cellX + widthCells * 0.5f, (cellY + heightCells).toFloat() + 0.35f)
}

enum class WorkerBehavior { OFF_DUTY, IDLE, TO_EGG, TO_BARN }

/** Serializable hired farmhand. The worker carries only one egg at a time. */
data class WorkerState(
    val id: Int,
    val name: String,
    val position: Vector2,
    val velocity: Vector2 = Vector2.ZERO,
    val facing: Facing = Facing.DOWN,
    val behavior: WorkerBehavior = WorkerBehavior.OFF_DUTY,
    val targetEggId: Int? = null,
    val carriedEggs: Int = 0,
    val shiftStartHour: Int = 8,
    val shiftEndHour: Int = 17,
    val animationTime: Float = 0f,
)

enum class TruckPhase { ABSENT, ARRIVING, PARKED, LEAVING }

data class TruckState(
    val id: Int = 1,
    val phase: TruckPhase = TruckPhase.ABSENT,
    val position: Vector2,
    val phaseTimer: Float,
    val eggsLoaded: Int = 0,
    val saleCompletedThisVisit: Boolean = false,
    val lastSaleCoins: Int = 0,
)

enum class FishingPhase { NONE, CASTING, WAITING_FOR_BITE, BITE, REELING, SUCCESS, FAILED }

data class FishingState(
    val phase: FishingPhase = FishingPhase.NONE,
    val waterBodyId: Int? = null,
    val bobberPosition: Vector2 = Vector2.ZERO,
    val phaseTimer: Float = 0f,
    val biteDelay: Float = 0f,
    /** 0 is the bottom and 1 is the top of the fishing meter. */
    val fishPosition: Float = 0.5f,
    val fishVelocity: Float = 0f,
    val catchBarPosition: Float = 0.25f,
    val catchBarVelocity: Float = 0f,
    val catchProgress: Float = 0f,
)

data class AmbientAnimationState(
    val elapsedSeconds: Float = 0f,
    val grassPhase: Float = 0f,
    val waterPhase: Float = 0f,
    val breezePhase: Float = 0f,
    val breezeStrength: Float = 0f,
)

data class GrassTuftState(
    val id: Int,
    val position: Vector2,
    val phase: Float,
)

/** Lightweight wildlife that decorates the farm without participating in collisions. */
enum class AmbientCreatureType { BIRD, BUTTERFLY }

data class AmbientCreatureState(
    val id: Int,
    val type: AmbientCreatureType,
    val position: Vector2,
    val velocity: Vector2 = Vector2.ZERO,
    val facing: Facing = Facing.RIGHT,
    val animationTime: Float = 0f,
    val lifeTimer: Float = 18f,
    val movementTimer: Float = 1f,
    val soundCooldown: Float = 4f,
)

/** Serializable rock-regrowth clock. The random generator itself deliberately stays in the engine. */
data class RockSpawnState(
    val secondsUntilNext: Float = 75f,
    val totalSpawned: Int = 0,
)

data class TreeSpawnState(
    val secondsUntilNext: Float = 120f,
    val totalSpawned: Int = 0,
)

enum class FoxPhase { ABSENT, HUNTING, FLEEING, DEAD }

data class FoxState(
    val id: Int = 870,
    val phase: FoxPhase = FoxPhase.ABSENT,
    val position: Vector2 = Vector2(-3f, -3f),
    val velocity: Vector2 = Vector2.ZERO,
    val facing: Facing = Facing.RIGHT,
    val health: Int = 3,
    val targetChickenId: Int? = null,
    /** Spawn, flee, or resurrection clock depending on [phase]. */
    val phaseTimer: Float = 75f,
    val biteCooldown: Float = 0f,
    val animationTime: Float = 0f,
    val huntingTimer: Float = 0f,
)

data class FenceGateState(
    val id: Int,
    val cellX: Int,
    val cellY: Int,
    val isOpen: Boolean = false,
) {
    val position: Vector2 get() = Vector2(cellX + 0.5f, cellY + 0.5f)
    val collider: CollisionRect get() = CollisionRect(cellX.toFloat(), cellY.toFloat(), cellX + 1f, cellY + 1f)
}

data class WheelbarrowState(
    val id: Int = 880,
    val position: Vector2,
    val isBeingPushed: Boolean = false,
    val cargo: Map<ItemType, Int> = emptyMap(),
    val capacity: Int = 8,
) {
    val totalCargo: Int get() = cargo.values.sum()
    val isFull: Boolean get() = totalCargo >= capacity
}

data class ThrownProjectileState(
    val id: Int,
    val item: ItemType,
    val position: Vector2,
    val velocity: Vector2,
    val height: Float = 0.5f,
    val verticalVelocity: Float = 3.2f,
    val lifeSeconds: Float = 0f,
    val bounces: Int = 0,
)

data class WaterRippleState(
    val id: Int,
    val position: Vector2,
    val radius: Float = 0.15f,
    val maxRadius: Float = 0.95f,
    val progress: Float = 0f,
)

data class AnimalPurchaseRequest(
    val type: DomesticAnimalType,
    val name: String = "",
    val breedIndex: Int = 0,
)

enum class DeliveryKind { ANIMAL, SEEDS, FENCES, BICYCLE, CHICKEN, TOOL, BLUEPRINT }

enum class CourierPhase { WAITING, TO_BARN, RETURNING }

data class DeliveryOrderState(
    val id: Int,
    val kind: DeliveryKind,
    val animalType: DomesticAnimalType? = null,
    val toolType: ToolType? = null,
    val factoryType: FactoryType? = null,
    val itemType: ItemType? = null,
    val breedIndex: Int = 0,
    val amount: Int = 1,
    val secondsRemaining: Float = 7f,
    /** The courier has reached the barn and handed over this order. */
    val delivered: Boolean = false,
    /** Explicit movement state replaces the old 1.5 second timer interpolation. */
    val courierPhase: CourierPhase = if (delivered) CourierPhase.RETURNING else CourierPhase.WAITING,
    val courierPosition: Vector2 = Vector2.ZERO,
    val courierFacing: Facing = Facing.LEFT,
    /** Index of the next waypoint in the entrance-to-barn route. */
    val courierRouteIndex: Int = -1,
    val courierAnimationTime: Float = 0f,
)

data class AnimalRenameRequest(
    val animal: AnimalReference,
    val name: String,
)

enum class PetCommandType { FOLLOW, STAY, CALL, RELEASE }

data class PetCommandRequest(
    /** Null applies the command to every owned dog and cat. */
    val animalId: Int? = null,
    val command: PetCommandType,
)

/** Complete serializable-looking snapshot. The engine replaces this value once per update. */
data class FarmWorldState(
    val bounds: CollisionRect,
    val player: PlayerState,
    val camera: CameraState,
    val terrainRegions: List<TerrainRegion>,
    val waterBodies: List<WaterBodyState>,
    val staticObstacles: List<StaticObstacleState>,
    val grassTufts: List<GrassTuftState>,
    val pathTiles: List<PathTileState> = emptyList(),
    val trees: List<TreeState>,
    val plots: List<SoilPlotState>,
    val chickens: List<ChickenState>,
    val animals: List<DomesticAnimalState> = emptyList(),
    val eggs: List<EggState>,
    val groundItems: List<GroundItemState>,
    val barn: BarnState,
    val helper: WorkerState? = null,
    val truck: TruckState,
    val fishing: FishingState = FishingState(),
    val ball: BallState = BallState(position = player.position),
    val bicycle: BicycleState = BicycleState(position = player.position + Vector2(1.5f, 0f)),
    val rockSpawning: RockSpawnState = RockSpawnState(),
    val treeSpawning: TreeSpawnState = TreeSpawnState(),
    val fox: FoxState = FoxState(),
    val deliveries: List<DeliveryOrderState> = emptyList(),
    val ambientCreatures: List<AmbientCreatureState> = emptyList(),
    val ambientCreatureSpawnTimer: Float = 6f,
    val animation: AmbientAnimationState = AmbientAnimationState(),
    val day: Int = 1,
    val secondsOfDay: Float = 8f * 60f * 60f,
    val nextEntityId: Int = 1_000,
    val factories: List<FactoryBuildingState> = emptyList(),
    val isPencilMode: Boolean = false,
    val selectedBlueprintOrientation: BuildingOrientation = BuildingOrientation.S,
    val selectedMoveStructureId: Int? = null,
    val gates: List<FenceGateState> = emptyList(),
    val wheelbarrow: WheelbarrowState = WheelbarrowState(position = Vector2(32f, 22f)),
    val projectiles: List<ThrownProjectileState> = emptyList(),
    val ripples: List<WaterRippleState> = emptyList(),
    val guestPlayer: PlayerState? = null,
    val guestFarmerName: String? = null,
)

data class WorldInput(
    /** Joystick input in the -1..1 range. Diagonals are normalized by the engine. */
    val movement: Vector2 = Vector2.ZERO,
    /** A single rising-edge press: shake, chop, plant, cast or hook. */
    val actionPressed: Boolean = false,
    /** Hold during the fishing minigame to raise its catch bar. */
    val actionHeld: Boolean = false,
    val selectedTool: ToolType? = null,
    /** Rising-edge request for one of the four social emotes. */
    val emote: EmoteType? = null,
    /** Contextual command: drops a carried animal with hands or throws it in this direction. */
    val throwAnimalPressed: Boolean = false,
    val throwDirection: Vector2 = Vector2.ZERO,
    val animalPurchase: AnimalPurchaseRequest? = null,
    val animalRename: AnimalRenameRequest? = null,
    val sellPigId: Int? = null,
    val petCommand: PetCommandRequest? = null,
    val toggleBicyclePressed: Boolean = false,
    val bicycleBellPressed: Boolean = false,
    val grabAnimalPressed: Boolean = false,
    val purchaseFences: Int = 0,
    val purchaseSeeds: Int = 0,
    val purchaseWheatSeeds: Int = 0,
    val purchaseGates: Int = 0,
    val purchaseBicycle: Boolean = false,
    val purchaseChicken: Boolean = false,
    val purchaseTool: ToolType? = null,
    val petAnimalPressed: Boolean = false,
    val removePlotPressed: Boolean = false,
    /** Name supplied by the shop. Hiring is immediate and charges the configured one-time fee. */
    val hireHelperName: String? = null,
    val renameHelper: String? = null,
    val dismissHelper: Boolean = false,
    val debugCoinsPressed: Boolean = false,
    val rotateBlueprintPressed: Boolean = false,
    val togglePencilModePressed: Boolean = false,
    val pencilMoveTargetCell: Pair<Int, Int>? = null,
    val nudgeStructure: Pair<Int, Int>? = null,
    val confirmStructurePosition: Boolean = false,
    val rotateStructurePressed: Boolean = false,
    val purchaseBlueprint: FactoryType? = null,
    val weedHarvestPressed: Boolean = false,
    val enterFactoryPressed: Boolean = false,
    val feedAnimalPressed: Boolean = false,
    val completeMinigame: FactoryType? = null,
    val depositAllToBarn: Boolean = false,
    val depositItemToBarn: ItemType? = null,
    val withdrawBarnItem: ItemType? = null,
    val withdrawBarnItemAmount: Int = 10,
    val withdrawBarnEggs: Int? = null,
    val selectActiveItem: ItemType? = null,
    val toggleGateId: Int? = null,
    val toggleWheelbarrow: Boolean = false,
    val loadWheelbarrowItem: ItemType? = null,
    val unloadWheelbarrowItem: ItemType? = null,
    val unloadWheelbarrowToBarn: Boolean = false,
    val sellWheelbarrowCargo: Boolean = false,
    val throwItemPressed: Boolean = false,
    val grindWheat: Boolean = false,
    val kneadDough: Boolean = false,
    val startBakeryOven: Pair<Int, Int>? = null,
    val collectBakeryOven: Pair<Int, Int>? = null,
)

data class FarmWorldFrame(
    val state: FarmWorldState,
    /** Consume once. Sound events are intentionally not persisted in [FarmWorldState]. */
    val events: List<WorldEvent>,
)

enum class SoundCue {
    FOOTSTEP_GRASS,
    FOOTSTEP_DIRT,
    FOOTSTEP_SAND,
    COLLISION,
    WATER_BLOCKED,
    ITEM_PICKUP,
    TREE_RUSTLE,
    AXE_SWING,
    AXE_HIT,
    TREE_FALL,
    PICKAXE_SWING,
    ROCK_HIT,
    ROCK_BREAK,
    MACHETE_SWING,
    MACHETE_HIT,
    ANIMAL_PICK_UP,
    ANIMAL_DROP,
    ANIMAL_THROW,
    COW_MILK,
    PET_CALL,
    BALL_THROW,
    BALL_FETCH,
    BICYCLE_BELL,
    BIRD_CHIRP,
    BUTTERFLY_FLUTTER,
    FOX_ALERT,
    FOX_BITE,
    FOX_DEFEATED,
    SOIL_TILL,
    SEED_PLANT,
    WATER_CROP,
    CROP_HARVEST,
    CHICKEN_CLUCK,
    CHICKEN_FLAP,
    EGG_LAID,
    EGG_HATCH,
    BARN_DEPOSIT,
    TRUCK_ARRIVE,
    TRUCK_ENGINE,
    COINS,
    FISHING_CAST,
    BOBBER_SPLASH,
    FISH_BITE,
    FISHING_REEL,
    FISH_CAUGHT,
    FISH_ESCAPED,
    TOOL_SELECT,
    CONSTRUCTION_HAMMER,
    SLAUGHTER_HIT,
    FACTORY_COMPLETE,
}

sealed interface WorldEvent {
    data class Sound(
        val cue: SoundCue,
        val position: Vector2,
        val volume: Float = 1f,
    ) : WorldEvent

    data class ItemCollected(val item: ItemType, val amount: Int) : WorldEvent
    data class ItemSpent(val item: ItemType, val amount: Int) : WorldEvent
    data class EggsStored(val amount: Int, val totalStored: Int) : WorldEvent
    data class TruckSale(val eggsSold: Int, val coinsEarned: Int) : WorldEvent
    data class TreeHit(val treeId: Int, val health: Int) : WorldEvent
    data class TreeCut(val treeId: Int) : WorldEvent
    data class TreeShaken(val treeId: Int, val droppedApple: Boolean) : WorldEvent
    data class TreeFelled(val treeId: Int) : WorldEvent
    data class TreeStumpRemoved(val treeId: Int) : WorldEvent
    data class RockHit(val obstacleId: Int, val health: Int) : WorldEvent
    data class RockBroken(val obstacleId: Int, val stoneYield: Int = 1) : WorldEvent
    data class RockSpawned(val obstacleId: Int, val position: Vector2) : WorldEvent
    data class TreeSpawned(val treeId: Int, val position: Vector2) : WorldEvent
    data class AnimalPurchased(val animal: DomesticAnimalState, val price: Int) : WorldEvent
    data class AnimalPurchaseRejected(val type: DomesticAnimalType, val reason: String) : WorldEvent
    data class PigSold(val animalId: Int, val name: String, val coins: Int) : WorldEvent
    data class PetCommanded(val animalId: Int, val command: PetCommandType) : WorldEvent
    data class AnimalGrabbed(val animal: AnimalReference) : WorldEvent
    data class AnimalDropped(val animal: AnimalReference, val position: Vector2) : WorldEvent
    data class AnimalThrown(val animal: AnimalReference, val velocity: Vector2) : WorldEvent
    data class CowMilked(val animalId: Int, val name: String, val amount: Int) : WorldEvent
    data class BallThrown(val position: Vector2, val velocity: Vector2) : WorldEvent
    data object BallPickedUp : WorldEvent
    data class PetFetchedBall(val animalId: Int) : WorldEvent
    data class BallReturned(val animalId: Int) : WorldEvent
    data class BicycleMounted(val bicycleId: Int) : WorldEvent
    data class BicycleDismounted(val bicycleId: Int, val position: Vector2) : WorldEvent
    data class FoxAppeared(val foxId: Int, val position: Vector2) : WorldEvent
    data class FoxHit(val foxId: Int, val remainingHealth: Int) : WorldEvent
    data class FoxDefeated(val foxId: Int) : WorldEvent
    data class PlayerBitten(val bites: Int) : WorldEvent
    data object PlayerFainted : WorldEvent
    data object PlayerRecovered : WorldEvent
    data class ChickenTakenByFox(val chickenId: Int, val chickenName: String) : WorldEvent
    data class FoxScaredByDog(val dogId: Int) : WorldEvent
    data class CatCaughtFish(val catId: Int, val catName: String) : WorldEvent
    data class OrderPlaced(val orderId: Int, val kind: DeliveryKind) : WorldEvent
    data class OrderDelivered(val orderId: Int, val kind: DeliveryKind) : WorldEvent
    data class OrderIntercepted(val orderId: Int, val kind: DeliveryKind) : WorldEvent
    data class FenceRemoved(val fenceId: Int) : WorldEvent
    data class PathPlaced(val pathId: Int, val cellX: Int, val cellY: Int) : WorldEvent
    data class PathRemoved(val pathId: Int, val cellX: Int, val cellY: Int) : WorldEvent
    data class HelperHired(val helper: WorkerState, val price: Int) : WorldEvent
    data class HelperHireRejected(val reason: String) : WorldEvent
    data class HelperRenamed(val helperId: Int, val name: String) : WorldEvent
    data class HelperDismissed(val helperId: Int, val name: String) : WorldEvent
    data class HelperPickedUpEgg(val helperId: Int, val eggId: Int) : WorldEvent
    data class HelperStoredEggs(val helperId: Int, val amount: Int, val totalStored: Int) : WorldEvent
    data class CropHarvested(val plotId: Int, val crop: CropType) : WorldEvent
    data class PlotChanged(val plotId: Int, val cropStage: CropStage?) : WorldEvent
    data class EggHatched(val eggId: Int, val chickenId: Int) : WorldEvent
    data class FishCaught(val item: ItemType) : WorldEvent
    data class FishingChanged(val phase: FishingPhase) : WorldEvent
    data class ToolBroken(val tool: ToolType) : WorldEvent
    data class PetPetted(val petId: Int, val petName: String) : WorldEvent
    data class FactoryPlaced(val factoryId: Int, val type: FactoryType) : WorldEvent
    data class FactoryMaterialAdded(val factoryId: Int, val item: ItemType, val remaining: Int) : WorldEvent
    data class FactoryCompleted(val factoryId: Int, val type: FactoryType) : WorldEvent
    data class FactoryMoved(val factoryId: Int, val newCellX: Int, val newCellY: Int) : WorldEvent
    data class BarnMoved(val newCollider: CollisionRect) : WorldEvent
    data class SlaughterFlash(val position: Vector2) : WorldEvent
    data class AnimalBorn(val kind: AnimalKind, val parentId: Int) : WorldEvent
    data class WeedHarvested(val tuftId: Int, val seed: ItemType) : WorldEvent
    data class CowGrazed(val cowId: Int) : WorldEvent
    data class FactoryMinigameTriggered(val factoryId: Int, val type: FactoryType) : WorldEvent
    data class GateToggled(val gateId: Int, val isOpen: Boolean) : WorldEvent
    data class ItemThrown(val item: ItemType, val position: Vector2, val velocity: Vector2) : WorldEvent
    data class StoneSkipped(val position: Vector2, val bounceCount: Int) : WorldEvent
    data class WheelbarrowCargoSold(val coinsEarned: Int) : WorldEvent
    data class BarnStorageChanged(val itemsCount: Int) : WorldEvent
}

enum class RenderEntityType {
    BARN, TREE, PLAYER, GUEST_PLAYER, CHICKEN, CHICK, COW, PIG, DOG, CAT, EGG, GROUND_ITEM, CROP,
    BALL, BICYCLE, FOX, TRUCK, COURIER, BIRD, BUTTERFLY, HELPER, FACTORY,
    GATE, WHEELBARROW, PROJECTILE, RIPPLE,
}

data class RenderEntry(
    val type: RenderEntityType,
    val id: Int,
    val sortY: Float,
)

/**
 * Top-down painter queue. Drawing in this order makes trees/buildings cover the player when the
 * player walks behind them and lets the player cover those objects when walking in front.
 */
fun FarmWorldState.renderQueue(): List<RenderEntry> = buildList {
    add(RenderEntry(RenderEntityType.BARN, barn.id, barn.collider.bottom))
    factories.forEach {
        add(RenderEntry(RenderEntityType.FACTORY, it.id, it.collider.bottom))
    }
    gates.forEach {
        add(RenderEntry(RenderEntityType.GATE, it.id, it.position.y + 0.5f))
    }
    trees.forEach {
        add(RenderEntry(RenderEntityType.TREE, it.id, it.position.y))
    }
    plots.filter { it.crop != null }.forEach {
        add(RenderEntry(RenderEntityType.CROP, it.id, it.position.y + it.size * 0.5f))
    }
    chickens.forEach {
        add(
            RenderEntry(
                if (it.lifeStage == ChickenLifeStage.CHICK) RenderEntityType.CHICK else RenderEntityType.CHICKEN,
                it.id,
                it.position.y + it.collisionRadius,
            ),
        )
    }
    animals.forEach { animal ->
        val type = when (animal.type) {
            DomesticAnimalType.COW -> RenderEntityType.COW
            DomesticAnimalType.PIG -> RenderEntityType.PIG
            DomesticAnimalType.DOG -> RenderEntityType.DOG
            DomesticAnimalType.CAT -> RenderEntityType.CAT
        }
        add(RenderEntry(type, animal.id, animal.position.y + animal.collisionRadius))
    }
    eggs.forEach { add(RenderEntry(RenderEntityType.EGG, it.id, it.position.y + 0.2f)) }
    groundItems.forEach { add(RenderEntry(RenderEntityType.GROUND_ITEM, it.id, it.position.y + 0.2f)) }
    if (ball.phase != BallPhase.WITH_PLAYER) {
        add(RenderEntry(RenderEntityType.BALL, ball.id, ball.position.y + 0.12f))
    }
    if (bicycle.owned && !bicycle.isMounted) {
        add(RenderEntry(RenderEntityType.BICYCLE, bicycle.id, bicycle.position.y + 0.45f))
    }
    add(RenderEntry(RenderEntityType.WHEELBARROW, wheelbarrow.id, wheelbarrow.position.y + 0.35f))
    projectiles.forEach {
        add(RenderEntry(RenderEntityType.PROJECTILE, it.id, it.position.y + 0.1f))
    }
    ripples.forEach {
        add(RenderEntry(RenderEntityType.RIPPLE, it.id, it.position.y + 0.05f))
    }
    if (fox.phase == FoxPhase.HUNTING || fox.phase == FoxPhase.FLEEING) {
        add(RenderEntry(RenderEntityType.FOX, fox.id, fox.position.y + 0.42f))
    }
    if (truck.phase != TruckPhase.ABSENT) add(RenderEntry(RenderEntityType.TRUCK, truck.id, truck.position.y + 1.1f))
    deliveries.firstOrNull { it.courierPhase != CourierPhase.WAITING }?.let { order ->
        add(RenderEntry(RenderEntityType.COURIER, order.id, order.courierPosition.y + 0.45f))
    }
    ambientCreatures.forEach { creature ->
        val type = if (creature.type == AmbientCreatureType.BIRD) {
            RenderEntityType.BIRD
        } else {
            RenderEntityType.BUTTERFLY
        }
        add(RenderEntry(type, creature.id, creature.position.y + 0.12f))
    }
    helper?.let { worker ->
        add(RenderEntry(RenderEntityType.HELPER, worker.id, worker.position.y + 0.45f))
    }
    add(RenderEntry(RenderEntityType.PLAYER, 0, player.position.y + 0.45f))
    guestPlayer?.let { guest ->
        add(RenderEntry(RenderEntityType.GUEST_PLAYER, 9999, guest.position.y + 0.45f))
    }
}.sortedWith(compareBy<RenderEntry> { it.sortY }.thenBy { it.type.ordinal }.thenBy { it.id })
