package com.example.couplefarm.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

data class FarmWorldConfig(
    val playerRadius: Float = 0.42f,
    val playerSpeed: Float = 5.2f,
    val interactionRange: Float = 1.65f,
    val pickupRange: Float = 0.72f,
    val treeAppleChance: Float = 0.25f,
    val treeShakeSeconds: Float = 0.55f,
    val treeShakeCooldownSeconds: Float = 8f,
    val treeFallSeconds: Float = 1.15f,
    val treeAxeHits: Int = 3,
    val stumpAxeHits: Int = 2,
    val stumpWoodYield: Int = 2,
    val plotPlacementDistance: Float = 1.18f,
    /** Half-unit cells stay invisible; a 2x2 plot occupies exactly one world unit. */
    val placementGridCellSize: Float = 0.5f,
    val plotGridCells: Int = 2,
    val newPlotSize: Float = 1f,
    val rockPickaxeHits: Int = 2,
    val rockStoneYield: Int = 3,
    val rockMinSpawnSeconds: Float = 70f,
    val rockMaxSpawnSeconds: Float = 125f,
    val rockSpawnRetrySeconds: Float = 12f,
    val rockSpawnAttempts: Int = 32,
    val maxWorldRocks: Int = 9,
    val minRockSpawnRadius: Float = 0.42f,
    val maxRockSpawnRadius: Float = 0.72f,
    val emoteDurationSeconds: Float = 1.6f,
    val animalWalkSpeed: Float = 1.05f,
    val animalFollowSpeed: Float = 2.1f,
    val petCallSpeed: Float = 3.15f,
    val petFollowDistance: Float = 1.45f,
    val petCritterAcquireRadius: Float = 5.1f,
    /** Pets abandon ambient prey before reaching this radius from their player. */
    val petCritterPlayerLimit: Float = 6.25f,
    val petCritterReturnMargin: Float = 0.7f,
    val petCritterChaseCooldownSeconds: Float = 2.6f,
    val petPlayChancePerEncounter: Float = 0.5f,
    val petPlayDurationSeconds: Float = 2.1f,
    val petSocialCooldownSeconds: Float = 4.5f,
    val baitFollowRadius: Float = 7.5f,
    val animalThrowSpeed: Float = 7.2f,
    val animalThrowSeconds: Float = 0.72f,
    val cowMilkIntervalSeconds: Float = 150f,
    val cowMilkYield: Int = 1,
    val cowPurchasePrice: Int = 500,
    val pigPurchasePrice: Int = 260,
    val dogPurchasePrice: Int = 420,
    val catPurchasePrice: Int = 380,
    val pigSalePrice: Int = 340,
    val animalBabyGrowthSeconds: Float = 120f,
    val catFishCooldownSeconds: Float = 55f,
    val dogFoxScareRadius: Float = 1.45f,
    val fenceBundlePrice: Int = 120,
    val bicyclePurchasePrice: Int = 700,
    val ballThrowSpeed: Float = 9f,
    val ballFlightSeconds: Float = 0.8f,
    val petBallInterestRadius: Float = 14f,
    val bicycleMountRange: Float = 2.6f,
    val bicycleSpeedMultiplier: Float = 1.85f,
    val courierWalkSpeed: Float = 1.15f,
    val courierRadius: Float = 0.28f,
    val maxAmbientCreatures: Int = 7,
    val ambientSpawnMinSeconds: Float = 5f,
    val ambientSpawnMaxSeconds: Float = 11f,
    val ambientSoundRadius: Float = 6.5f,
    val treeMinSpawnSeconds: Float = 95f,
    val treeMaxSpawnSeconds: Float = 170f,
    val treeSpawnRetrySeconds: Float = 15f,
    val treeSpawnAttempts: Int = 36,
    val maxWorldTrees: Int = 24,
    val foxFirstAppearanceSeconds: Float = 70f,
    val foxMinReturnSeconds: Float = 80f,
    val foxMaxReturnSeconds: Float = 150f,
    val foxRespawnAfterDefeatSeconds: Float = 120f,
    val foxFleeSeconds: Float = 2.4f,
    val foxSpeed: Float = 3f,
    val foxFleeSpeed: Float = 4.6f,
    val foxMaxHealth: Int = 3,
    val foxBiteRange: Float = 0.82f,
    val foxBiteCooldownSeconds: Float = 1.15f,
    val playerBitesBeforeFaint: Int = 3,
    val playerFaintSeconds: Float = 3.5f,
    val chickenWalkSpeed: Float = 1.25f,
    val chickenFleeSpeed: Float = 3.15f,
    val chickenFleeDistance: Float = 4.4f,
    val chickenMinEggSeconds: Float = 22f,
    val chickenMaxEggSeconds: Float = 48f,
    val eggHatchChance: Float = 0.22f,
    val eggHatchSeconds: Float = 75f,
    val chickGrowthSeconds: Float = 100f,
    val maxChickenPopulation: Int = 18,
    val maxGroundEggs: Int = 20,
    val pathTileSize: Float = 1f,
    val helperHirePrice: Int = 600,
    val helperWalkSpeed: Float = 1.35f,
    val helperRadius: Float = 0.38f,
    val helperPickupRange: Float = 0.55f,
    val helperBarnRange: Float = 0.7f,
    val helperShiftStartHour: Int = 8,
    val helperShiftEndHour: Int = 17,
    val truckFirstArrivalSeconds: Float = 18f,
    val truckMinReturnSeconds: Float = 55f,
    val truckMaxReturnSeconds: Float = 95f,
    val truckArrivalSeconds: Float = 4f,
    val truckParkedSeconds: Float = 15f,
    val truckLeaveSeconds: Float = 4f,
    val eggSalePrice: Int = 12,
    val fishingShoreRange: Float = 3.1f,
    val maxDeltaSeconds: Float = 0.25f,
)

/**
 * Deterministic, Android-free simulation for the farm. Keep one instance in a ViewModel and pass
 * joystick/action input to [update]. Compose only needs to observe the returned immutable state.
 */
class FarmWorldEngine(
    initialState: FarmWorldState = createDefaultFarmWorld(),
    seed: Int = 0xC0FFEE,
    val config: FarmWorldConfig = FarmWorldConfig(),
) {
    private val random = Random(seed)

    var state: FarmWorldState = initialState
        private set

    fun update(deltaSeconds: Float, input: WorldInput = WorldInput()): FarmWorldFrame {
        val delta = deltaSeconds.coerceIn(0f, config.maxDeltaSeconds)
        val events = mutableListOf<WorldEvent>()
        var world = sanitizeUnreachableGrowth(
            migrateLegacyMilkPail(enforceGroundEggCap(normalizeMountedState(state))),
        )

        input.selectedTool?.let { tool ->
            if (tool != world.player.selectedTool) {
                world = world.copy(
                    player = world.player.copy(
                        selectedTool = tool,
                        activeItemId = world.player.activeItemId?.takeIf { item -> item.matchesTool(tool) },
                        actionAnimation = null,
                    ),
                    fishing = if (world.fishing.phase == FishingPhase.NONE) world.fishing else FishingState(),
                )
                events.sound(SoundCue.TOOL_SELECT, world.player.position)
            }
        }

        input.emote?.let { emote ->
            world = world.copy(
                player = world.player.copy(
                    activeEmote = PlayerEmoteState(
                        type = emote,
                        durationSeconds = config.emoteDurationSeconds,
                    ),
                ),
            )
        }

        world = handleAnimalPurchase(world, input.animalPurchase, events)
        if (input.debugCoinsPressed) world = world.copy(player = world.player.copy(inventory = world.player.inventory.addCoins(1_000)))
        if (input.purchaseSeeds > 0) world = placeDelivery(world, DeliveryKind.SEEDS, input.purchaseSeeds, input.purchaseSeeds * 4, events = events)
        if (input.purchaseFences > 0) world = placeDelivery(world, DeliveryKind.FENCES, input.purchaseFences, config.fenceBundlePrice, events = events)
        if (input.purchaseWheatSeeds > 0) world = placeDelivery(world, DeliveryKind.SEEDS, input.purchaseWheatSeeds, 35, itemType = ItemType.WHEAT_SEED, events = events)
        if (input.purchaseGates > 0) world = placeDelivery(world, DeliveryKind.FENCES, input.purchaseGates, 60, itemType = ItemType.GATE, events = events)
        if (input.purchaseBicycle && !world.bicycle.owned && world.deliveries.none { it.kind == DeliveryKind.BICYCLE }) {
            world = placeDelivery(world, DeliveryKind.BICYCLE, 1, config.bicyclePurchasePrice, events = events)
        }
        if (input.purchaseChicken && world.chickens.size < config.maxChickenPopulation) {
            world = placeDelivery(world, DeliveryKind.CHICKEN, 1, 150, events = events)
        }
        if (input.purchaseTool != null) {
            val tool = input.purchaseTool
            world = placeDelivery(world, DeliveryKind.TOOL, 1, toolPurchasePrice(tool), toolType = tool, events = events)
        }
        input.selectActiveItem?.let { item ->
            val equipped = if (world.player.activeItemId == item) null else item
            world = world.copy(
                player = world.player.copy(
                    activeItemId = equipped,
                    selectedTool = equipped?.equipmentTool() ?: ToolType.HANDS,
                    actionAnimation = null,
                ),
            )
        }
        if (input.rotateStructurePressed || input.rotateBlueprintPressed) {
            world = if (world.selectedMoveStructureId != null) handleRotateStructure(world, events)
            else world.copy(selectedBlueprintOrientation = world.selectedBlueprintOrientation.next())
        }
        if (input.confirmStructurePosition) {
            world = world.copy(selectedMoveStructureId = null)
        }
        input.nudgeStructure?.let { nudge ->
            world = handleNudgeStructure(world, nudge.first, nudge.second, events)
        }
        if (input.depositAllToBarn) world = depositAllToBarn(world, events)
        input.depositItemToBarn?.let { item -> world = depositItemToBarn(world, item, events) }
        input.withdrawBarnItem?.let { item -> world = withdrawBarnItem(world, item, input.withdrawBarnItemAmount, events) }
        input.withdrawBarnEggs?.let { count -> world = withdrawBarnEggs(world, count, events) }
        input.toggleGateId?.let { gateId -> world = toggleGate(world, gateId, events) }
        if (input.toggleWheelbarrow) world = toggleWheelbarrow(world, events)
        input.loadWheelbarrowItem?.let { item -> world = loadWheelbarrow(world, item, events) }
        input.unloadWheelbarrowItem?.let { item -> world = unloadWheelbarrowItem(world, item, events) }
        if (input.unloadWheelbarrowToBarn) world = unloadWheelbarrowToBarn(world, events)
        if (input.sellWheelbarrowCargo) world = sellWheelbarrowCargo(world, events)
        if (input.throwItemPressed) world = throwActiveItem(world, events)

        if (input.togglePencilModePressed) {
            val newMode = !world.isPencilMode
            val targetZoom = if (newMode) 0.52f else 1.45f
            val newCamera = clampCamera(world.camera.copy(zoom = targetZoom), world.bounds)
            world = world.copy(isPencilMode = newMode, selectedMoveStructureId = null, camera = newCamera)
        }
        input.pencilMoveTargetCell?.let { cell ->
            world = handlePencilClick(world, cell, events)
        }
        input.purchaseBlueprint?.let { fType ->
            world = placeDelivery(world, DeliveryKind.BLUEPRINT, 1, fType.blueprintPrice, factoryType = fType, events = events)
        }
        input.completeMinigame?.let { fType ->
            when (fType) {
                FactoryType.EGG_PACKER -> {
                    if (world.player.inventory.count(ItemType.EGG) >= 6) {
                        world = world.copy(
                            player = world.player.copy(
                                inventory = world.player.inventory
                                    .remove(ItemType.EGG, 6)
                                    .add(ItemType.EGG_CARTON, 1),
                            ),
                        )
                        events.sound(SoundCue.COINS, world.player.position)
                    }
                }
                FactoryType.VEGGIE_PACKER -> {
                    if (world.player.inventory.count(ItemType.CARROT) >= 5) {
                        world = world.copy(
                            player = world.player.copy(
                                inventory = world.player.inventory
                                    .remove(ItemType.CARROT, 5)
                                    .add(ItemType.VEGGIE_BOX, 1),
                            ),
                        )
                        events.sound(SoundCue.COINS, world.player.position)
                    }
                }
                FactoryType.BAKERY -> {
                    if (world.player.inventory.count(ItemType.WHEAT) >= 1) {
                        world = world.copy(
                            player = world.player.copy(
                                inventory = world.player.inventory
                                    .remove(ItemType.WHEAT, 1)
                                    .add(ItemType.BREAD, 2),
                            ),
                        )
                        events.sound(SoundCue.COINS, world.player.position)
                    }
                }
                FactoryType.FISH_PROCESSOR -> {
                    if (world.player.inventory.count(ItemType.FISH) >= 1) {
                        world = world.copy(
                            player = world.player.copy(
                                inventory = world.player.inventory
                                    .remove(ItemType.FISH, 1)
                                    .add(ItemType.FISH_FILLET, 1),
                            ),
                        )
                        events.sound(SoundCue.COINS, world.player.position)
                    }
                }
                else -> Unit
            }
        }
        if (input.grindWheat) {
            if (world.player.inventory.count(ItemType.WHEAT) >= 1) {
                world = world.copy(
                    player = world.player.copy(
                        inventory = world.player.inventory
                            .remove(ItemType.WHEAT, 1)
                            .add(ItemType.FLOUR, 1),
                        activeItemId = ItemType.FLOUR,
                    ),
                )
                events.sound(SoundCue.ITEM_PICKUP, world.player.position)
            }
        }
        if (input.kneadDough) {
            if (world.player.inventory.count(ItemType.FLOUR) >= 1) {
                world = world.copy(
                    player = world.player.copy(
                        inventory = world.player.inventory
                            .remove(ItemType.FLOUR, 1)
                            .add(ItemType.DOUGH, 1),
                        activeItemId = ItemType.DOUGH,
                    ),
                )
                events.sound(SoundCue.ITEM_PICKUP, world.player.position)
            }
        }
        input.startBakeryOven?.let { (factoryId, ovenIndex) ->
            val factory = world.factories.firstOrNull { it.id == factoryId }
            if (factory != null && factory.type == FactoryType.BAKERY && factory.phase == ConstructionPhase.OPERATIONAL) {
                if (world.player.inventory.count(ItemType.DOUGH) >= 1) {
                    val updatedFactory = if (ovenIndex == 1 && !factory.oven1Active && !factory.oven1Ready) {
                        factory.copy(oven1Timer = 20f, oven1Active = true, oven1Ready = false)
                    } else if (ovenIndex == 2 && !factory.oven2Active && !factory.oven2Ready) {
                        factory.copy(oven2Timer = 20f, oven2Active = true, oven2Ready = false)
                    } else factory

                    if (updatedFactory != factory) {
                        val newInventory = world.player.inventory.remove(ItemType.DOUGH, 1)
                        val newActiveItem = if (world.player.activeItemId == ItemType.DOUGH && newInventory.count(ItemType.DOUGH) <= 0) {
                            null
                        } else {
                            world.player.activeItemId
                        }
                        world = world.copy(
                            player = world.player.copy(
                                inventory = newInventory,
                                activeItemId = newActiveItem,
                            ),
                            factories = world.factories.map { if (it.id == factory.id) updatedFactory else it },
                        )
                        events.sound(SoundCue.CONSTRUCTION_HAMMER, world.player.position)
                    }
                }
            }
        }
        input.collectBakeryOven?.let { (factoryId, ovenIndex) ->
            val factory = world.factories.firstOrNull { it.id == factoryId }
            if (factory != null && factory.type == FactoryType.BAKERY) {
                var breadToAdd = 0
                val updatedFactory = if (ovenIndex == 1 && factory.oven1Ready) {
                    breadToAdd = 2
                    factory.copy(oven1Ready = false, oven1Active = false, oven1Timer = 0f)
                } else if (ovenIndex == 2 && factory.oven2Ready) {
                    breadToAdd = 2
                    factory.copy(oven2Ready = false, oven2Active = false, oven2Timer = 0f)
                } else factory

                if (breadToAdd > 0) {
                    world = world.copy(
                        player = world.player.copy(
                            inventory = world.player.inventory.add(ItemType.BREAD, breadToAdd),
                            activeItemId = ItemType.BREAD,
                        ),
                        factories = world.factories.map { if (it.id == factory.id) updatedFactory else it },
                    )
                    events.sound(SoundCue.COINS, world.player.position)
                }
            }
        }
        if (input.petAnimalPressed) {
            world = petAnimal(world, events)
        }
        if (input.removePlotPressed) {
            world = removePlotUnderOrInFrontOfPlayer(world, events)
        }
        world = handleHelperInput(world, input, events)
        world = handleAnimalRename(world, input.animalRename)
        world = handlePigSale(world, input.sellPigId, events)
        world = handlePetCommand(world, input.petCommand, events)
        if (input.toggleBicyclePressed) world = toggleBicycle(world, events)
        if (input.bicycleBellPressed && world.player.isMounted) {
            events.sound(SoundCue.BICYCLE_BELL, world.player.position, 0.58f)
        }
        if (input.grabAnimalPressed) {
            world = if (world.player.carriedAnimal != null) dropCarriedAnimal(world, events)
            else nearestAnimalReference(world, config.interactionRange)?.let { grabAnimal(world, it, events) } ?: world
        }

        world = advanceTimeAndCooldowns(world, delta, events)
        world = updateDeliveries(world, delta, events)
        world = updateTrees(world, delta, events)
        world = updateTreeSpawning(world, delta, events)
        world = updateCrops(world, delta, events)
        world = updateRockSpawning(world, delta, events)
        world = updateEggLifecycle(world, delta, events)
        world = updateChickens(world, delta)
        world = updateHelper(world, delta, events)
        world = updateAnimalsAndBall(
            world,
            delta,
            events,
            playerHasMovementIntent = input.movement.lengthSquared() >= 0.0064f && world.player.faintTimer <= 0f,
        )
        world = updateAmbientCreatures(world, delta, events)
        world = updateFox(world, delta, events)
        world = updateFactories(world, delta, events)
        world = updateCowGrazing(world, delta, events)
        world = updateGrassTuftRespawn(world, delta)
        world = updateTruck(world, delta, events)

        val fishingResult = updateFishing(world, delta, input, events)
        world = fishingResult.world

        val locksMovement = world.player.faintTimer > 0f || world.fishing.phase in setOf(
            FishingPhase.CASTING,
            FishingPhase.WAITING_FOR_BITE,
            FishingPhase.BITE,
            FishingPhase.REELING,
        )
        if (!locksMovement) {
            world = updatePlayerMovement(world, delta, input.movement, events)
            world = updateSoilTrampling(world, delta, events)
        }

        world = synchronizeCarriedEntities(world)
        if (input.throwAnimalPressed) world = throwCarriedAnimal(world, input.throwDirection, events)

        world = collectNearbyItems(world, events)

        if (input.actionHeld && world.player.faintTimer <= 0f && world.player.actionCooldown <= 0f) {
            val scaffold = world.factories.firstOrNull {
                it.phase == ConstructionPhase.SCAFFOLD &&
                    it.collider.distanceTo(world.player.position) <= config.interactionRange + 0.6f
            }
            if (scaffold != null) {
                world = contributeToScaffold(world, scaffold, events)
            }
        }

        if (
            input.actionPressed &&
            world.player.faintTimer <= 0f &&
            !fishingResult.consumedAction &&
            world.fishing.phase == FishingPhase.NONE
        ) {
            world = performToolAction(world, events)
        }

        world = synchronizeCarriedEntities(world)

        world = updateProjectiles(world, delta, events)
        world = updateRipples(world, delta)

        world = updateCamera(world, delta)
        state = world
        return FarmWorldFrame(world, events)
    }

    private fun placeDelivery(
        world: FarmWorldState,
        kind: DeliveryKind,
        amount: Int,
        price: Int,
        animalType: DomesticAnimalType? = null,
        breedIndex: Int = 0,
        toolType: ToolType? = null,
        factoryType: FactoryType? = null,
        itemType: ItemType? = null,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        if (amount <= 0 || price < 0 || world.player.inventory.coins < price) return world
        val id = world.nextEntityId
        val order = DeliveryOrderState(
            id = id,
            kind = kind,
            animalType = animalType,
            toolType = toolType,
            factoryType = factoryType,
            itemType = itemType,
            breedIndex = breedIndex.coerceAtLeast(0),
            amount = amount,
            secondsRemaining = config.truckArrivalSeconds,
        )
        events += WorldEvent.OrderPlaced(id, kind)
        return world.copy(
            deliveries = world.deliveries + order,
            nextEntityId = id + 1,
            player = world.player.copy(inventory = world.player.inventory.copy(coins = world.player.inventory.coins - price)),
            truck = if (world.truck.phase == TruckPhase.ABSENT) world.truck.copy(
                phase = TruckPhase.ARRIVING,
                position = TRUCK_START_POSITION,
                phaseTimer = config.truckArrivalSeconds,
            ) else world.truck,
        )
    }

    private fun handleAnimalRename(world: FarmWorldState, request: AnimalRenameRequest?): FarmWorldState {
        request ?: return world
        val cleanName = request.name.trim().take(18)
        if (cleanName.isBlank()) return world
        return when (request.animal.kind) {
            AnimalKind.CHICKEN -> world.copy(chickens = world.chickens.map {
                if (it.id == request.animal.id) it.copy(name = cleanName) else it
            })
            else -> world.copy(animals = world.animals.map {
                if (it.id == request.animal.id && it.type.kind == request.animal.kind) it.copy(name = cleanName) else it
            })
        }
    }

    private fun persistentHelperName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").ifBlank { "Ayudante" }.take(18)

    private fun handleHelperInput(
        world: FarmWorldState,
        input: WorldInput,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var result = world
        input.hireHelperName?.let { requestedName ->
            when {
                result.helper != null -> events += WorldEvent.HelperHireRejected("ALREADY_HIRED")
                result.player.inventory.coins < config.helperHirePrice.coerceAtLeast(0) ->
                    events += WorldEvent.HelperHireRejected("NOT_ENOUGH_COINS")
                else -> {
                    val id = result.nextEntityId
                    val helper = WorkerState(
                        id = id,
                        name = persistentHelperName(requestedName),
                        position = result.barn.doorPosition + Vector2(0f, 0.65f),
                        facing = Facing.DOWN,
                        shiftStartHour = config.helperShiftStartHour.coerceIn(0, 23),
                        shiftEndHour = config.helperShiftEndHour.coerceIn(0, 23),
                    )
                    result = result.copy(
                        helper = helper,
                        nextEntityId = id + 1,
                        player = result.player.copy(
                            inventory = result.player.inventory.copy(
                                coins = result.player.inventory.coins - config.helperHirePrice.coerceAtLeast(0),
                            ),
                        ),
                    )
                    events += WorldEvent.HelperHired(helper, config.helperHirePrice.coerceAtLeast(0))
                }
            }
        }

        input.renameHelper?.let { requestedName ->
            val helper = result.helper
            val cleanName = requestedName.trim().replace(Regex("\\s+"), " ").take(18)
            if (helper != null && cleanName.isNotBlank() && cleanName != helper.name) {
                result = result.copy(helper = helper.copy(name = cleanName))
                events += WorldEvent.HelperRenamed(helper.id, cleanName)
            }
        }

        if (input.dismissHelper) {
            result.helper?.let { helper ->
                val amount = helper.carriedEggs.coerceAtLeast(0)
                val stored = (result.barn.storedEggs + amount).coerceAtMost(result.barn.eggCapacity)
                result = result.copy(
                    helper = null,
                    barn = result.barn.copy(storedEggs = stored),
                )
                events += WorldEvent.HelperDismissed(helper.id, helper.name)
            }
        }
        return result
    }

    /** Updates the camera's unzoomed viewport after a size/orientation change. */
    fun setViewportWorldSize(size: Vector2): FarmWorldState {
        if (size.x <= 0f || size.y <= 0f) return state
        state = state.copy(camera = clampCamera(state.camera.copy(baseViewportWorldSize = size), state.bounds))
        return state
    }

    fun setZoom(zoom: Float): FarmWorldState {
        state = state.copy(camera = clampCamera(state.camera.copy(zoom = zoom.coerceIn(0.45f, 4f)), state.bounds))
        return state
    }

    fun updateGuestPlayerState(
        position: Vector2,
        facing: Facing,
        movementDirection: Vector2,
        isMoving: Boolean,
        selectedTool: ToolType = ToolType.HANDS,
        activeItemId: ItemType? = null,
        carriedAnimal: AnimalReference? = null,
        isMounted: Boolean = false,
        name: String? = null,
    ) {
        val currentGuest = state.guestPlayer ?: PlayerState(position = position)
        val updatedGuest = currentGuest.copy(
            position = position,
            facing = facing,
            movementDirection = movementDirection,
            isMoving = isMoving,
            selectedTool = selectedTool,
            activeItemId = activeItemId,
            carriedAnimal = carriedAnimal,
            isMounted = isMounted,
        )
        state = state.copy(
            guestPlayer = updatedGuest,
            guestFarmerName = name ?: state.guestFarmerName ?: "Amigo",
        )
    }

    fun removeGuestPlayer() {
        state = state.copy(guestPlayer = null, guestFarmerName = null)
    }

    fun handleGuestAction(tool: ToolType, targetPos: Vector2) {
        val guest = state.guestPlayer ?: return
        val pos = targetPos
        val events = mutableListOf<WorldEvent>()
        var updatedWorld = state
        val active = guest.activeItemId

        if (active == ItemType.CARROT) {
            val nearbyPig = updatedWorld.animals.firstOrNull {
                it.type == DomesticAnimalType.PIG && it.lifeStage == AnimalLifeStage.ADULT &&
                    it.handling == AnimalHandlingState.FREE && it.breedingCooldown <= 0f &&
                    it.position.distanceTo(pos) <= config.interactionRange + it.collisionRadius
            }
            if (nearbyPig != null) {
                val mate = updatedWorld.animals.firstOrNull {
                    it.type == DomesticAnimalType.PIG && it.id != nearbyPig.id &&
                        it.loveTimer > 0f && it.position.distanceTo(nearbyPig.position) <= 4.5f
                }
                val newAnimals = updatedWorld.animals.toMutableList()
                val idx = newAnimals.indexOfFirst { it.id == nearbyPig.id }
                if (mate != null) {
                    val spawnPos = (nearbyPig.position + mate.position) * 0.5f
                    val baby = DomesticAnimalState(
                        id = updatedWorld.nextEntityId,
                        name = "Cerdito #${updatedWorld.nextEntityId % 100}",
                        type = DomesticAnimalType.PIG,
                        position = spawnPos,
                        lifeStage = AnimalLifeStage.BABY,
                        breedingCooldown = 180f,
                    )
                    newAnimals[idx] = nearbyPig.copy(loveTimer = 0f, breedingCooldown = 180f)
                    val mateIdx = newAnimals.indexOfFirst { it.id == mate.id }
                    if (mateIdx >= 0) newAnimals[mateIdx] = mate.copy(loveTimer = 0f, breedingCooldown = 180f)
                    newAnimals.add(baby)
                } else {
                    newAnimals[idx] = nearbyPig.copy(loveTimer = 25f)
                }
                state = updatedWorld.copy(animals = newAnimals, nextEntityId = updatedWorld.nextEntityId + (if (mate != null) 1 else 0))
                return
            }
        }

        // Gate toggle
        val nearbyGate = updatedWorld.gates.firstOrNull { it.position.distanceTo(pos) <= config.interactionRange + 0.6f }
        if (nearbyGate != null) {
            val updatedGates = updatedWorld.gates.map {
                if (it.id == nearbyGate.id) it.copy(isOpen = !it.isOpen) else it
            }
            state = updatedWorld.copy(gates = updatedGates)
            return
        }

        when (tool) {
            ToolType.AXE -> {
                val nearbyTree = updatedWorld.trees.firstOrNull {
                    it.lifeState != TreeLifeState.STUMP && it.collider.distanceTo(pos) <= config.interactionRange + 0.5f
                }
                if (nearbyTree != null) {
                    val health = nearbyTree.health - 1
                    val isFalling = health <= 0
                    val away = (nearbyTree.position - pos).normalized().let {
                        if (it == Vector2.ZERO) Vector2.RIGHT else it
                    }
                    val updatedTrees = updatedWorld.trees.map {
                        if (it.id == nearbyTree.id) {
                            it.copy(
                                health = health.coerceAtLeast(0),
                                lifeState = if (isFalling) TreeLifeState.FALLING else TreeLifeState.STANDING,
                                fallProgress = if (isFalling) 0f else it.fallProgress,
                                fallDirection = if (isFalling) away else it.fallDirection,
                                shakeTimer = if (isFalling) 0f else 0.18f,
                            )
                        } else it
                    }
                    state = updatedWorld.copy(trees = updatedTrees)
                }
            }
            ToolType.WATERING_CAN -> {
                val plot = updatedWorld.plots.firstOrNull {
                    it.collider.contains(pos) || it.collider.distanceTo(pos) <= config.interactionRange
                }
                if (plot != null && plot.moisture < 1f) {
                    val updatedPlots = updatedWorld.plots.map {
                        if (it.id == plot.id) it.copy(moisture = 1f, soil = SoilState.TILLED) else it
                    }
                    state = updatedWorld.copy(plots = updatedPlots)
                }
            }
            ToolType.MILK_PAIL -> {
                val adultCow = updatedWorld.animals.firstOrNull {
                    it.type == DomesticAnimalType.COW && it.lifeStage == AnimalLifeStage.ADULT &&
                        it.position.distanceTo(pos) <= config.interactionRange + it.collisionRadius
                }
                if (adultCow != null) {
                    val dropPos = pos + Vector2(0f, 0.4f)
                    val milkItem = GroundItemState(id = updatedWorld.nextEntityId, item = ItemType.MILK, position = dropPos)
                    state = updatedWorld.copy(groundItems = updatedWorld.groundItems + milkItem, nextEntityId = updatedWorld.nextEntityId + 1)
                }
            }
            else -> {}
        }
    }

    /** Repairs contradictory mounted flags from legacy saves before any renderer sees the state. */
    private fun normalizeMountedState(world: FarmWorldState): FarmWorldState {
        if (world.player.faintTimer > 0f) {
            return world.copy(
                player = world.player.copy(isMounted = false, isMoving = false),
                bicycle = world.bicycle.copy(isMounted = false),
            )
        }
        val mounted = world.player.isMounted && world.bicycle.owned
        if (world.player.isMounted == mounted && world.bicycle.isMounted == mounted) return world
        return world.copy(
            player = world.player.copy(isMounted = mounted),
            bicycle = world.bicycle.copy(isMounted = mounted),
        )
    }

    private fun handleAnimalPurchase(
        world: FarmWorldState,
        request: AnimalPurchaseRequest?,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        request ?: return world
        val price = purchasePrice(request.type)
        if (world.player.inventory.coins < price) {
            events += WorldEvent.AnimalPurchaseRejected(request.type, "NOT_ENOUGH_COINS")
            return world
        }

        return placeDelivery(
            world = world,
            kind = DeliveryKind.ANIMAL,
            amount = 1,
            price = price,
            animalType = request.type,
            breedIndex = request.breedIndex,
            events = events,
        )
    }

    private fun updateDeliveries(world: FarmWorldState, delta: Float, events: MutableList<WorldEvent>): FarmWorldState {
        if (world.deliveries.isEmpty()) return world
        var result = world
        val initialRoute = courierRoute(world)
        var orders = world.deliveries.map { original ->
            // Old saves only had `delivered` and a signed timer. Give an already-delivered courier
            // a valid return origin instead of briefly drawing it at (0, 0).
            val order = if (
                original.courierPhase == CourierPhase.RETURNING &&
                original.courierRouteIndex < 0 &&
                original.courierPosition == Vector2.ZERO
            ) {
                original.copy(courierPosition = initialRoute.first(), courierRouteIndex = 1)
            } else {
                original
            }
            if (order.courierPhase == CourierPhase.WAITING) {
                order.copy(secondsRemaining = (order.secondsRemaining - delta).coerceAtLeast(0f))
            } else {
                order.copy(courierAnimationTime = order.courierAnimationTime + delta)
            }
        }

        // One courier handles the queue. A second order waits in the truck instead of drawing a
        // stack of overlapping delivery people.
        if (orders.none { it.courierPhase != CourierPhase.WAITING } && world.truck.phase == TruckPhase.PARKED) {
            val waitingIndex = orders.indexOfFirst { !it.delivered && it.secondsRemaining <= 0f }
            if (waitingIndex >= 0) {
                val route = courierRoute(result)
                orders = orders.replaceAt(waitingIndex) {
                    it.copy(
                        courierPhase = CourierPhase.TO_BARN,
                        courierPosition = route.last(),
                        courierFacing = Facing.LEFT,
                        courierRouteIndex = route.lastIndex - 1,
                        courierAnimationTime = 0f,
                    )
                }
            }
        }

        val activeIndex = orders.indexOfFirst { it.courierPhase != CourierPhase.WAITING }
        if (activeIndex < 0) return result.copy(deliveries = orders)
        var order = orders[activeIndex]
        val route = courierRoute(result)
        when (order.courierPhase) {
            CourierPhase.WAITING -> Unit
            CourierPhase.TO_BARN -> {
                val moved = advanceCourier(order, route, towardBarn = true, delta = delta)
                order = moved.order
                if (moved.finished) {
                    val deliveredWorld = fulfillDelivery(result, order, events)
                    if (deliveredWorld != null) {
                        result = deliveredWorld
                        order = order.copy(
                            delivered = true,
                            courierPhase = CourierPhase.RETURNING,
                            courierPosition = route.first(),
                            courierRouteIndex = 1,
                        )
                        events += WorldEvent.OrderDelivered(order.id, order.kind)
                    }
                }
                orders = orders.replaceAt(activeIndex) { order }
            }
            CourierPhase.RETURNING -> {
                val moved = advanceCourier(order, route, towardBarn = false, delta = delta)
                if (moved.finished) {
                    orders = orders.filterNot { it.id == order.id }
                } else {
                    orders = orders.replaceAt(activeIndex) { moved.order }
                }
            }
        }
        return result.copy(deliveries = orders)
    }

    private data class CourierAdvance(
        val order: DeliveryOrderState,
        val finished: Boolean,
    )

    private fun advanceCourier(
        order: DeliveryOrderState,
        route: List<Vector2>,
        towardBarn: Boolean,
        delta: Float,
    ): CourierAdvance {
        if (route.isEmpty()) return CourierAdvance(order, true)
        val lastIndex = route.lastIndex
        val routeIndex = order.courierRouteIndex
        if (towardBarn && routeIndex < 0) return CourierAdvance(order.copy(courierPosition = route.first()), true)
        if (!towardBarn && routeIndex > lastIndex) return CourierAdvance(order.copy(courierPosition = route.last()), true)

        val safeIndex = routeIndex.coerceIn(0, lastIndex)
        val target = route[safeIndex]
        val deltaToTarget = target - order.courierPosition
        val distance = deltaToTarget.length()
        val maxStep = config.courierWalkSpeed.coerceAtLeast(0.05f) * delta
        val reached = distance <= maxStep || distance <= 0.015f
        val direction = deltaToTarget.normalized()
        val position = if (reached) target else order.courierPosition + direction * maxStep
        val nextIndex = if (reached) {
            if (towardBarn) safeIndex - 1 else safeIndex + 1
        } else safeIndex
        val finished = reached && if (towardBarn) nextIndex < 0 else nextIndex > lastIndex
        return CourierAdvance(
            order.copy(
                courierPosition = position,
                courierFacing = facingFor(direction, order.courierFacing),
                courierRouteIndex = nextIndex,
            ),
            finished,
        )
    }

    /** Returns null only when the barn delivery point is temporarily blocked. */
    private fun fulfillDelivery(
        world: FarmWorldState,
        order: DeliveryOrderState,
        events: MutableList<WorldEvent>,
    ): FarmWorldState? {
        if (order.itemType != null) {
            events.sound(SoundCue.COINS, world.player.position)
            return world.copy(player = world.player.copy(inventory = world.player.inventory.add(order.itemType, order.amount)))
        }
        return when (order.kind) {
            DeliveryKind.SEEDS -> world.copy(player = world.player.copy(inventory = world.player.inventory.add(ItemType.SEED, order.amount)))
            DeliveryKind.FENCES -> world.copy(player = world.player.copy(inventory = world.player.inventory.add(ItemType.FENCE, order.amount)))
            DeliveryKind.BICYCLE -> world.copy(bicycle = world.bicycle.copy(owned = true, position = world.barn.doorPosition + Vector2(2f, 1f)))
            DeliveryKind.CHICKEN -> {
                val chickenId = order.id
                val spawn = animalPurchaseSpawnCandidates(world.barn.doorPosition)
                    .firstOrNull { canOccupy(it, 0.42f, world) } ?: (world.barn.doorPosition + Vector2(1.2f, 1f))
                val chicken = ChickenState(
                    id = chickenId,
                    name = "Gallina #${chickenId % 100}",
                    position = spawn,
                    lifeStage = ChickenLifeStage.ADULT,
                    facing = Facing.DOWN,
                    eggTimer = randomBetween(config.chickenMinEggSeconds, config.chickenMaxEggSeconds),
                )
                events.sound(SoundCue.CHICKEN_CLUCK, spawn)
                world.copy(chickens = world.chickens + chicken)
            }
            DeliveryKind.TOOL -> {
                val tool = order.toolType ?: ToolType.HOE
                events.sound(SoundCue.COINS, world.player.position)
                if (tool == ToolType.MILK_PAIL) {
                    world.copy(
                        player = world.player.copy(
                            inventory = world.player.inventory.add(ItemType.MILK_PAIL, order.amount),
                            toolDurability = world.player.toolDurability - ToolType.MILK_PAIL,
                        ),
                    )
                } else {
                    val maxDurability = defaultToolDurabilities[tool] ?: 50
                    val newDurabilities = world.player.toolDurability + (tool to maxDurability)
                    world.copy(player = world.player.copy(toolDurability = newDurabilities))
                }
            }
            DeliveryKind.BLUEPRINT -> {
                val fType = order.factoryType ?: FactoryType.DAIRY
                val blueprintTool = when (fType) {
                    FactoryType.DAIRY -> ToolType.BLUEPRINT_DAIRY
                    FactoryType.SLAUGHTERHOUSE -> ToolType.BLUEPRINT_SLAUGHTERHOUSE
                    FactoryType.EGG_PACKER -> ToolType.BLUEPRINT_EGG_PACKER
                    FactoryType.VEGGIE_PACKER -> ToolType.BLUEPRINT_VEGGIE_PACKER
                    FactoryType.BAKERY -> ToolType.BLUEPRINT_BAKERY
                    FactoryType.FISH_PROCESSOR -> ToolType.BLUEPRINT_FISH_PROCESSOR
                }
                val count = (world.player.toolDurability[blueprintTool] ?: 0) + 1
                val newDurabilities = world.player.toolDurability + (blueprintTool to count)
                events.sound(SoundCue.COINS, world.player.position)
                world.copy(player = world.player.copy(toolDurability = newDurabilities))
            }
        DeliveryKind.ANIMAL -> {
            val type = order.animalType ?: return world
            val probe = DomesticAnimalState(order.id, persistentAnimalName("", type, order.id), type, world.barn.doorPosition)
            val spawn = animalPurchaseSpawnCandidates(world.barn.doorPosition)
                .firstOrNull { canOccupy(it, probe.collisionRadius, world) } ?: return null
            val animal = probe.copy(
                position = spawn,
                behaviorTimer = randomBetween(0.8f, 2.2f),
                animationPhase = randomPhase(),
                breedIndex = order.breedIndex,
                lifeStage = AnimalLifeStage.BABY,
                specialCooldown = randomBetween(28f, 52f),
                petFollowState = if (type.isPet) PetFollowState.STAYING else null,
            )
            events += WorldEvent.AnimalPurchased(animal, purchasePrice(type))
            world.copy(animals = world.animals + animal)
        }
    }
    }

    private fun handlePigSale(
        world: FarmWorldState,
        pigId: Int?,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        pigId ?: return world
        val pig = world.animals.firstOrNull {
            it.id == pigId && it.type == DomesticAnimalType.PIG
        } ?: return world
        val reference = pig.reference
        val player = world.player.copy(
            inventory = world.player.inventory.addCoins(config.pigSalePrice),
            carriedAnimal = world.player.carriedAnimal.takeUnless { it == reference },
        )
        events += WorldEvent.PigSold(pig.id, pig.name, config.pigSalePrice)
        return world.copy(
            animals = world.animals.filterNot { it.id == pig.id },
            player = player,
        )
    }

    private fun handlePetCommand(
        world: FarmWorldState,
        request: PetCommandRequest?,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        request ?: return world
        if (request.command == PetCommandType.RELEASE) {
            val targetId = request.animalId ?: return world
            val petExists = world.animals.any { it.id == targetId && it.type.isPet }
            if (!petExists) return world
            val newAnimals = world.animals.filterNot { it.id == targetId && it.type.isPet }
            val newPlayer = if (world.player.carriedAnimal?.id == targetId) world.player.copy(carriedAnimal = null) else world.player
            val newBall = if (world.ball.holderAnimalId == targetId || world.ball.targetPetId == targetId) {
                world.ball.copy(phase = BallPhase.ON_GROUND, holderAnimalId = null, targetPetId = null)
            } else world.ball
            events += WorldEvent.PetCommanded(targetId, PetCommandType.RELEASE)
            events.sound(SoundCue.PET_CALL, world.player.position)
            return world.copy(animals = newAnimals, player = newPlayer, ball = newBall)
        }
        var changed = false
        val activePetIds = world.animals.filter {
            it.type.isPet && it.petFollowState in setOf(PetFollowState.FOLLOWING, PetFollowState.CALLED)
        }.map { it.id }.toSet()
        val animals = world.animals.map { animal ->
            if (
                !animal.type.isPet ||
                (request.animalId != null && request.animalId != animal.id)
            ) return@map animal
            if (
                request.command in setOf(PetCommandType.FOLLOW, PetCommandType.CALL) &&
                animal.id !in activePetIds && activePetIds.size >= 2
            ) return@map animal
            changed = true
            events += WorldEvent.PetCommanded(animal.id, request.command)
            when (request.command) {
                PetCommandType.FOLLOW -> animal.copy(
                    petFollowState = PetFollowState.FOLLOWING,
                    petCallTimer = 0f,
                )
                PetCommandType.STAY -> animal.copy(
                    velocity = Vector2.ZERO,
                    behavior = DomesticAnimalBehavior.IDLE,
                    petFollowState = PetFollowState.STAYING,
                    petCallTimer = 0f,
                )
                PetCommandType.CALL -> animal.copy(
                    petFollowState = PetFollowState.CALLED,
                    petCallTimer = 3f,
                )
                PetCommandType.RELEASE -> animal
            }
        }
        if (changed) events.sound(SoundCue.PET_CALL, world.player.position)
        return if (changed) world.copy(animals = animals) else world
    }

    private fun toggleBicycle(
        world: FarmWorldState,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        if (!world.bicycle.owned) return world
        if (world.player.isMounted) {
            val position = world.player.position - facingVector(world.player.facing) * 0.8f
            events += WorldEvent.BicycleDismounted(world.bicycle.id, position)
            events.sound(SoundCue.BICYCLE_BELL, world.player.position, 0.65f)
            return world.copy(
                player = world.player.copy(isMounted = false),
                bicycle = world.bicycle.copy(position = position, isMounted = false),
            )
        }
        if (world.player.position.distanceTo(world.bicycle.position) > config.bicycleMountRange) return world
        events += WorldEvent.BicycleMounted(world.bicycle.id)
        events.sound(SoundCue.BICYCLE_BELL, world.player.position, 0.65f)
        return world.copy(
            player = world.player.copy(isMounted = true),
            bicycle = world.bicycle.copy(position = world.player.position, isMounted = true),
        )
    }

    private fun advanceTimeAndCooldowns(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val oldAnimation = world.animation
        val elapsed = oldAnimation.elapsedSeconds + delta
        val breezePhase = wrapRadians(oldAnimation.breezePhase + delta * 0.72f)
        val animation = AmbientAnimationState(
            elapsedSeconds = elapsed,
            grassPhase = wrapRadians(oldAnimation.grassPhase + delta * 2.2f),
            waterPhase = wrapRadians(oldAnimation.waterPhase + delta * 1.35f),
            breezePhase = breezePhase,
            breezeStrength = 0.18f + (sin(breezePhase) * 0.5f + 0.5f) * 0.32f,
        )

        var secondsOfDay = world.secondsOfDay + delta * 60f
        var day = world.day
        while (secondsOfDay >= SECONDS_PER_DAY) {
            secondsOfDay -= SECONDS_PER_DAY
            day += 1
        }

        val actionAnimation = world.player.actionAnimation?.let { action ->
            val remaining = action.remainingSeconds - delta
            if (remaining <= 0f) null else action.copy(remainingSeconds = remaining)
        }
        val activeEmote = world.player.activeEmote?.let { emote ->
            val remaining = emote.remainingSeconds - delta
            if (remaining <= 0f) null else emote.copy(remainingSeconds = remaining)
        }
        val faintTimer = (world.player.faintTimer - delta).coerceAtLeast(0f)
        if (world.player.faintTimer > 0f && faintTimer <= 0f) events += WorldEvent.PlayerRecovered
        val player = world.player.copy(
            actionCooldown = (world.player.actionCooldown - delta).coerceAtLeast(0f),
            blockedSoundCooldown = (world.player.blockedSoundCooldown - delta).coerceAtLeast(0f),
            actionAnimation = actionAnimation,
            activeEmote = activeEmote,
            faintTimer = faintTimer,
            foxBites = if (world.player.faintTimer > 0f && faintTimer <= 0f) 0 else world.player.foxBites,
        )
        return world.copy(animation = animation, secondsOfDay = secondsOfDay, day = day, player = player)
    }

    private fun updatePlayerMovement(
        world: FarmWorldState,
        delta: Float,
        rawMovement: Vector2,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val movement = if (rawMovement.lengthSquared() > 1f) rawMovement.normalized() else rawMovement
        if (movement.lengthSquared() < 0.0064f || delta <= 0f) {
            return world.copy(player = world.player.copy(isMoving = false, footstepTimer = 0f))
        }

        if (world.isPencilMode) {
            val panSpeed = 22f
            val panDisplacement = movement * (panSpeed * delta)
            val newCenter = (world.camera.center + panDisplacement).coerceIn(world.bounds, 0f)
            return world.copy(camera = clampCamera(world.camera.copy(center = newCenter), world.bounds))
        }

        val direction = movement.normalized()
        val speed = config.playerSpeed * if (world.player.isMounted) config.bicycleSpeedMultiplier else 1f
        val displacement = direction * (speed * delta)
        val start = world.player.position
        var resolved = start
        val xCandidate = Vector2(start.x + displacement.x, start.y)
        if (canOccupy(xCandidate, config.playerRadius, world)) resolved = xCandidate
        val yCandidate = Vector2(resolved.x, start.y + displacement.y)
        if (canOccupy(yCandidate, config.playerRadius, world)) resolved = yCandidate

        val attemptedBlocked = resolved.distanceSquaredTo(start + displacement) > 0.0001f
        var blockedCooldown = world.player.blockedSoundCooldown
        if (attemptedBlocked && blockedCooldown <= 0f) {
            val attemptedCircle = CollisionCircle(start + displacement, config.playerRadius)
            val waterBlocked = world.waterBodies.any { it.shape.intersects(attemptedCircle) }
            events.sound(if (waterBlocked) SoundCue.WATER_BLOCKED else SoundCue.COLLISION, start)
            blockedCooldown = 0.32f
        }

        val actuallyMoving = resolved.distanceSquaredTo(start) > 0.00001f
        var footstepTimer = world.player.footstepTimer - delta
        if (actuallyMoving && footstepTimer <= 0f) {
            val volume = if (world.player.isMounted) 0.28f else 0.72f
            events.sound(terrainAt(resolved, world).footstepCue(), resolved, volume)
            footstepTimer = if (world.player.isMounted) 0.46f else 0.31f
        }

        val playerWithMovement = world.player.copy(
            position = resolved,
            facing = facingFor(direction, world.player.facing),
            movementDirection = direction,
            isMoving = actuallyMoving,
            footstepTimer = footstepTimer,
            blockedSoundCooldown = blockedCooldown,
        )
        val wb = if (world.wheelbarrow.isBeingPushed) {
            val wbOffset = facingVector(playerWithMovement.facing) * 0.95f
            world.wheelbarrow.copy(position = resolved + wbOffset)
        } else world.wheelbarrow
        return world.copy(
            player = playerWithMovement,
            wheelbarrow = wb,
        )
    }

    private fun updateSoilTrampling(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val playerPos = world.player.position
        val isMoving = world.player.isMoving
        var modified = false
        val updatedPlots = world.plots.mapNotNull { plot ->
            if (plot.soil != SoilState.TILLED) return@mapNotNull plot
            val onPlot = plot.collider.expanded(0.12f).contains(playerPos)
            if (onPlot && isMoving) {
                val newTrample = plot.tramplingSeconds + delta
                if (newTrample >= 0.65f) {
                    modified = true
                    events.sound(SoundCue.FOOTSTEP_DIRT, plot.position, 0.8f)
                    events += WorldEvent.PlotChanged(plot.id, null)
                    null
                } else {
                    plot.copy(tramplingSeconds = newTrample)
                }
            } else if (plot.tramplingSeconds > 0f) {
                plot.copy(tramplingSeconds = (plot.tramplingSeconds - delta * 0.5f).coerceAtLeast(0f))
            } else {
                plot
            }
        }
        return if (modified || updatedPlots != world.plots) world.copy(plots = updatedPlots) else world
    }

    private fun updateTrees(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var nextId = world.nextEntityId
        val drops = world.groundItems.toMutableList()
        val trees = buildList {
            world.trees.forEach { tree ->
            when (tree.lifeState) {
                TreeLifeState.STANDING -> add(
                    tree.copy(
                        shakeTimer = (tree.shakeTimer - delta).coerceAtLeast(0f),
                        shakeCooldown = (tree.shakeCooldown - delta).coerceAtLeast(0f),
                    ),
                )
                TreeLifeState.FALLING -> {
                    val progress = tree.fallProgress + delta / config.treeFallSeconds
                    if (progress >= 1f) {
                        drops += GroundItemState(
                            id = nextId++,
                            item = ItemType.WOOD,
                            amount = 5,
                            position = tree.position + tree.fallDirection * 1.1f,
                            animationPhase = randomPhase(),
                        )
                        events += WorldEvent.TreeFelled(tree.id)
                    } else {
                        add(tree.copy(fallProgress = progress))
                    }
                }
                // Legacy saves can still contain stumps and the axe can clear them.
                TreeLifeState.STUMP -> add(tree)
            }
            }
        }
        return world.copy(trees = trees, groundItems = drops, nextEntityId = nextId)
    }

    private fun updateTreeSpawning(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val timer = world.treeSpawning.secondsUntilNext - delta
        if (timer > 0f) {
            return world.copy(treeSpawning = world.treeSpawning.copy(secondsUntilNext = timer))
        }
        if (world.trees.count { it.lifeState == TreeLifeState.STANDING } >= config.maxWorldTrees) {
            return world.copy(
                treeSpawning = world.treeSpawning.copy(secondsUntilNext = randomTreeSpawnDelay()),
            )
        }

        repeat(config.treeSpawnAttempts.coerceAtLeast(0)) {
            val position = randomWorldPosition(world.bounds, margin = 2.6f)
            if (!canSpawnTreeAt(position, world)) return@repeat
            val id = world.nextEntityId
            val tree = TreeState(
                id = id,
                position = position,
                health = config.treeAxeHits.coerceAtLeast(1),
                windPhase = randomPhase(),
            )
            events += WorldEvent.TreeSpawned(id, position)
            return world.copy(
                trees = world.trees + tree,
                treeSpawning = TreeSpawnState(
                    secondsUntilNext = randomTreeSpawnDelay(),
                    totalSpawned = world.treeSpawning.totalSpawned + 1,
                ),
                nextEntityId = id + 1,
            )
        }
        return world.copy(
            treeSpawning = world.treeSpawning.copy(
                secondsUntilNext = config.treeSpawnRetrySeconds.coerceAtLeast(1f),
            ),
        )
    }

    private fun updateCrops(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val plots = world.plots.map { plot ->
            val newMoisture = (plot.moisture - delta * 0.0065f).coerceIn(0f, 1f)
            val crop = plot.crop ?: return@map plot.copy(moisture = newMoisture)
            if (crop.stage == CropStage.MATURE || newMoisture <= 0f) return@map plot.copy(moisture = newMoisture)

            val growthMultiplier = 0.35f + newMoisture * 0.65f
            val growth = (crop.growthSeconds + delta * growthMultiplier).coerceAtMost(crop.type.totalGrowthSeconds)
            val stage = cropStageFor(growth / crop.type.totalGrowthSeconds)
            if (stage != crop.stage) events += WorldEvent.PlotChanged(plot.id, stage)
            plot.copy(moisture = newMoisture, crop = crop.copy(growthSeconds = growth, stage = stage))
        }
        return world.copy(plots = plots)
    }

    private fun updateRockSpawning(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val timer = world.rockSpawning.secondsUntilNext - delta
        if (timer > 0f) {
            return world.copy(rockSpawning = world.rockSpawning.copy(secondsUntilNext = timer))
        }

        val rockCount = world.staticObstacles.count { it.kind == ObstacleKind.ROCK }
        if (rockCount >= config.maxWorldRocks || config.rockSpawnAttempts <= 0) {
            return world.copy(
                rockSpawning = world.rockSpawning.copy(secondsUntilNext = randomRockSpawnDelay()),
            )
        }

        repeat(config.rockSpawnAttempts) {
            val radius = randomBetween(config.minRockSpawnRadius, config.maxRockSpawnRadius)
            val margin = radius + 0.45f
            val position = Vector2(
                randomBetween(world.bounds.left + margin, world.bounds.right - margin),
                randomBetween(world.bounds.top + margin, world.bounds.bottom - margin),
            )
            if (!canSpawnRockAt(position, radius, world)) return@repeat

            val id = world.nextEntityId
            val obstacle = StaticObstacleState(
                id = id,
                kind = ObstacleKind.ROCK,
                collider = CollisionCircle(position, radius),
                sortY = position.y + radius * 0.65f,
                hitPoints = config.rockPickaxeHits.coerceAtLeast(1),
            )
            events += WorldEvent.RockSpawned(id, position)
            return world.copy(
                staticObstacles = world.staticObstacles + obstacle,
                rockSpawning = RockSpawnState(
                    secondsUntilNext = randomRockSpawnDelay(),
                    totalSpawned = world.rockSpawning.totalSpawned + 1,
                ),
                nextEntityId = id + 1,
            )
        }

        return world.copy(
            rockSpawning = world.rockSpawning.copy(secondsUntilNext = config.rockSpawnRetrySeconds.coerceAtLeast(1f)),
        )
    }

    private fun updateEggLifecycle(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var nextId = world.nextEntityId
        val chickens = world.chickens.toMutableList()
        val remainingEggs = mutableListOf<EggState>()
        world.eggs.forEach { egg ->
            val aged = egg.copy(ageSeconds = egg.ageSeconds + delta)
            if (
                aged.fertile &&
                aged.ageSeconds >= aged.hatchAtSeconds &&
                chickens.size < config.maxChickenPopulation
            ) {
                val chickenId = nextId++
                chickens += ChickenState(
                    id = chickenId,
                    position = aged.position,
                    lifeStage = ChickenLifeStage.CHICK,
                    behavior = ChickenBehavior.IDLE,
                    behaviorTimer = randomBetween(0.8f, 2f),
                    animationPhase = randomPhase(),
                    eggTimer = Float.POSITIVE_INFINITY,
                    cluckTimer = randomBetween(7f, 16f),
                )
                events += WorldEvent.EggHatched(aged.id, chickenId)
            } else {
                remainingEggs += aged
            }
        }
        return enforceGroundEggCap(
            world.copy(eggs = remainingEggs, chickens = chickens, nextEntityId = nextId),
        )
    }

    private fun updateChickens(
        world: FarmWorldState,
        delta: Float,
    ): FarmWorldState {
        var nextId = world.nextEntityId
        val eggs = world.eggs.toMutableList()
        val originalChickens = world.chickens
        val updated = originalChickens.map { chicken ->
            if (chicken.handling == AnimalHandlingState.CARRIED) {
                return@map chicken.copy(
                    velocity = Vector2.ZERO,
                    behavior = ChickenBehavior.IDLE,
                    animationTime = chicken.animationTime + delta,
                )
            }
            if (chicken.handling == AnimalHandlingState.THROWN) {
                val timer = (chicken.thrownTimer - delta).coerceAtLeast(0f)
                val attempted = chicken.position + chicken.velocity * delta
                val movement = findChickenMovement(
                    chicken.position,
                    attempted,
                    chicken.velocity.normalized(),
                    chicken.collisionRadius,
                    world,
                )
                return@map chicken.copy(
                    position = chicken.position + movement,
                    velocity = if (timer <= 0f || movement == Vector2.ZERO) Vector2.ZERO else chicken.velocity * 0.9f,
                    facing = facingFor(chicken.velocity, chicken.facing),
                    handling = if (timer <= 0f || movement == Vector2.ZERO) {
                        AnimalHandlingState.FREE
                    } else AnimalHandlingState.THROWN,
                    thrownTimer = timer,
                    animationTime = chicken.animationTime + delta,
                )
            }

            var current = chicken.copy(
                ageSeconds = chicken.ageSeconds + delta,
                animationTime = chicken.animationTime + delta,
                behaviorTimer = chicken.behaviorTimer - delta,
                cluckTimer = chicken.cluckTimer - delta,
                eggTimer = chicken.eggTimer - delta,
            )

            if (current.lifeStage == ChickenLifeStage.CHICK && current.ageSeconds >= config.chickGrowthSeconds) {
                current = current.copy(
                    lifeStage = ChickenLifeStage.ADULT,
                    eggTimer = randomEggDelay(),
                )
            }

            if (current.cluckTimer <= 0f) {
                current = current.copy(cluckTimer = randomBetween(8f, 20f))
            }

            if (current.lifeStage == ChickenLifeStage.ADULT && current.eggTimer <= 0f) {
                val cap = config.maxGroundEggs.coerceAtLeast(0)
                if (cap <= 0) {
                    current = current.copy(eggTimer = randomEggDelay())
                    return@map current
                }
                // Keep the freshest eggs: once the hard cap is reached, the oldest loose egg
                // quietly expires before the new one is added. This bounds simulation/render cost.
                while (eggs.size >= cap) {
                    val oldest = eggs.maxWithOrNull(
                        compareBy<EggState> { it.ageSeconds }.thenBy { -it.id },
                    ) ?: break
                    eggs.remove(oldest)
                }
                val fertile = random.nextFloat() < config.eggHatchChance
                eggs += EggState(
                    id = nextId++,
                    position = current.position,
                    fertile = fertile,
                    hatchAtSeconds = if (fertile) config.eggHatchSeconds else Float.POSITIVE_INFINITY,
                    animationPhase = randomPhase(),
                )
                current = current.copy(eggTimer = randomEggDelay())
            }

            val fromPlayer = current.position - world.player.position
            val playerDistance = fromPlayer.length()
            val followsSeedBait =
                world.player.selectedTool == ToolType.SEED_BAG &&
                    world.player.inventory.count(ItemType.SEED) > 0 &&
                    playerDistance <= config.baitFollowRadius
            current = when {
                followsSeedBait -> current.copy(
                    behavior = ChickenBehavior.FOLLOW_BAIT,
                    behaviorTimer = 0.4f,
                    desiredDirection = if (playerDistance <= 1.05f) {
                        Vector2.ZERO
                    } else (world.player.position - current.position).normalized(),
                )
                playerDistance < config.chickenFleeDistance -> current.copy(
                    behavior = ChickenBehavior.FLEE,
                    behaviorTimer = 0.55f,
                    desiredDirection = if (fromPlayer.lengthSquared() < 0.001f) randomDirection() else fromPlayer.normalized(),
                )
                current.behavior == ChickenBehavior.FLEE && current.behaviorTimer > 0f -> current
                current.behaviorTimer <= 0f -> chooseChickenBehavior(current)
                else -> current
            }

            val baseDirection = when (current.behavior) {
                ChickenBehavior.WANDER, ChickenBehavior.FLEE, ChickenBehavior.FOLLOW_BAIT -> current.desiredDirection
                ChickenBehavior.IDLE, ChickenBehavior.PECK -> Vector2.ZERO
            }
            val separation = chickenSeparation(current, originalChickens)
            val moveDirection = (baseDirection + separation).normalized()
            val speed = if (current.behavior == ChickenBehavior.FLEE) config.chickenFleeSpeed else config.chickenWalkSpeed
            val attempted = current.position + moveDirection * (speed * delta)
            val movement = if (moveDirection == Vector2.ZERO) {
                Vector2.ZERO
            } else {
                findChickenMovement(current.position, attempted, moveDirection, current.collisionRadius, world)
            }
            val rawPosition = current.position + movement
            val margin = 1.35f + current.collisionRadius
            val newPosition = Vector2(
                rawPosition.x.coerceIn(world.bounds.left + margin, world.bounds.right - margin),
                rawPosition.y.coerceIn(world.bounds.top + margin, world.bounds.bottom - margin),
            )
            val touchesEdge = newPosition != rawPosition ||
                newPosition.x <= world.bounds.left + margin + 0.15f ||
                newPosition.x >= world.bounds.right - margin - 0.15f ||
                newPosition.y <= world.bounds.top + margin + 0.15f ||
                newPosition.y >= world.bounds.bottom - margin - 0.15f
            if (moveDirection != Vector2.ZERO && movement == Vector2.ZERO) {
                current = current.copy(
                    desiredDirection = randomDirection(),
                    behaviorTimer = randomBetween(0.35f, 0.9f),
                )
            }
            current.copy(
                position = newPosition,
                velocity = if (delta > 0f) movement / delta else Vector2.ZERO,
                facing = if (movement.lengthSquared() > 0.00001f) facingFor(movement, current.facing) else current.facing,
                desiredDirection = if (touchesEdge) (world.bounds.center - newPosition).normalized() else current.desiredDirection,
                behaviorTimer = if (touchesEdge) randomBetween(0.8f, 1.5f) else current.behaviorTimer,
            )
        }
        return world.copy(chickens = updated, eggs = eggs, nextEntityId = nextId)
    }

    private fun enforceGroundEggCap(world: FarmWorldState): FarmWorldState {
        val cap = config.maxGroundEggs.coerceAtLeast(0)
        if (world.eggs.size <= cap) return world
        val overflow = world.eggs.size - cap
        val expiredIds = world.eggs
            .sortedWith(compareByDescending<EggState> { it.ageSeconds }.thenBy { it.id })
            .take(overflow)
            .mapTo(mutableSetOf()) { it.id }
        return world.copy(eggs = world.eggs.filterNot { it.id in expiredIds })
    }

    private fun updateHelper(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var helper = world.helper ?: return world
        helper = helper.copy(animationTime = helper.animationTime + delta)
        var eggs = world.eggs
        var barn = world.barn
        val hour = (world.secondsOfDay / 3_600f).toInt().coerceIn(0, 23)
        // Generous daylight shift from 6:00 to 22:00
        val onShift = hour in 6..22

        val barnClearancePoint = Vector2(32f, 15.5f)
        val barnDoorPos = world.barn.doorPosition
        val maxBasket = 8

        // If carrying eggs and (basket full OR no more eggs to pick up)
        if (helper.carriedEggs >= maxBasket || (helper.carriedEggs > 0 && eggs.isEmpty())) {
            val distToDoor = helper.position.distanceTo(barnDoorPos)
            val distToClearance = helper.position.distanceTo(barnClearancePoint)
            if (distToDoor <= 2.2f || (distToClearance <= 0.9f && helper.position.y <= 16f)) {
                val room = (barn.eggCapacity - barn.storedEggs).coerceAtLeast(0)
                val deposited = helper.carriedEggs.coerceAtMost(if (room > 0) room else helper.carriedEggs)
                if (deposited > 0) {
                    barn = barn.copy(storedEggs = barn.storedEggs + deposited)
                    events += WorldEvent.HelperStoredEggs(helper.id, deposited, barn.storedEggs)
                }
                helper = helper.copy(
                    carriedEggs = 0,
                    velocity = Vector2.ZERO,
                    behavior = if (onShift) WorkerBehavior.IDLE else WorkerBehavior.OFF_DUTY,
                    targetEggId = null,
                )
            } else {
                val targetNav = if (helper.position.y > 17f && kotlin.math.abs(helper.position.x - barnClearancePoint.x) > 2.5f) {
                    barnClearancePoint
                } else {
                    barnDoorPos + Vector2(0f, 0.8f)
                }
                helper = moveHelperTowards(helper, targetNav, world, delta, WorkerBehavior.TO_BARN)
            }
            return world.copy(helper = helper, eggs = eggs, barn = barn)
        }

        if (!onShift && helper.carriedEggs == 0) {
            val distanceToClearance = helper.position.distanceTo(barnClearancePoint)
            helper = if (distanceToClearance > 1.2f) {
                moveHelperTowards(helper, barnClearancePoint, world, delta, WorkerBehavior.TO_BARN)
            } else {
                helper.copy(
                    position = barnClearancePoint,
                    velocity = Vector2.ZERO,
                    behavior = WorkerBehavior.OFF_DUTY,
                    targetEggId = null,
                )
            }
            return world.copy(helper = helper)
        }

        val target = eggs.firstOrNull { it.id == helper.targetEggId }
            ?: eggs.minByOrNull { it.position.distanceTo(helper.position) }
        if (target == null) {
            // No eggs left to collect
            if (helper.carriedEggs > 0) {
                // Deposit remaining carried eggs
                helper = moveHelperTowards(helper, barnClearancePoint, world, delta, WorkerBehavior.TO_BARN)
            } else {
                val idleRestPoint = Vector2(32f, 16.5f)
                if (helper.position.distanceTo(idleRestPoint) > 1.5f) {
                    helper = moveHelperTowards(helper, idleRestPoint, world, delta, WorkerBehavior.IDLE)
                } else {
                    helper = helper.copy(velocity = Vector2.ZERO, behavior = WorkerBehavior.IDLE, targetEggId = null)
                }
            }
            return world.copy(helper = helper, eggs = eggs, barn = barn)
        }

        val distToEgg = helper.position.distanceTo(target.position)
        if (distToEgg <= config.helperPickupRange + 0.35f) {
            eggs = eggs.filterNot { it.id == target.id }
            val newCarried = helper.carriedEggs + 1
            events += WorldEvent.HelperPickedUpEgg(helper.id, target.id)
            val nextEgg = eggs.minByOrNull { it.position.distanceTo(helper.position) }
            val shouldReturn = newCarried >= maxBasket || nextEgg == null
            helper = helper.copy(
                velocity = Vector2.ZERO,
                behavior = if (shouldReturn) WorkerBehavior.TO_BARN else WorkerBehavior.TO_EGG,
                targetEggId = nextEgg?.id,
                carriedEggs = newCarried,
            )
        } else {
            // Waypoint clearance: if near the barn door, step south to barnClearancePoint first
            val navTarget = if (helper.position.y < 15.2f && target.position.y >= 15.2f) {
                barnClearancePoint
            } else {
                target.position
            }
            helper = moveHelperTowards(
                helper.copy(targetEggId = target.id),
                navTarget,
                world,
                delta,
                WorkerBehavior.TO_EGG,
            )
        }
        return world.copy(helper = helper, eggs = eggs, barn = barn)
    }

    private fun moveHelperTowards(
        helper: WorkerState,
        target: Vector2,
        world: FarmWorldState,
        delta: Float,
        behavior: WorkerBehavior,
    ): WorkerState {
        val offset = target - helper.position
        val direction = offset.normalized()
        if (direction == Vector2.ZERO || delta <= 0f) {
            return helper.copy(velocity = Vector2.ZERO, behavior = behavior)
        }
        val distance = offset.length()
        val step = (config.helperWalkSpeed.coerceAtLeast(0.05f) * delta).coerceAtMost(distance)
        val attempted = helper.position + direction * step
        var movement = findAnimalMovement(helper.position, attempted, direction, config.helperRadius, world)
        if (movement.lengthSquared() < 0.00001f && distance > 0.3f) {
            // Sidestep deflection if blocked by a corner or fence
            val sideDirs = listOf(Vector2(-direction.y, direction.x), Vector2(direction.y, -direction.x))
            for (sd in sideDirs) {
                val sideAttempt = helper.position + sd * step
                val sideMove = findAnimalMovement(helper.position, sideAttempt, sd, config.helperRadius, world)
                if (sideMove.lengthSquared() > 0.00001f) {
                    movement = sideMove
                    break
                }
            }
        }
        return helper.copy(
            position = helper.position + movement,
            velocity = if (delta > 0f) movement / delta else Vector2.ZERO,
            facing = if (movement.lengthSquared() > 0.00001f) facingFor(movement, helper.facing) else helper.facing,
            behavior = behavior,
        )
    }

    private fun isHourInShift(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }

    private fun updateAnimalsAndBall(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
        playerHasMovementIntent: Boolean,
    ): FarmWorldState {
        var ball = advanceBall(world.ball, world, delta)
        var groundItems = world.groundItems
        var nextEntityId = world.nextEntityId
        val freePets = world.animals.filter { it.type.isPet && it.handling == AnimalHandlingState.FREE }
        val playAssignments = mutableMapOf<Int, Int>()
        val playStartedIds = mutableSetOf<Int>()
        val playRejectedIds = mutableSetOf<Int>()

        // Preserve an encounter already in progress before considering a new 50/50 roll.
        freePets.sortedBy { it.id }.forEach { pet ->
            if (pet.id in playAssignments || pet.behavior != DomesticAnimalBehavior.PLAY_WITH_PET || pet.behaviorTimer <= 0f) {
                return@forEach
            }
            val mate = freePets.firstOrNull { it.id == pet.playMateId } ?: return@forEach
            if (
                mate.id !in playAssignments &&
                mate.position.distanceTo(pet.position) <= 3.5f &&
                pet.position.distanceTo(world.player.position) <= config.petCritterPlayerLimit &&
                mate.position.distanceTo(world.player.position) <= config.petCritterPlayerLimit
            ) {
                playAssignments[pet.id] = mate.id
                playAssignments[mate.id] = pet.id
            }
        }

        // Evaluate a nearby pair once per social cooldown instead of effectively rolling every
        // frame. Both participants share the same result, so an encounter is exactly 50/50.
        val evaluated = mutableSetOf<Int>()
        freePets.sortedBy { it.id }.forEach { pet ->
            if (
                pet.id in playAssignments || pet.id in evaluated || pet.socialCooldown > 0f ||
                pet.petFollowState == PetFollowState.STAYING ||
                pet.behavior in setOf(
                    DomesticAnimalBehavior.CHASE_CREATURE,
                    DomesticAnimalBehavior.CHASE_FOX,
                    DomesticAnimalBehavior.FETCH_BALL,
                    DomesticAnimalBehavior.RETURN_BALL,
                )
            ) return@forEach
            val mate = freePets.asSequence()
                .filter { it.id != pet.id && it.id !in playAssignments && it.id !in evaluated }
                .filter { it.socialCooldown <= 0f && it.petFollowState != PetFollowState.STAYING }
                .filter { it.behavior !in setOf(DomesticAnimalBehavior.CHASE_CREATURE, DomesticAnimalBehavior.CHASE_FOX) }
                .filter { it.position.distanceTo(pet.position) <= 2.8f }
                .filter { it.position.distanceTo(world.player.position) <= 4.8f }
                .minByOrNull { it.position.distanceSquaredTo(pet.position) }
                ?: return@forEach
            evaluated += pet.id
            evaluated += mate.id
            if (random.nextFloat() < config.petPlayChancePerEncounter.coerceIn(0f, 1f)) {
                playAssignments[pet.id] = mate.id
                playAssignments[mate.id] = pet.id
                playStartedIds += pet.id
                playStartedIds += mate.id
            } else {
                playRejectedIds += pet.id
                playRejectedIds += mate.id
            }
        }
        if (ball.phase == BallPhase.ON_GROUND) {
            val currentTarget = freePets.firstOrNull { it.id == ball.targetPetId }
            val fetcher = currentTarget ?: freePets
                .filter { it.position.distanceTo(ball.position) <= config.petBallInterestRadius }
                .minByOrNull { it.position.distanceSquaredTo(ball.position) }
            ball = ball.copy(targetPetId = fetcher?.id)
        }

        val updated = mutableListOf<DomesticAnimalState>()
        world.animals.forEach { original ->
            var animal = original.copy(
                animationTime = original.animationTime + delta,
                ageSeconds = original.ageSeconds + delta,
                lifeStage = if (original.lifeStage == AnimalLifeStage.BABY && original.ageSeconds + delta >= config.animalBabyGrowthSeconds) AnimalLifeStage.ADULT else original.lifeStage,
                specialCooldown = (original.specialCooldown - delta).coerceAtLeast(0f),
                critterChaseCooldown = (original.critterChaseCooldown - delta).coerceAtLeast(0f),
                socialCooldown = when (original.id) {
                    in playStartedIds -> config.petSocialCooldownSeconds.coerceAtLeast(0.1f)
                    in playRejectedIds -> 8.5f
                    else -> (original.socialCooldown - delta).coerceAtLeast(0f)
                },
                milkReadyInSeconds = if (original.type == DomesticAnimalType.COW && original.lifeStage == AnimalLifeStage.ADULT) {
                    (original.milkReadyInSeconds - delta).coerceAtLeast(0f)
                } else Float.POSITIVE_INFINITY,
            )
            if (animal.handling == AnimalHandlingState.CARRIED) {
                updated += animal.copy(
                    velocity = Vector2.ZERO,
                    behavior = DomesticAnimalBehavior.CARRIED,
                )
                return@forEach
            }
            if (animal.handling == AnimalHandlingState.THROWN) {
                val timer = (animal.thrownTimer - delta).coerceAtLeast(0f)
                val attempted = animal.position + animal.velocity * delta
                val movement = findAnimalMovement(
                    start = animal.position,
                    attempted = attempted,
                    direction = animal.velocity.normalized(),
                    radius = animal.collisionRadius,
                    world = world,
                )
                val stopped = timer <= 0f || movement == Vector2.ZERO
                updated += animal.copy(
                    position = animal.position + movement,
                    velocity = if (stopped) Vector2.ZERO else animal.velocity * 0.9f,
                    facing = facingFor(animal.velocity, animal.facing),
                    handling = if (stopped) AnimalHandlingState.FREE else AnimalHandlingState.THROWN,
                    thrownTimer = timer,
                    behavior = if (stopped) DomesticAnimalBehavior.IDLE else DomesticAnimalBehavior.THROWN,
                    behaviorTimer = if (stopped) randomBetween(0.6f, 1.4f) else animal.behaviorTimer,
                )
                return@forEach
            }

            val toPlayer = world.player.position - animal.position
            val playerDistance = toPlayer.length()
            val carryingBall = ball.phase == BallPhase.WITH_PET && ball.holderAnimalId == animal.id
            val fetchingBall = ball.phase == BallPhase.ON_GROUND && ball.targetPetId == animal.id
            val followsCarrot =
                animal.type == DomesticAnimalType.PIG &&
                    world.player.selectedTool == ToolType.CARROT_BAIT &&
                    world.player.inventory.count(ItemType.CARROT) > 0 &&
                    playerDistance <= config.baitFollowRadius

            val nearbyWater = if (animal.type == DomesticAnimalType.CAT && animal.lifeStage == AnimalLifeStage.ADULT) {
                world.waterBodies.minByOrNull { animal.position.distanceSquaredTo(shapeCenter(it.shape)) }
            } else null
            val canFish = nearbyWater != null && animal.specialCooldown <= 0f &&
                animal.position.distanceTo(shapeCenter(nearbyWater.shape)) <= 5.2f
            if (canFish) {
                groundItems = groundItems + GroundItemState(nextEntityId++, ItemType.FISH, animal.position, animationPhase = randomPhase())
                animal = animal.copy(
                    behavior = DomesticAnimalBehavior.FISHING,
                    behaviorTimer = 1.25f,
                    specialCooldown = config.catFishCooldownSeconds,
                )
                events += WorldEvent.CatCaughtFish(animal.id, animal.name)
            }

            val wantedCritterType = when (animal.type) {
                DomesticAnimalType.CAT -> AmbientCreatureType.BUTTERFLY
                DomesticAnimalType.DOG -> AmbientCreatureType.BIRD
                else -> null
            }
            val critterBounds = world.bounds.inset(1.1f)
            val existingCritterTarget = wantedCritterType?.let { wantedType ->
                world.ambientCreatures.firstOrNull {
                    it.id == animal.chaseTargetCreatureId && it.type == wantedType
                }
            }
            val targetStillSafe = existingCritterTarget?.takeIf { target ->
                target.position.distanceTo(world.player.position) <= config.petCritterPlayerLimit &&
                    animal.position.distanceTo(world.player.position) <= config.petCritterPlayerLimit &&
                    target.position.distanceTo(animal.position) <= config.petCritterAcquireRadius + 2f &&
                    critterBounds.contains(target.position)
            }
            val canAcquireCritter = wantedCritterType != null &&
                animal.petFollowState != PetFollowState.STAYING &&
                animal.critterChaseCooldown <= 0f &&
                playerDistance <= config.petCritterPlayerLimit - config.petCritterReturnMargin
            val acquiredCritter = if (targetStillSafe == null && canAcquireCritter) {
                world.ambientCreatures.asSequence()
                    .filter { it.type == wantedCritterType }
                    .filter { it.position.distanceTo(animal.position) <= config.petCritterAcquireRadius }
                    .filter {
                        it.position.distanceTo(world.player.position) <=
                            config.petCritterPlayerLimit - config.petCritterReturnMargin
                    }
                    .filter { critterBounds.contains(it.position) }
                    .minByOrNull { it.position.distanceSquaredTo(animal.position) }
            } else null
            val targetCritter = targetStillSafe ?: acquiredCritter
            val abandonedCritterChase =
                (animal.chaseTargetCreatureId != null || animal.behavior == DomesticAnimalBehavior.CHASE_CREATURE) &&
                    targetCritter == null
            if (abandonedCritterChase) {
                animal = animal.copy(
                    chaseTargetCreatureId = null,
                    critterChaseCooldown = 5.0f,
                )
            }

            val nearbyPlaymate = playAssignments[animal.id]?.let { mateId ->
                world.animals.firstOrNull { it.id == mateId && it.type.isPet }
            }
            val playTimeRemaining = if (animal.id in playStartedIds) {
                config.petPlayDurationSeconds.coerceAtLeast(0.1f)
            } else {
                (animal.behaviorTimer - delta).coerceAtLeast(0f)
            }

            var desired = Vector2.ZERO
            var speed = config.animalWalkSpeed
            animal = when {
                animal.type == DomesticAnimalType.DOG && world.fox.phase == FoxPhase.HUNTING &&
                    animal.position.distanceTo(world.fox.position) <= 12f -> {
                    desired = (world.fox.position - animal.position).normalized()
                    speed = config.petCallSpeed
                    animal.copy(behavior = DomesticAnimalBehavior.CHASE_FOX)
                }
                animal.behavior == DomesticAnimalBehavior.FISHING && animal.behaviorTimer > 0f ->
                    animal.copy(behaviorTimer = (animal.behaviorTimer - delta).coerceAtLeast(0f))
                carryingBall -> {
                    desired = if (playerDistance > 1.05f) toPlayer.normalized() else Vector2.ZERO
                    speed = config.animalFollowSpeed * 1.15f
                    animal.copy(behavior = DomesticAnimalBehavior.RETURN_BALL)
                }
                fetchingBall -> {
                    desired = (ball.position - animal.position).normalized()
                    speed = config.animalFollowSpeed * 1.2f
                    animal.copy(behavior = DomesticAnimalBehavior.FETCH_BALL)
                }
                followsCarrot -> {
                    desired = if (playerDistance > 1.1f) toPlayer.normalized() else Vector2.ZERO
                    speed = config.animalFollowSpeed
                    animal.copy(behavior = DomesticAnimalBehavior.FOLLOW_BAIT, behaviorTimer = 0.4f)
                }
                abandonedCritterChase -> {
                    animal.copy(
                        behavior = DomesticAnimalBehavior.IDLE,
                        behaviorTimer = randomBetween(1.2f, 2.0f),
                        desiredDirection = Vector2.ZERO,
                        velocity = Vector2.ZERO,
                        playMateId = null,
                        chaseTargetCreatureId = null,
                        critterChaseCooldown = 5.0f,
                    )
                }
                animal.type.isPet && animal.petFollowState == PetFollowState.CALLED -> {
                    val callTimer = (animal.petCallTimer - delta).coerceAtLeast(0f)
                    desired = if (playerDistance > config.petFollowDistance) toPlayer.normalized() else Vector2.ZERO
                    speed = config.petCallSpeed
                    animal.copy(
                        behavior = DomesticAnimalBehavior.FOLLOW_PLAYER,
                        petCallTimer = callTimer,
                        petFollowState = if (
                            playerDistance <= config.petFollowDistance || callTimer <= 0f
                        ) PetFollowState.FOLLOWING else PetFollowState.CALLED,
                    )
                }
                animal.type.isPet && animal.petFollowState == PetFollowState.FOLLOWING -> {
                    val companionRadius = 3.2f
                    if (playerDistance > companionRadius) {
                        // Pet is outside the companion circle -> navigate toward player until reaching the circle
                        desired = toPlayer.normalized()
                        speed = if (playerDistance > 6.0f) config.animalFollowSpeed * 1.35f else config.animalFollowSpeed
                        animal.copy(
                            behavior = DomesticAnimalBehavior.FOLLOW_PLAYER,
                            chaseTargetCreatureId = null,
                            playMateId = null,
                        )
                    } else if (targetCritter != null) {
                        // Inside circle and a critter (bird/butterfly) is near -> playfully chase
                        val dCritter = animal.position.distanceTo(targetCritter.position)
                        if (dCritter > 0.8f) {
                            desired = (targetCritter.position - animal.position).normalized()
                            speed = if (animal.type == DomesticAnimalType.CAT) config.animalWalkSpeed * 0.95f else config.animalFollowSpeed * 1.05f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.CHASE_CREATURE,
                                desiredDirection = desired,
                                chaseTargetCreatureId = targetCritter.id,
                                playMateId = null,
                            )
                        } else {
                            desired = (targetCritter.position - animal.position).normalized() * 0.35f
                            speed = config.animalWalkSpeed * 0.6f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.CHASE_CREATURE,
                                behaviorTimer = 0.55f,
                                desiredDirection = desired,
                                chaseTargetCreatureId = targetCritter.id,
                                playMateId = null,
                            )
                        }
                    } else if (nearbyPlaymate != null && playTimeRemaining > 0f) {
                        // Inside circle and a playmate pet is near -> play together
                        val dMate = animal.position.distanceTo(nearbyPlaymate.position)
                        if (dMate > 0.95f) {
                            desired = (nearbyPlaymate.position - animal.position).normalized()
                            speed = config.animalWalkSpeed * 1.05f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.PLAY_WITH_PET,
                                behaviorTimer = playTimeRemaining,
                                desiredDirection = desired,
                                playMateId = nearbyPlaymate.id,
                            )
                        } else {
                            val toMate = nearbyPlaymate.position - animal.position
                            desired = Vector2(-toMate.y, toMate.x).normalized()
                            speed = config.animalWalkSpeed * 0.8f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.PLAY_WITH_PET,
                                behaviorTimer = playTimeRemaining,
                                desiredDirection = desired,
                                playMateId = nearbyPlaymate.id,
                            )
                        }
                    } else {
                        // Inside circle -> wander comfortably, explore, or rest near the player
                        val remaining = animal.behaviorTimer - delta
                        if (remaining <= 0f) {
                            if (random.nextFloat() < 0.35f) {
                                desired = Vector2.ZERO
                                animal.copy(
                                    behavior = DomesticAnimalBehavior.IDLE,
                                    behaviorTimer = randomBetween(0.8f, 1.8f),
                                    desiredDirection = Vector2.ZERO,
                                    velocity = Vector2.ZERO,
                                    playMateId = null,
                                )
                            } else {
                                val wanderDir = if (playerDistance > companionRadius * 0.8f) {
                                    (toPlayer.normalized() + randomDirection() * 0.5f).normalized()
                                } else {
                                    randomDirection()
                                }
                                desired = wanderDir
                                speed = config.animalWalkSpeed * 0.85f
                                animal.copy(
                                    behavior = DomesticAnimalBehavior.WANDER,
                                    behaviorTimer = randomBetween(1.0f, 2.5f),
                                    desiredDirection = desired,
                                    playMateId = null,
                                )
                            }
                        } else {
                            desired = if (animal.behavior == DomesticAnimalBehavior.WANDER) {
                                if (playerDistance > companionRadius * 0.85f) toPlayer.normalized() else animal.desiredDirection
                            } else Vector2.ZERO
                            speed = config.animalWalkSpeed * 0.85f
                            animal.copy(behaviorTimer = remaining)
                        }
                    }
                }
                animal.type.isPet && animal.petFollowState == PetFollowState.STAYING -> animal.copy(
                    behavior = DomesticAnimalBehavior.IDLE,
                    velocity = Vector2.ZERO,
                )
                else -> {
                    if (animal.type.isPet && targetCritter != null) {
                        val dCritter = animal.position.distanceTo(targetCritter.position)
                        if (dCritter > 0.8f) {
                            desired = (targetCritter.position - animal.position).normalized()
                            speed = if (animal.type == DomesticAnimalType.CAT) config.animalWalkSpeed * 0.95f else config.animalFollowSpeed * 1.05f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.CHASE_CREATURE,
                                desiredDirection = desired,
                                chaseTargetCreatureId = targetCritter.id,
                                playMateId = null,
                            )
                        } else {
                            desired = (targetCritter.position - animal.position).normalized() * 0.35f
                            speed = config.animalWalkSpeed * 0.6f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.CHASE_CREATURE,
                                behaviorTimer = 0.55f,
                                desiredDirection = desired,
                                chaseTargetCreatureId = targetCritter.id,
                                playMateId = null,
                            )
                        }
                    } else if (animal.type.isPet && nearbyPlaymate != null && playTimeRemaining > 0f) {
                        val dMate = animal.position.distanceTo(nearbyPlaymate.position)
                        if (dMate > 0.95f) {
                            desired = (nearbyPlaymate.position - animal.position).normalized()
                            speed = config.animalWalkSpeed * 1.05f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.PLAY_WITH_PET,
                                behaviorTimer = playTimeRemaining,
                                desiredDirection = desired,
                                playMateId = nearbyPlaymate.id,
                            )
                        } else {
                            val toMate = nearbyPlaymate.position - animal.position
                            desired = Vector2(-toMate.y, toMate.x).normalized()
                            speed = config.animalWalkSpeed * 0.8f
                            animal.copy(
                                behavior = DomesticAnimalBehavior.PLAY_WITH_PET,
                                behaviorTimer = playTimeRemaining,
                                desiredDirection = desired,
                                playMateId = nearbyPlaymate.id,
                            )
                        }
                    } else {
                        val remaining = animal.behaviorTimer - delta
                        if (remaining <= 0f) {
                            if (random.nextFloat() < 0.48f) {
                                desired = Vector2.ZERO
                                animal.copy(
                                    behavior = DomesticAnimalBehavior.IDLE,
                                    behaviorTimer = randomBetween(0.8f, 2.4f),
                                    desiredDirection = Vector2.ZERO,
                                )
                            } else {
                                desired = randomDirection()
                                animal.copy(
                                    behavior = DomesticAnimalBehavior.WANDER,
                                    behaviorTimer = randomBetween(1.1f, 3.8f),
                                    desiredDirection = desired,
                                )
                            }
                        } else {
                            desired = if (animal.behavior == DomesticAnimalBehavior.WANDER) {
                                animal.desiredDirection
                            } else Vector2.ZERO
                            animal.copy(behaviorTimer = remaining)
                        }
                    }
                }
            }

            val attempted = animal.position + desired * (speed * delta)
            val movement = if (desired == Vector2.ZERO) Vector2.ZERO else findAnimalMovement(
                start = animal.position,
                attempted = attempted,
                direction = desired,
                radius = animal.collisionRadius,
                world = world,
            )
            val rawPos = animal.position + movement
            val safeBounds = world.bounds.inset(animal.collisionRadius + 0.12f)
            val clampedPos = Vector2(
                rawPos.x.coerceIn(safeBounds.left, safeBounds.right),
                rawPos.y.coerceIn(safeBounds.top, safeBounds.bottom),
            )
            val hitEdge = rawPos != clampedPos || (desired != Vector2.ZERO && movement.lengthSquared() < 0.0001f)
            val shouldAbandonChaseAtEdge = hitEdge && animal.type.isPet && animal.behavior == DomesticAnimalBehavior.CHASE_CREATURE

            animal = animal.copy(
                position = clampedPos,
                velocity = if (shouldAbandonChaseAtEdge || delta <= 0f) Vector2.ZERO else (clampedPos - animal.position) / delta,
                facing = if (movement.lengthSquared() > 0.00001f) {
                    facingFor(movement, animal.facing)
                } else animal.facing,
                behavior = if (shouldAbandonChaseAtEdge) DomesticAnimalBehavior.IDLE else animal.behavior,
                behaviorTimer = if (shouldAbandonChaseAtEdge) randomBetween(1.2f, 2.2f) else animal.behaviorTimer,
                chaseTargetCreatureId = if (shouldAbandonChaseAtEdge) null else animal.chaseTargetCreatureId,
                critterChaseCooldown = if (shouldAbandonChaseAtEdge) 5.0f else animal.critterChaseCooldown,
            )

            if (fetchingBall && animal.position.distanceTo(ball.position) <= animal.collisionRadius + 0.32f) {
                ball = ball.copy(
                    phase = BallPhase.WITH_PET,
                    position = animal.position,
                    velocity = Vector2.ZERO,
                    holderAnimalId = animal.id,
                    targetPetId = animal.id,
                )
                events += WorldEvent.PetFetchedBall(animal.id)
                events.sound(SoundCue.BALL_FETCH, animal.position, 0.75f)
            } else if (carryingBall && playerDistance <= 1.05f) {
                ball = ball.copy(
                    phase = BallPhase.WITH_PLAYER,
                    position = world.player.position,
                    velocity = Vector2.ZERO,
                    holderAnimalId = null,
                    targetPetId = null,
                )
                events += WorldEvent.BallReturned(animal.id)
            }
            updated += animal
        }

        val holder = ball.holderAnimalId?.let { id -> updated.firstOrNull { it.id == id } }
        if (ball.phase == BallPhase.WITH_PET && holder != null) ball = ball.copy(position = holder.position)
        if (ball.phase == BallPhase.WITH_PET && holder == null) {
            ball = ball.copy(phase = BallPhase.ON_GROUND, holderAnimalId = null, targetPetId = null)
        }
        return world.copy(animals = updated, ball = ball, groundItems = groundItems, nextEntityId = nextEntityId)
    }

    private fun updateAmbientCreatures(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val updated = buildList {
            world.ambientCreatures.forEach { original ->
                val life = original.lifeTimer - delta
                if (life <= 0f) return@forEach

                var movementTimer = original.movementTimer - delta
                var velocity = original.velocity
                val scaringPet = world.animals.firstOrNull { pet ->
                    pet.type.isPet && pet.position.distanceTo(original.position) <= 1.2f
                }
                if (scaringPet != null) {
                    val fleeDir = (original.position - scaringPet.position).normalized()
                    val fleeSpeed = if (original.type == AmbientCreatureType.BIRD) 3.6f else 1.4f
                    velocity = fleeDir * fleeSpeed
                    movementTimer = randomBetween(1.4f, 2.6f)
                } else if (movementTimer <= 0f || velocity == Vector2.ZERO) {
                    val speed = if (original.type == AmbientCreatureType.BIRD) {
                        randomBetween(1.35f, 2.15f)
                    } else {
                        randomBetween(0.38f, 0.78f)
                    }
                    velocity = randomDirection() * speed
                    movementTimer = if (original.type == AmbientCreatureType.BIRD) {
                        randomBetween(1.8f, 4.2f)
                    } else {
                        randomBetween(0.45f, 1.25f)
                    }
                }

                var position = original.position + velocity * delta
                val safeBounds = world.bounds.inset(0.55f)
                if (!safeBounds.contains(position)) {
                    val inward = (safeBounds.center - original.position).normalized()
                    val speed = velocity.length().coerceAtLeast(0.4f)
                    velocity = inward * speed
                    position = (original.position + velocity * delta).coerceIn(safeBounds)
                    movementTimer = randomBetween(1.2f, 2.8f)
                }

                var soundCooldown = original.soundCooldown - delta
                val distance = position.distanceTo(world.player.position)
                if (soundCooldown <= 0f) {
                    if (distance <= config.ambientSoundRadius) {
                        val volume = (1f - distance / config.ambientSoundRadius).coerceIn(0.12f, 0.62f)
                        val cue = if (original.type == AmbientCreatureType.BIRD) {
                            SoundCue.BIRD_CHIRP
                        } else {
                            SoundCue.BUTTERFLY_FLUTTER
                        }
                        events.sound(cue, position, volume)
                        soundCooldown = if (original.type == AmbientCreatureType.BIRD) {
                            randomBetween(4.5f, 9.5f)
                        } else {
                            randomBetween(7f, 13f)
                        }
                    } else {
                        // Recheck soon after it approaches, without polling every frame.
                        soundCooldown = randomBetween(1.3f, 2.5f)
                    }
                }
                add(
                    original.copy(
                        position = position,
                        velocity = velocity,
                        facing = facingFor(velocity, original.facing),
                        animationTime = original.animationTime + delta,
                        lifeTimer = life,
                        movementTimer = movementTimer,
                        soundCooldown = soundCooldown,
                    ),
                )
            }
        }.toMutableList()

        var nextId = world.nextEntityId
        var spawnTimer = world.ambientCreatureSpawnTimer - delta
        if (spawnTimer <= 0f) {
            if (updated.size < config.maxAmbientCreatures.coerceAtLeast(0)) {
                ambientSpawnPosition(world)?.let { position ->
                    val type = if (random.nextFloat() < 0.58f) {
                        AmbientCreatureType.BUTTERFLY
                    } else {
                        AmbientCreatureType.BIRD
                    }
                    val speed = if (type == AmbientCreatureType.BIRD) 1.7f else 0.58f
                    val velocity = randomDirection() * speed
                    updated += AmbientCreatureState(
                        id = nextId++,
                        type = type,
                        position = position,
                        velocity = velocity,
                        facing = facingFor(velocity, Facing.RIGHT),
                        animationTime = randomPhase(),
                        lifeTimer = if (type == AmbientCreatureType.BIRD) {
                            randomBetween(15f, 28f)
                        } else {
                            randomBetween(19f, 36f)
                        },
                        movementTimer = randomBetween(0.6f, 2.4f),
                        soundCooldown = randomBetween(2.5f, 7f),
                    )
                }
            }
            spawnTimer = randomBetween(config.ambientSpawnMinSeconds, config.ambientSpawnMaxSeconds)
                .coerceAtLeast(0.5f)
        }
        return world.copy(
            ambientCreatures = updated,
            ambientCreatureSpawnTimer = spawnTimer,
            nextEntityId = nextId,
        )
    }

    private fun ambientSpawnPosition(world: FarmWorldState): Vector2? {
        repeat(14) {
            val radius = randomBetween(3.5f, 10.5f)
            val candidate = (world.player.position + randomDirection() * radius)
                .coerceIn(world.bounds.inset(0.8f))
            if (world.barn.collider.distanceTo(candidate) > 0.8f) return candidate
        }
        return null
    }

    private fun advanceBall(ball: BallState, world: FarmWorldState, delta: Float): BallState {
        val animated = ball.copy(animationTime = ball.animationTime + delta)
        return when (animated.phase) {
            BallPhase.WITH_PLAYER -> animated.copy(position = world.player.position, velocity = Vector2.ZERO)
            BallPhase.ON_GROUND, BallPhase.WITH_PET -> animated
            BallPhase.FLYING -> {
                val timer = (animated.flightTimer - delta).coerceAtLeast(0f)
                val candidate = animated.position + animated.velocity * delta
                val canMove = canOccupy(candidate, 0.16f, world)
                if (timer <= 0f || !canMove) {
                    animated.copy(
                        phase = BallPhase.ON_GROUND,
                        velocity = Vector2.ZERO,
                        flightTimer = 0f,
                    )
                } else {
                    animated.copy(
                        position = candidate,
                        velocity = animated.velocity * 0.94f,
                        flightTimer = timer,
                    )
                }
            }
        }
    }

    private fun findAnimalMovement(
        start: Vector2,
        attempted: Vector2,
        direction: Vector2,
        radius: Float,
        world: FarmWorldState,
    ): Vector2 {
        val enteringFromSouth = start.y > 42.7f && attempted.y < start.y
        if (!enteringFromSouth && (attempted.y !in 4.3f..42.7f || attempted.x !in 4.3f..59.7f)) return Vector2.ZERO
        if (canOccupy(attempted, radius, world)) return attempted - start
        val distance = start.distanceTo(attempted)
        AVOIDANCE_ANGLES.forEach { angle ->
            val candidate = start + rotate(direction, angle) * distance
            val validArea = enteringFromSouth || (candidate.y in 4.3f..42.7f && candidate.x in 4.3f..59.7f)
            if (validArea && canOccupy(candidate, radius, world)) return candidate - start
        }
        return Vector2.ZERO
    }

    private fun updateFox(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var fox = world.fox.copy(animationTime = world.fox.animationTime + delta)
        var player = world.player
        var bicycle = world.bicycle
        var chickens = world.chickens
        var animals = world.animals
        when (fox.phase) {
            FoxPhase.ABSENT, FoxPhase.DEAD -> {
                val timer = fox.phaseTimer - delta
                if (timer > 0f) return world.copy(fox = fox.copy(phaseTimer = timer))
                val spawn = findFoxSpawnPosition(world)
                    ?: return world.copy(fox = fox.copy(phaseTimer = 8f))
                fox = fox.copy(
                    phase = FoxPhase.HUNTING,
                    position = spawn,
                    velocity = Vector2.ZERO,
                    health = config.foxMaxHealth.coerceAtLeast(1),
                    targetChickenId = null,
                    phaseTimer = 0f,
                    biteCooldown = 0f,
                    huntingTimer = 0f,
                )
                events += WorldEvent.FoxAppeared(fox.id, spawn)
                events.sound(SoundCue.FOX_ALERT, spawn)
            }
            FoxPhase.HUNTING -> {
                val newHuntTimer = fox.huntingTimer + delta
                if (newHuntTimer >= 120f) {
                    fox = fox.copy(phase = FoxPhase.FLEEING, phaseTimer = config.foxFleeSeconds, targetChickenId = null, huntingTimer = 0f)
                    events += WorldEvent.FoxScaredByDog(-1)
                    return world.copy(fox = fox)
                }
                fox = fox.copy(huntingTimer = newHuntTimer)
                val guardDog = animals.firstOrNull {
                    it.type == DomesticAnimalType.DOG && it.handling == AnimalHandlingState.FREE &&
                        it.position.distanceTo(fox.position) <= config.dogFoxScareRadius
                }
                if (guardDog != null) {
                    fox = fox.copy(phase = FoxPhase.FLEEING, phaseTimer = config.foxFleeSeconds, targetChickenId = null)
                    events += WorldEvent.FoxScaredByDog(guardDog.id)
                    return world.copy(fox = fox, animals = animals)
                }
                fox = fox.copy(biteCooldown = (fox.biteCooldown - delta).coerceAtLeast(0f))
                val eligibleChickens = chickens.filter { it.handling == AnimalHandlingState.FREE }
                val target = eligibleChickens.firstOrNull { it.id == fox.targetChickenId }
                    ?: eligibleChickens.minByOrNull { it.position.distanceSquaredTo(fox.position) }

                val distToPlayer = fox.position.distanceTo(player.position)
                val canTargetPlayer = player.faintTimer <= 0f && distToPlayer <= 8.5f
                var foxSpeed = config.foxSpeed

                val direction = when {
                    canTargetPlayer && (target == null || distToPlayer < fox.position.distanceTo(target.position) * 0.9f || distToPlayer <= 3.8f) -> {
                        (player.position - fox.position).normalized()
                    }
                    target != null -> {
                        (target.position - fox.position).normalized()
                    }
                    else -> {
                        foxSpeed = config.foxSpeed * 0.62f
                        if (fox.velocity.lengthSquared() > 0.01f && random.nextFloat() < 0.94f) {
                            fox.velocity.normalized()
                        } else {
                            val towardsCenter = (world.bounds.center - fox.position).normalized()
                            (towardsCenter + randomDirection() * 0.75f).normalized()
                        }
                    }
                }
                val attempted = fox.position + direction * (foxSpeed * delta)
                val movement = if (direction == Vector2.ZERO) Vector2.ZERO else findAnimalMovement(
                    start = fox.position,
                    attempted = attempted,
                    direction = direction,
                    radius = 0.43f,
                    world = world,
                )
                fox = fox.copy(
                    position = fox.position + movement,
                    velocity = if (delta > 0f) movement / delta else Vector2.ZERO,
                    facing = if (movement.lengthSquared() > 0.00001f) {
                        facingFor(movement, fox.facing)
                    } else fox.facing,
                    targetChickenId = target?.id,
                )

                val canBitePlayer =
                    player.faintTimer <= 0f &&
                        fox.biteCooldown <= 0f &&
                        fox.position.distanceTo(player.position) <= config.foxBiteRange
                if (canBitePlayer) {
                    val bites = player.foxBites + 1
                    player = player.copy(foxBites = bites, isMoving = false)
                    fox = fox.copy(biteCooldown = config.foxBiteCooldownSeconds.coerceAtLeast(0f))
                    events += WorldEvent.PlayerBitten(bites)
                    events.sound(SoundCue.FOX_BITE, player.position)
                    if (bites >= config.playerBitesBeforeFaint.coerceAtLeast(1)) {
                        val bicycleWasMounted = player.isMounted || bicycle.isMounted
                        player.carriedAnimal?.let { carried ->
                            if (carried.kind == AnimalKind.CHICKEN) {
                                chickens = chickens.map {
                                    if (it.id == carried.id) it.copy(
                                        position = player.position,
                                        handling = AnimalHandlingState.FREE,
                                        velocity = Vector2.ZERO,
                                    ) else it
                                }
                            } else {
                                animals = animals.map {
                                    if (it.id == carried.id) it.copy(
                                        position = player.position,
                                        handling = AnimalHandlingState.FREE,
                                        velocity = Vector2.ZERO,
                                    ) else it
                                }
                            }
                        }
                        player = player.copy(
                            faintTimer = config.playerFaintSeconds.coerceAtLeast(0.1f),
                            isMounted = false,
                            carriedAnimal = null,
                            actionAnimation = null,
                        )
                        // Never teleport a parked bicycle onto the fainted player. That was the
                        // source of the bicycle appearing over an otherwise-correct faint pose.
                        bicycle = if (bicycleWasMounted) {
                            bicycle.copy(
                                position = player.position - facingVector(player.facing) * 0.85f,
                                isMounted = false,
                            )
                        } else {
                            bicycle.copy(isMounted = false)
                        }
                        fox = fox.copy(
                            phase = FoxPhase.FLEEING,
                            phaseTimer = config.foxFleeSeconds,
                            targetChickenId = null,
                        )
                        events += WorldEvent.PlayerFainted
                    }
                } else if (
                    target != null &&
                    fox.position.distanceTo(target.position) <= target.collisionRadius + 0.42f
                ) {
                    chickens = chickens.filterNot { it.id == target.id }
                    fox = fox.copy(
                        phase = FoxPhase.FLEEING,
                        phaseTimer = config.foxFleeSeconds,
                        targetChickenId = null,
                    )
                    events += WorldEvent.ChickenTakenByFox(target.id, target.name)
                }
            }
            FoxPhase.FLEEING -> {
                val timer = fox.phaseTimer - delta
                val away = (fox.position - world.bounds.center).normalized().let {
                    if (it == Vector2.ZERO) Vector2.RIGHT else it
                }
                fox = fox.copy(
                    position = fox.position + away * (config.foxFleeSpeed * delta),
                    velocity = away * config.foxFleeSpeed,
                    facing = facingFor(away, fox.facing),
                    phaseTimer = timer,
                )
                if (timer <= 0f) {
                    fox = fox.copy(
                        phase = FoxPhase.ABSENT,
                        position = Vector2(-3f, -3f),
                        velocity = Vector2.ZERO,
                        phaseTimer = randomFoxReturnDelay(),
                    )
                }
            }
        }
        return world.copy(
            fox = fox,
            player = player,
            bicycle = bicycle,
            chickens = chickens,
            animals = animals,
        )
    }

    private fun chooseChickenBehavior(chicken: ChickenState): ChickenState {
        val roll = random.nextFloat()
        return when {
            roll < 0.22f -> chicken.copy(
                behavior = ChickenBehavior.IDLE,
                behaviorTimer = randomBetween(0.7f, 2.1f),
                desiredDirection = Vector2.ZERO,
            )
            roll < 0.48f -> chicken.copy(
                behavior = ChickenBehavior.PECK,
                behaviorTimer = randomBetween(0.8f, 2.4f),
                desiredDirection = Vector2.ZERO,
            )
            else -> chicken.copy(
                behavior = ChickenBehavior.WANDER,
                behaviorTimer = randomBetween(1.2f, 4f),
                desiredDirection = randomDirection(),
            )
        }
    }

    private fun chickenSeparation(chicken: ChickenState, others: List<ChickenState>): Vector2 {
        var separation = Vector2.ZERO
        others.forEach { other ->
            if (other.id == chicken.id) return@forEach
            val difference = chicken.position - other.position
            val distanceSquared = difference.lengthSquared()
            if (distanceSquared in 0.0001f..1.44f) {
                separation += difference.normalized() * ((1.44f - distanceSquared) / 1.44f)
            }
        }
        return separation * 0.8f
    }

    private fun findChickenMovement(
        start: Vector2,
        attempted: Vector2,
        direction: Vector2,
        radius: Float,
        world: FarmWorldState,
    ): Vector2 {
        if (attempted.y !in 4.3f..42.7f || attempted.x !in 4.3f..59.7f) return Vector2.ZERO
        if (canOccupy(attempted, radius, world)) return attempted - start
        val distance = start.distanceTo(attempted)
        val angles = if (random.nextBoolean()) AVOIDANCE_ANGLES else AVOIDANCE_ANGLES_REVERSED
        angles.forEach { angle ->
            val candidateDirection = rotate(direction, angle)
            val candidate = start + candidateDirection * distance
            if (candidate.y in 4.3f..42.7f && candidate.x in 4.3f..59.7f && canOccupy(candidate, radius, world)) {
                return candidate - start
            }
        }
        return Vector2.ZERO
    }

    private fun updateTruck(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var truck = world.truck
        var barn = world.barn
        var player = world.player
        when (truck.phase) {
            TruckPhase.ABSENT -> {
                val timer = truck.phaseTimer - delta
                truck = if (timer <= 0f) {
                    events.sound(SoundCue.TRUCK_ARRIVE, TRUCK_PARKED_POSITION)
                    events.sound(SoundCue.TRUCK_ENGINE, TRUCK_PARKED_POSITION, 0.7f)
                    truck.copy(
                        phase = TruckPhase.ARRIVING,
                        position = TRUCK_START_POSITION,
                        phaseTimer = config.truckArrivalSeconds,
                        eggsLoaded = 0,
                        saleCompletedThisVisit = false,
                        lastSaleCoins = 0,
                    )
                } else truck.copy(phaseTimer = timer)
            }
            TruckPhase.ARRIVING -> {
                val timer = (truck.phaseTimer - delta).coerceAtLeast(0f)
                val progress = 1f - timer / config.truckArrivalSeconds
                truck = truck.copy(
                    position = lerp(TRUCK_START_POSITION, TRUCK_PARKED_POSITION, progress),
                    phaseTimer = timer,
                )
                if (timer <= 0f) truck = truck.copy(phase = TruckPhase.PARKED, phaseTimer = config.truckParkedSeconds)
            }
            TruckPhase.PARKED -> {
                var additionalCoins = 0
                var itemsSold = 0

                if (!truck.saleCompletedThisVisit && barn.storedEggs > 0) {
                    val sold = barn.storedEggs
                    val coins = sold * config.eggSalePrice
                    itemsSold += sold
                    additionalCoins += coins
                    barn = barn.copy(storedEggs = 0)
                }

                if (additionalCoins > 0) {
                    player = player.copy(inventory = player.inventory.addCoins(additionalCoins))
                    truck = truck.copy(
                        eggsLoaded = truck.eggsLoaded + itemsSold,
                        saleCompletedThisVisit = true,
                        lastSaleCoins = truck.lastSaleCoins + additionalCoins,
                    )
                    events += WorldEvent.TruckSale(itemsSold, additionalCoins)
                    events.sound(SoundCue.COINS, truck.position)
                }
                val courierNeedsTruck = world.deliveries.any {
                    it.courierPhase != CourierPhase.WAITING || it.secondsRemaining <= 0f
                }
                val timer = if (courierNeedsTruck) maxOf(truck.phaseTimer, 2f) else truck.phaseTimer - delta
                truck = if (timer <= 0f) {
                    events.sound(SoundCue.TRUCK_ENGINE, truck.position, 0.7f)
                    truck.copy(phase = TruckPhase.LEAVING, phaseTimer = config.truckLeaveSeconds)
                } else truck.copy(phaseTimer = timer)
            }
            TruckPhase.LEAVING -> {
                val timer = (truck.phaseTimer - delta).coerceAtLeast(0f)
                val progress = 1f - timer / config.truckLeaveSeconds
                truck = truck.copy(
                    position = lerp(TRUCK_PARKED_POSITION, TRUCK_START_POSITION, progress),
                    phaseTimer = timer,
                )
                if (timer <= 0f) {
                    truck = truck.copy(
                        phase = TruckPhase.ABSENT,
                        position = TRUCK_START_POSITION,
                        phaseTimer = randomBetween(config.truckMinReturnSeconds, config.truckMaxReturnSeconds),
                    )
                }
            }
        }
        return world.copy(truck = truck, barn = barn, player = player)
    }

    private data class FishingUpdate(val world: FarmWorldState, val consumedAction: Boolean)

    private fun updateFishing(
        world: FarmWorldState,
        delta: Float,
        input: WorldInput,
        events: MutableList<WorldEvent>,
    ): FishingUpdate {
        var fishing = world.fishing
        var player = world.player
        var consumedAction = false

        when (fishing.phase) {
            FishingPhase.NONE -> Unit
            FishingPhase.CASTING -> {
                val timer = fishing.phaseTimer - delta
                fishing = if (timer <= 0f) {
                    events.sound(SoundCue.BOBBER_SPLASH, fishing.bobberPosition)
                    events += WorldEvent.FishingChanged(FishingPhase.WAITING_FOR_BITE)
                    fishing.copy(phase = FishingPhase.WAITING_FOR_BITE, phaseTimer = fishing.biteDelay)
                } else fishing.copy(phaseTimer = timer)
            }
            FishingPhase.WAITING_FOR_BITE -> {
                val timer = fishing.phaseTimer - delta
                fishing = if (timer <= 0f) {
                    events.sound(SoundCue.FISH_BITE, fishing.bobberPosition)
                    events += WorldEvent.FishingChanged(FishingPhase.BITE)
                    fishing.copy(phase = FishingPhase.BITE, phaseTimer = 0.85f)
                } else fishing.copy(phaseTimer = timer)
            }
            FishingPhase.BITE -> {
                if (input.actionPressed) {
                    consumedAction = true
                    events.sound(SoundCue.FISHING_REEL, world.player.position)
                    events += WorldEvent.FishingChanged(FishingPhase.REELING)
                    player = player.startAction(
                        tool = ToolType.FISHING_ROD,
                        target = fishing.bobberPosition,
                        duration = 0.46f,
                    )
                    fishing = fishing.copy(
                        phase = FishingPhase.REELING,
                        phaseTimer = 0f,
                        catchProgress = 0.3f,
                        fishPosition = 0.5f,
                        fishVelocity = 0f,
                        catchBarPosition = 0.25f,
                        catchBarVelocity = 0f,
                    )
                } else {
                    val timer = fishing.phaseTimer - delta
                    fishing = if (timer <= 0f) failFishing(fishing, world.player.position, events)
                    else fishing.copy(phaseTimer = timer)
                }
            }
            FishingPhase.REELING -> {
                val fishAcceleration = sin(world.animation.elapsedSeconds * 4.1f + (fishing.waterBodyId ?: 0)) * 1.35f
                var fishVelocity = (fishing.fishVelocity + fishAcceleration * delta).coerceIn(-0.72f, 0.72f)
                var fishPosition = fishing.fishPosition + fishVelocity * delta
                if (fishPosition < 0.04f || fishPosition > 0.96f) {
                    fishPosition = fishPosition.coerceIn(0.04f, 0.96f)
                    fishVelocity *= -0.72f
                }

                val barAcceleration = if (input.actionHeld) 2.05f else -1.65f
                var barVelocity = (fishing.catchBarVelocity + barAcceleration * delta).coerceIn(-1.05f, 1.05f)
                var barPosition = fishing.catchBarPosition + barVelocity * delta
                if (barPosition < 0.1f || barPosition > 0.9f) {
                    barPosition = barPosition.coerceIn(0.1f, 0.9f)
                    barVelocity *= -0.28f
                }

                val overlapping = abs(fishPosition - barPosition) <= 0.155f
                val progressDelta = if (overlapping) delta * 0.23f else -delta * 0.105f
                val progress = (fishing.catchProgress + progressDelta).coerceIn(0f, 1f)
                fishing = fishing.copy(
                    fishPosition = fishPosition,
                    fishVelocity = fishVelocity,
                    catchBarPosition = barPosition,
                    catchBarVelocity = barVelocity,
                    catchProgress = progress,
                )
                when {
                    progress >= 1f -> {
                        player = player.copy(inventory = player.inventory.add(ItemType.FISH))
                        events.sound(SoundCue.FISH_CAUGHT, player.position)
                        events += WorldEvent.ItemCollected(ItemType.FISH, 1)
                        events += WorldEvent.FishingChanged(FishingPhase.SUCCESS)
                        fishing = fishing.copy(phase = FishingPhase.SUCCESS, phaseTimer = 1.05f)
                    }
                    progress <= 0f -> fishing = failFishing(fishing, player.position, events)
                }
            }
            FishingPhase.SUCCESS, FishingPhase.FAILED -> {
                val timer = fishing.phaseTimer - delta
                fishing = if (timer <= 0f) FishingState() else fishing.copy(phaseTimer = timer)
            }
        }
        return FishingUpdate(world.copy(fishing = fishing, player = player), consumedAction)
    }

    private fun failFishing(
        fishing: FishingState,
        position: Vector2,
        events: MutableList<WorldEvent>,
    ): FishingState {
        events.sound(SoundCue.FISH_ESCAPED, position)
        events += WorldEvent.FishingChanged(FishingPhase.FAILED)
        return fishing.copy(phase = FishingPhase.FAILED, phaseTimer = 0.9f)
    }

    private fun performToolAction(
        world: FarmWorldState,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        if (world.player.actionCooldown > 0f) return world
        val tool = world.player.selectedTool
        val currentDurability = world.player.toolDurability[tool]
        if (currentDurability != null && currentDurability <= 0) {
            events.sound(SoundCue.COLLISION, world.player.position, 0.45f)
            return world
        }
        val result = when (tool) {
            ToolType.HANDS -> {
                if (world.player.activeItemId == ItemType.MILK_PAIL && world.player.inventory.count(ItemType.MILK_PAIL) > 0) {
                    val nearbyCow = world.animals.firstOrNull {
                        it.type == DomesticAnimalType.COW &&
                            it.handling == AnimalHandlingState.FREE &&
                            it.lifeStage == AnimalLifeStage.ADULT &&
                            it.milkReadyInSeconds <= 0f &&
                            it.position.distanceTo(world.player.position) <= config.interactionRange + it.collisionRadius
                    }
                    if (nearbyCow != null) useMilkPail(world, events) else useHands(world, events)
                } else if (world.player.activeItemId in setOf(ItemType.SEED, ItemType.WHEAT_SEED)) {
                    val emptyPlot = nearestPlot(world) { it.soil == SoilState.TILLED && it.crop == null }
                    if (emptyPlot != null && emptyPlot.position.distanceTo(world.player.position) <= config.interactionRange + 0.5f) {
                        useSeedBag(world, events)
                    } else {
                        useHands(world, events)
                    }
                } else {
                    useHands(world, events)
                }
            }
            ToolType.AXE -> useAxe(world, events)
            ToolType.PICKAXE -> usePickaxe(world, events)
            ToolType.MACHETE -> useMachete(world, events)
            ToolType.PET_BALL -> usePetBall(world, events)
            ToolType.CARROT_BAIT -> world
            ToolType.FENCE -> useFence(world)
            ToolType.GATE -> useGate(world)
            ToolType.HOE -> useHoe(world, events)
            ToolType.WATERING_CAN -> useWateringCan(world, events)
            ToolType.SEED_BAG -> useSeedBag(world, events)
            ToolType.PATH_TOOL -> usePathTool(world, events)
            ToolType.FISHING_ROD -> useFishingRod(world, events)
            ToolType.MILK_PAIL -> useMilkPail(world, events)
            ToolType.ARCHITECT_PENCIL -> useArchitectPencil(world, events)
            ToolType.BLUEPRINT_DAIRY,
            ToolType.BLUEPRINT_SLAUGHTERHOUSE,
            ToolType.BLUEPRINT_EGG_PACKER,
            ToolType.BLUEPRINT_VEGGIE_PACKER,
            ToolType.BLUEPRINT_BAKERY,
            ToolType.BLUEPRINT_FISH_PROCESSOR -> useBlueprint(world, tool, events)
        }
        if (currentDurability != null && result.player.actionAnimation != null && result.player.actionCooldown > 0f) {
            val (playerWithUsedTool, broken) = result.player.useTool(tool)
            if (broken) {
                events += WorldEvent.ToolBroken(tool)
                events.sound(SoundCue.ROCK_BREAK, result.player.position, 0.65f)
            }
            return result.copy(player = playerWithUsedTool)
        }
        return result
    }

    private fun useHands(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val playerPosition = world.player.position

        // The player can meet the courier anywhere along the route and receive the parcel early.
        val courierIndex = world.deliveries.indexOfFirst { order ->
            order.courierPhase == CourierPhase.TO_BARN &&
                order.courierPosition.distanceTo(playerPosition) <= config.interactionRange
        }
        if (courierIndex >= 0) {
            val order = world.deliveries[courierIndex]
            val deliveredWorld = fulfillDelivery(world, order, events)
            if (deliveredWorld != null) {
                val route = courierRoute(world)
                val returnIndex = when {
                    order.courierRouteIndex < 0 -> 1
                    else -> (order.courierRouteIndex + 1).coerceAtMost(route.lastIndex)
                }
                val returning = order.copy(
                    delivered = true,
                    courierPhase = CourierPhase.RETURNING,
                    courierRouteIndex = returnIndex,
                )
                events += WorldEvent.OrderDelivered(order.id, order.kind)
                events += WorldEvent.OrderIntercepted(order.id, order.kind)
                return deliveredWorld.copy(
                    deliveries = world.deliveries.replaceAt(courierIndex) { returning },
                    player = deliveredWorld.player.startAction(
                        ToolType.HANDS,
                        order.courierPosition,
                        0.28f,
                    ),
                )
            }
        }

        // Hands are the universal interaction: approach the bicycle and tap the action button.
        if (world.player.isMounted || (
                world.bicycle.owned &&
                    world.player.position.distanceTo(world.bicycle.position) <= config.bicycleMountRange
            )
        ) return toggleBicycle(world, events)

        // Cow milking if carrying empty pail
        if (world.player.inventory.count(ItemType.MILK_PAIL) > 0) {
            val nearbyMilkingCow = world.animals.firstOrNull {
                it.type == DomesticAnimalType.COW &&
                    it.handling == AnimalHandlingState.FREE &&
                    it.lifeStage == AnimalLifeStage.ADULT &&
                    it.milkReadyInSeconds <= 0f &&
                    it.position.distanceTo(playerPosition) <= config.interactionRange + it.collisionRadius
            }
            if (nearbyMilkingCow != null) return useMilkPail(world, events)
        }

        // Planting seeds if holding seeds near an empty tilled plot
        val hasSeeds = world.player.inventory.count(ItemType.SEED) > 0 || world.player.inventory.count(ItemType.WHEAT_SEED) > 0
        if (hasSeeds && (world.player.activeItemId in setOf(ItemType.SEED, ItemType.WHEAT_SEED))) {
            val emptyPlot = nearestPlot(world) { it.soil == SoilState.TILLED && it.crop == null }
            if (emptyPlot != null && emptyPlot.position.distanceTo(playerPosition) <= config.interactionRange + 0.5f) {
                return useSeedBag(world, events)
            }
        }

        // Weed harvesting: pulling weeds by hand
        val nearbyTuft = world.grassTufts.firstOrNull { it.position.distanceTo(playerPosition) <= config.interactionRange + 0.35f }
        if (nearbyTuft != null) {
            val seedItem = if (random.nextFloat() < 0.35f) ItemType.WHEAT_SEED else ItemType.SEED
            events.sound(SoundCue.FOOTSTEP_GRASS, nearbyTuft.position)
            events += WorldEvent.WeedHarvested(nearbyTuft.id, seedItem)
            events += WorldEvent.ItemCollected(seedItem, 1)
            return world.copy(
                grassTufts = world.grassTufts.filterNot { it.id == nearbyTuft.id },
                player = world.player.startAction(ToolType.HANDS, nearbyTuft.position, 0.25f).copy(
                    inventory = world.player.inventory.add(seedItem, 1),
                ),
            )
        }

        // Gate interaction: toggle open/close
        val nearbyGate = world.gates.firstOrNull { it.position.distanceTo(playerPosition) <= config.interactionRange + 0.6f }
        if (nearbyGate != null) {
            events.sound(SoundCue.TOOL_SELECT, nearbyGate.position)
            events += WorldEvent.GateToggled(nearbyGate.id, !nearbyGate.isOpen)
            val updatedGates = world.gates.map {
                if (it.id == nearbyGate.id) it.copy(isOpen = !it.isOpen) else it
            }
            return world.copy(
                gates = updatedGates,
                player = world.player.startAction(ToolType.HANDS, nearbyGate.position, 0.2f),
            )
        }

        // Wheelbarrow interaction
        if (world.player.position.distanceTo(world.wheelbarrow.position) <= config.interactionRange + 0.5f) {
            return toggleWheelbarrow(world, events)
        }

        // Factory interactions
        val nearbyFactory = world.factories.firstOrNull {
            it.collider.distanceTo(playerPosition) <= config.interactionRange + 0.6f
        }
        if (nearbyFactory != null) {
            if (nearbyFactory.phase == ConstructionPhase.SCAFFOLD) {
                return contributeToScaffold(world, nearbyFactory, events)
            }
            when (nearbyFactory.type) {
                FactoryType.SLAUGHTERHOUSE -> {
                    val carried = world.player.carriedAnimal
                    if (carried != null && (carried.kind == AnimalKind.PIG || carried.kind == AnimalKind.COW) && !nearbyFactory.hasPigInChamber && !nearbyFactory.hasCowInChamber) {
                        val isCow = carried.kind == AnimalKind.COW
                        events.sound(SoundCue.ANIMAL_DROP, nearbyFactory.position)
                        val remainingAnimals = world.animals.filterNot { it.id == carried.id }
                        val updatedFactories = world.factories.map {
                            if (it.id == nearbyFactory.id) it.copy(hasPigInChamber = !isCow, hasCowInChamber = isCow) else it
                        }
                        return world.copy(
                            animals = remainingAnimals,
                            factories = updatedFactories,
                            player = world.player.copy(carriedAnimal = null),
                        )
                    }
                    if (carried == null && (nearbyFactory.hasPigInChamber || nearbyFactory.hasCowInChamber)) {
                        val isCow = nearbyFactory.hasCowInChamber
                        val animalType = if (isCow) DomesticAnimalType.COW else DomesticAnimalType.PIG
                        val newAnimal = DomesticAnimalState(
                            id = world.nextEntityId,
                            name = if (isCow) "Vaca" else "Cerdito",
                            type = animalType,
                            position = nearbyFactory.doorPosition,
                            lifeStage = AnimalLifeStage.ADULT,
                        )
                        events.sound(SoundCue.ANIMAL_PICK_UP, nearbyFactory.position)
                        val updatedFactories = world.factories.map {
                            if (it.id == nearbyFactory.id) it.copy(hasPigInChamber = false, hasCowInChamber = false) else it
                        }
                        return world.copy(
                            animals = world.animals + newAnimal,
                            factories = updatedFactories,
                            nextEntityId = world.nextEntityId + 1,
                            player = world.player.copy(carriedAnimal = newAnimal.reference),
                        )
                    }
                }
                FactoryType.DAIRY -> {
                    if (nearbyFactory.storedOutputs > 0) {
                        val collected = nearbyFactory.storedOutputs
                        events.sound(SoundCue.COINS, nearbyFactory.position)
                        events += WorldEvent.ItemCollected(ItemType.CHEESE, collected)
                        val updatedFactories = world.factories.map {
                            if (it.id == nearbyFactory.id) it.copy(storedOutputs = 0) else it
                        }
                        return world.copy(
                            factories = updatedFactories,
                            player = world.player.startAction(ToolType.HANDS, nearbyFactory.position, 0.3f).copy(
                                inventory = world.player.inventory.add(ItemType.CHEESE, collected),
                            ),
                        )
                    }
                    val milkInInventory = world.player.inventory.count(ItemType.MILK)
                    if (milkInInventory > 0) {
                        events.sound(SoundCue.COW_MILK, nearbyFactory.position)
                        val updatedFactories = world.factories.map {
                            if (it.id == nearbyFactory.id) {
                                val newStored = it.storedInputs + milkInInventory
                                it.copy(
                                    storedInputs = newStored,
                                    productionTimer = if (it.storedInputs == 0 && it.productionTimer <= 0f) 12f else it.productionTimer,
                                )
                            } else it
                        }
                        return world.copy(
                            factories = updatedFactories,
                            player = world.player.startAction(ToolType.HANDS, nearbyFactory.position, 0.3f).copy(
                                inventory = world.player.inventory
                                    .remove(ItemType.MILK, milkInInventory)
                                    .add(ItemType.MILK_PAIL, milkInInventory),
                                activeItemId = null,
                            ),
                        )
                    }
                }
                FactoryType.EGG_PACKER,
                FactoryType.VEGGIE_PACKER,
                FactoryType.BAKERY,
                FactoryType.FISH_PROCESSOR -> {
                    events += WorldEvent.FactoryMinigameTriggered(nearbyFactory.id, nearbyFactory.type)
                    return world
                }
            }
        }

        // Animal feeding & breeding
        if (world.player.inventory.count(ItemType.CARROT) > 0) {
            val nearbyPig = world.animals.firstOrNull {
                it.type == DomesticAnimalType.PIG && it.lifeStage == AnimalLifeStage.ADULT &&
                    it.handling == AnimalHandlingState.FREE && it.breedingCooldown <= 0f &&
                    it.position.distanceTo(playerPosition) <= config.interactionRange + it.collisionRadius
            }
            if (nearbyPig != null) {
                events.sound(SoundCue.ANIMAL_PICK_UP, nearbyPig.position)
                val mate = world.animals.firstOrNull {
                    it.type == DomesticAnimalType.PIG && it.id != nearbyPig.id &&
                        it.loveTimer > 0f && it.position.distanceTo(nearbyPig.position) <= 4.5f
                }
                val newAnimals = world.animals.toMutableList()
                val pigIdx = newAnimals.indexOfFirst { it.id == nearbyPig.id }
                if (mate != null) {
                    val spawnPos = (nearbyPig.position + mate.position) * 0.5f
                    val baby = DomesticAnimalState(
                        id = world.nextEntityId,
                        name = "Cerdito #${world.nextEntityId % 100}",
                        type = DomesticAnimalType.PIG,
                        position = spawnPos,
                        lifeStage = AnimalLifeStage.BABY,
                        breedingCooldown = 180f,
                    )
                    newAnimals[pigIdx] = nearbyPig.copy(loveTimer = 0f, breedingCooldown = 180f)
                    val mateIdx = newAnimals.indexOfFirst { it.id == mate.id }
                    if (mateIdx >= 0) newAnimals[mateIdx] = mate.copy(loveTimer = 0f, breedingCooldown = 180f)
                    newAnimals.add(baby)
                    events += WorldEvent.AnimalBorn(AnimalKind.PIG, nearbyPig.id)
                    events.sound(SoundCue.ANIMAL_PICK_UP, spawnPos)
                } else {
                    newAnimals[pigIdx] = nearbyPig.copy(loveTimer = 25f)
                }
                return world.copy(
                    animals = newAnimals,
                    nextEntityId = world.nextEntityId + (if (mate != null) 1 else 0),
                    player = world.player.startAction(ToolType.HANDS, nearbyPig.position, 0.3f).copy(
                        inventory = world.player.inventory.remove(ItemType.CARROT, 1),
                    ),
                )
            }
        }

        if (world.player.inventory.count(ItemType.WHEAT) > 0) {
            val nearbyCow = world.animals.firstOrNull {
                it.type == DomesticAnimalType.COW && it.lifeStage == AnimalLifeStage.ADULT &&
                    it.handling == AnimalHandlingState.FREE && it.breedingCooldown <= 0f &&
                    it.position.distanceTo(playerPosition) <= config.interactionRange + it.collisionRadius
            }
            if (nearbyCow != null) {
                events.sound(SoundCue.ANIMAL_PICK_UP, nearbyCow.position)
                val mate = world.animals.firstOrNull {
                    it.type == DomesticAnimalType.COW && it.id != nearbyCow.id &&
                        it.loveTimer > 0f && it.position.distanceTo(nearbyCow.position) <= 4.5f
                }
                val newAnimals = world.animals.toMutableList()
                val cowIdx = newAnimals.indexOfFirst { it.id == nearbyCow.id }
                if (mate != null) {
                    val spawnPos = (nearbyCow.position + mate.position) * 0.5f
                    val baby = DomesticAnimalState(
                        id = world.nextEntityId,
                        name = "Ternero #${world.nextEntityId % 100}",
                        type = DomesticAnimalType.COW,
                        position = spawnPos,
                        lifeStage = AnimalLifeStage.BABY,
                        breedingCooldown = 220f,
                    )
                    newAnimals[cowIdx] = nearbyCow.copy(loveTimer = 0f, breedingCooldown = 220f)
                    val mateIdx = newAnimals.indexOfFirst { it.id == mate.id }
                    if (mateIdx >= 0) newAnimals[mateIdx] = mate.copy(loveTimer = 0f, breedingCooldown = 220f)
                    newAnimals.add(baby)
                    events += WorldEvent.AnimalBorn(AnimalKind.COW, nearbyCow.id)
                    events.sound(SoundCue.ANIMAL_PICK_UP, spawnPos)
                } else {
                    newAnimals[cowIdx] = nearbyCow.copy(loveTimer = 25f)
                }
                return world.copy(
                    animals = newAnimals,
                    nextEntityId = world.nextEntityId + (if (mate != null) 1 else 0),
                    player = world.player.startAction(ToolType.HANDS, nearbyCow.position, 0.3f).copy(
                        inventory = world.player.inventory.remove(ItemType.WHEAT, 1),
                    ),
                )
            }
        }

        val totalSeeds = world.player.inventory.count(ItemType.SEED) + world.player.inventory.count(ItemType.WHEAT_SEED)
        if (totalSeeds > 0) {
            val nearbyChicken = world.chickens.firstOrNull {
                it.lifeStage == ChickenLifeStage.ADULT && it.handling == AnimalHandlingState.FREE &&
                    it.breedingCooldown <= 0f &&
                    it.position.distanceTo(playerPosition) <= config.interactionRange + it.collisionRadius
            }
            if (nearbyChicken != null) {
                val seedItem = if (world.player.inventory.count(ItemType.SEED) > 0) ItemType.SEED else ItemType.WHEAT_SEED
                events.sound(SoundCue.CHICKEN_CLUCK, nearbyChicken.position)
                val mate = world.chickens.firstOrNull {
                    it.id != nearbyChicken.id && it.loveTimer > 0f &&
                        it.position.distanceTo(nearbyChicken.position) <= 3.8f
                }
                val newChickens = world.chickens.toMutableList()
                val chkIdx = newChickens.indexOfFirst { it.id == nearbyChicken.id }
                if (mate != null) {
                    val spawnPos = (nearbyChicken.position + mate.position) * 0.5f
                    val chick = ChickenState(
                        id = world.nextEntityId,
                        name = "Pollito #${world.nextEntityId % 100}",
                        position = spawnPos,
                        lifeStage = ChickenLifeStage.CHICK,
                        breedingCooldown = 150f,
                    )
                    newChickens[chkIdx] = nearbyChicken.copy(loveTimer = 0f, breedingCooldown = 150f)
                    val mateIdx = newChickens.indexOfFirst { it.id == mate.id }
                    if (mateIdx >= 0) newChickens[mateIdx] = mate.copy(loveTimer = 0f, breedingCooldown = 150f)
                    newChickens.add(chick)
                    events.sound(SoundCue.CHICKEN_CLUCK, spawnPos)
                } else {
                    newChickens[chkIdx] = nearbyChicken.copy(loveTimer = 25f)
                }
                return world.copy(
                    chickens = newChickens,
                    nextEntityId = world.nextEntityId + (if (mate != null) 1 else 0),
                    player = world.player.startAction(ToolType.HANDS, nearbyChicken.position, 0.3f).copy(
                        inventory = world.player.inventory.remove(seedItem, 1),
                    ),
                )
            }
        }

        if (playerPosition.distanceTo(world.barn.doorPosition) <= config.interactionRange) {
            val carried = world.player.inventory.count(ItemType.EGG)
            val room = world.barn.eggCapacity - world.barn.storedEggs
            val deposited = minOf(carried, room)
            if (deposited > 0) {
                val inventory = world.player.inventory.remove(ItemType.EGG, deposited)
                val stored = world.barn.storedEggs + deposited
                events.sound(SoundCue.BARN_DEPOSIT, world.barn.doorPosition)
                events += WorldEvent.EggsStored(deposited, stored)
                return world.copy(
                    player = world.player.startAction(
                        tool = ToolType.HANDS,
                        target = world.barn.doorPosition,
                        duration = 0.28f,
                    ).copy(inventory = inventory),
                    barn = world.barn.copy(storedEggs = stored),
                )
            }
        }

        val maturePlot = world.plots
            .asSequence()
            .filter { it.crop?.stage == CropStage.MATURE }
            .filter { it.position.distanceTo(playerPosition) <= config.interactionRange }
            .minByOrNull { it.position.distanceSquaredTo(playerPosition) }
        if (maturePlot != null) {
            val crop = requireNotNull(maturePlot.crop)
            val plots = world.plots.replacePlotById(maturePlot.id) { it.copy(crop = null, moisture = it.moisture * 0.5f) }
            val inventory = world.player.inventory.add(crop.type.produceItem, crop.type.harvestYield)
            events.sound(SoundCue.CROP_HARVEST, maturePlot.position)
            events += WorldEvent.ItemCollected(crop.type.produceItem, crop.type.harvestYield)
            events += WorldEvent.PlotChanged(maturePlot.id, null)
            return world.copy(
                plots = plots,
                player = world.player.startAction(
                    tool = ToolType.HANDS,
                    target = maturePlot.position,
                    duration = 0.3f,
                ).copy(inventory = inventory),
            )
        }

        val tree = nearestStandingTree(world, config.interactionRange)
        if (tree != null && tree.shakeCooldown <= 0f) {
            var nextId = world.nextEntityId
            val droppedApple = random.nextFloat() < config.treeAppleChance
            val drops = if (droppedApple) {
                world.groundItems + GroundItemState(
                    id = nextId++,
                    item = ItemType.APPLE,
                    position = tree.position + randomDirection() * randomBetween(0.65f, 1.25f),
                    animationPhase = randomPhase(),
                )
            } else world.groundItems
            val trees = world.trees.replaceTreeById(tree.id) {
                it.copy(shakeTimer = config.treeShakeSeconds, shakeCooldown = config.treeShakeCooldownSeconds)
            }
            events.sound(SoundCue.TREE_RUSTLE, tree.position)
            events += WorldEvent.TreeShaken(tree.id, droppedApple)
            return world.copy(
                trees = trees,
                groundItems = drops,
                nextEntityId = nextId,
                player = world.player.startAction(
                    tool = ToolType.HANDS,
                    target = tree.position,
                    duration = 0.35f,
                ),
            )
        }
        return world
    }

    private fun useFence(world: FarmWorldState): FarmWorldState {
        val raw = interactionPoint(world, 1.15f)
        val target = snapToTileCenter(raw, FENCE_TILE_SIZE)
        val existingFence = world.staticObstacles.firstOrNull {
            it.kind == ObstacleKind.FENCE && shapeCenter(it.collider).distanceSquaredTo(target) < 0.2f
        }
        if (existingFence != null) {
            return world.copy(
                staticObstacles = world.staticObstacles.filterNot { it.id == existingFence.id },
                player = world.player.startAction(ToolType.FENCE, target, 0.22f).copy(
                    inventory = world.player.inventory.add(ItemType.FENCE),
                ),
            )
        }
        if (world.player.inventory.count(ItemType.FENCE) <= 0) return world
        // Exact half-cell geometry makes adjacent fence sprites join seamlessly.
        val halfSize = FENCE_TILE_SIZE * 0.5f
        val collider = CollisionRect(target.x - halfSize, target.y - halfSize, target.x + halfSize, target.y + halfSize)
        val overlapsFence = world.staticObstacles.any {
            it.kind == ObstacleKind.FENCE && shapeCenter(it.collider).distanceSquaredTo(target) < 0.04f
        }
        val blockedByWorld = world.staticObstacles.any {
            it.kind != ObstacleKind.FENCE && rectIntersectsShape(collider, it.collider)
        } || world.waterBodies.any { rectIntersectsShape(collider, it.shape) } ||
            world.trees.any { rectIntersectsShape(collider, it.collider) } ||
            collider.intersects(world.barn.collider) ||
            world.factories.any { collider.intersects(it.collider) } ||
            (world.truck.phase != TruckPhase.ABSENT && collider.intersects(truckCollider(world.truck)))
        if (!world.bounds.inset(halfSize).contains(target) || overlapsFence || blockedByWorld) return world
        val fence = StaticObstacleState(world.nextEntityId, ObstacleKind.FENCE, collider, collider.bottom)
        val remaining = world.player.inventory.count(ItemType.FENCE) - 1
        return world.copy(
            staticObstacles = world.staticObstacles + fence,
            nextEntityId = world.nextEntityId + 1,
            player = world.player.startAction(ToolType.FENCE, target, 0.22f).copy(
                inventory = world.player.inventory.remove(ItemType.FENCE),
                activeItemId = if (remaining <= 0) null else world.player.activeItemId,
                selectedTool = if (remaining <= 0) ToolType.HANDS else world.player.selectedTool,
            ),
        )
    }

    private fun usePathTool(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val raw = interactionPoint(world, 1.15f)
        val cellX = floor(raw.x).toInt()
        val cellY = floor(raw.y).toInt()
        val targetPos = Vector2(cellX + 0.5f, cellY + 0.5f)
        val existing = world.pathTiles.firstOrNull { it.cellX == cellX && it.cellY == cellY }
        if (existing != null) {
            events += WorldEvent.PathRemoved(existing.id, cellX, cellY)
            events.sound(SoundCue.ROCK_BREAK, targetPos, 0.45f)
            return world.copy(
                pathTiles = world.pathTiles.filterNot { it.id == existing.id },
                player = world.player.startAction(ToolType.PATH_TOOL, targetPos, 0.22f),
            )
        }
        val tileRect = CollisionRect(cellX.toFloat(), cellY.toFloat(), cellX + 1f, cellY + 1f)
        val blocked = world.waterBodies.any { rectIntersectsShape(tileRect, it.shape) } ||
            tileRect.intersects(world.barn.collider) ||
            world.plots.any { it.collider.intersects(tileRect) } ||
            world.factories.any { it.collider.intersects(tileRect) }
        if (blocked || !world.bounds.inset(0.5f).contains(targetPos)) return world

        val newTile = PathTileState(
            id = world.nextEntityId,
            cellX = cellX,
            cellY = cellY,
            style = PathStyle.GRAVEL,
        )
        events += WorldEvent.PathPlaced(newTile.id, cellX, cellY)
        events.sound(SoundCue.SOIL_TILL, targetPos, 0.55f)
        return world.copy(
            pathTiles = world.pathTiles + newTile,
            grassTufts = world.grassTufts.filterNot { tileRect.contains(it.position) },
            nextEntityId = world.nextEntityId + 1,
            player = world.player.startAction(ToolType.PATH_TOOL, targetPos, 0.22f),
        )
    }

    private fun useAxe(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val slaughterhouse = world.factories.firstOrNull {
            it.type == FactoryType.SLAUGHTERHOUSE && it.phase == ConstructionPhase.OPERATIONAL &&
                (it.hasPigInChamber || it.hasCowInChamber) &&
                it.collider.distanceTo(world.player.position) <= config.interactionRange + 0.8f
        }
        if (slaughterhouse != null) {
            val isCow = slaughterhouse.hasCowInChamber
            events.sound(SoundCue.SLAUGHTER_HIT, slaughterhouse.position)
            events += WorldEvent.SlaughterFlash(slaughterhouse.position)
            val meatDropPos = slaughterhouse.doorPosition
            val dropCount = if (isCow) 4 else 3
            val drops = List(dropCount) { idx ->
                val offset = (idx - (dropCount - 1) * 0.5f) * 0.35f
                GroundItemState(world.nextEntityId + idx, ItemType.MEAT, meatDropPos + Vector2(offset, 0.25f), animationPhase = randomPhase())
            }
            val updatedFactories = world.factories.map {
                if (it.id == slaughterhouse.id) it.copy(hasPigInChamber = false, hasCowInChamber = false) else it
            }
            return world.copy(
                factories = updatedFactories,
                groundItems = world.groundItems + drops,
                nextEntityId = world.nextEntityId + dropCount,
                player = world.player.startAction(ToolType.AXE, slaughterhouse.position, 0.4f),
            )
        }

        val fence = nearestFence(world, config.interactionRange)
        val tree = nearestAxeTarget(world, config.interactionRange)
        val fenceDistance = fence?.collider?.distanceTo(world.player.position) ?: Float.POSITIVE_INFINITY
        val treeDistance = tree?.collider?.distanceTo(world.player.position) ?: Float.POSITIVE_INFINITY
        if (fence != null && fenceDistance <= treeDistance) {
            val target = shapeCenter(fence.collider)
            events.sound(SoundCue.AXE_SWING, world.player.position)
            events.sound(SoundCue.AXE_HIT, target)
            events += WorldEvent.FenceRemoved(fence.id)
            return world.copy(
                staticObstacles = world.staticObstacles.filterNot { it.id == fence.id },
                player = world.player.startAction(ToolType.AXE, target, 0.34f).copy(
                    inventory = world.player.inventory.add(ItemType.FENCE),
                ),
            )
        }
        if (tree == null) {
            events.sound(SoundCue.AXE_SWING, world.player.position)
            return world.copy(
                player = world.player.startAction(
                    tool = ToolType.AXE,
                    target = interactionPoint(world, config.plotPlacementDistance),
                    duration = 0.4f,
                ),
            )
        }

        if (tree.lifeState == TreeLifeState.STUMP) {
            val remaining = tree.health - 1
            events.sound(SoundCue.AXE_SWING, world.player.position)
            events.sound(SoundCue.AXE_HIT, tree.position)
            if (remaining > 0) {
                return world.copy(
                    trees = world.trees.replaceTreeById(tree.id) { it.copy(health = remaining) },
                    player = world.player.startAction(ToolType.AXE, tree.position, 0.4f),
                )
            }

            val woodYield = config.stumpWoodYield.coerceAtLeast(1)
            val drop = GroundItemState(
                id = world.nextEntityId,
                item = ItemType.WOOD,
                amount = woodYield,
                position = tree.position,
                animationPhase = randomPhase(),
            )
            events += WorldEvent.TreeStumpRemoved(tree.id)
            return world.copy(
                trees = world.trees.filterNot { it.id == tree.id },
                groundItems = world.groundItems + drop,
                nextEntityId = world.nextEntityId + 1,
                player = world.player.startAction(ToolType.AXE, tree.position, 0.4f),
            )
        }

        val health = tree.health - 1
        val isFalling = health <= 0
        val away = (tree.position - world.player.position).normalized().let {
            if (it == Vector2.ZERO) Vector2.RIGHT else it
        }
        val trees = world.trees.replaceTreeById(tree.id) {
            it.copy(
                health = health.coerceAtLeast(0),
                lifeState = if (isFalling) TreeLifeState.FALLING else TreeLifeState.STANDING,
                fallProgress = if (isFalling) 0f else it.fallProgress,
                fallDirection = if (isFalling) away else it.fallDirection,
                shakeTimer = if (isFalling) 0f else 0.18f,
            )
        }
        events.sound(SoundCue.AXE_SWING, world.player.position)
        events.sound(SoundCue.AXE_HIT, tree.position)
        if (isFalling) events.sound(SoundCue.TREE_FALL, tree.position)
        return world.copy(
            trees = trees,
            player = world.player.startAction(ToolType.AXE, tree.position, 0.42f),
        )
    }

    private fun usePickaxe(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val target = nearestRock(world, config.interactionRange)
        val animationTarget = target?.anchorPosition()
            ?: interactionPoint(world, config.plotPlacementDistance)
        events.sound(SoundCue.PICKAXE_SWING, world.player.position)
        if (target == null) {
            return world.copy(
                player = world.player.startAction(ToolType.PICKAXE, animationTarget, 0.42f),
            )
        }

        val remainingHitPoints = target.hitPoints - 1
        events.sound(SoundCue.ROCK_HIT, animationTarget)
        events += WorldEvent.RockHit(target.id, remainingHitPoints.coerceAtLeast(0))
        if (remainingHitPoints > 0) {
            return world.copy(
                staticObstacles = world.staticObstacles.map {
                    if (it.id == target.id) it.copy(hitPoints = remainingHitPoints) else it
                },
                player = world.player.startAction(ToolType.PICKAXE, animationTarget, 0.42f),
            )
        }

        val stoneYield = config.rockStoneYield.coerceAtLeast(1)
        val drop = GroundItemState(
            id = world.nextEntityId,
            item = ItemType.STONE,
            position = animationTarget,
            amount = stoneYield,
            animationPhase = randomPhase(),
        )
        events.sound(SoundCue.ROCK_BREAK, animationTarget)
        events += WorldEvent.RockBroken(target.id, stoneYield)
        return world.copy(
            staticObstacles = world.staticObstacles.filterNot { it.id == target.id },
            groundItems = world.groundItems + drop,
            nextEntityId = world.nextEntityId + 1,
            player = world.player.startAction(ToolType.PICKAXE, animationTarget, 0.42f),
        )
    }

    private fun useMachete(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val fox = world.fox.takeIf {
            it.phase == FoxPhase.HUNTING || it.phase == FoxPhase.FLEEING
        }?.takeIf {
            it.position.distanceTo(world.player.position) <= config.interactionRange + 0.43f
        }
        val target = fox?.position ?: interactionPoint(world, config.plotPlacementDistance)
        events.sound(SoundCue.MACHETE_SWING, world.player.position)
        if (fox == null) {
            return world.copy(
                player = world.player.startAction(ToolType.MACHETE, target, 0.38f),
            )
        }

        val health = fox.health - 1
        events.sound(SoundCue.MACHETE_HIT, fox.position)
        events += WorldEvent.FoxHit(fox.id, health.coerceAtLeast(0))
        val updatedFox = if (health <= 0) {
            events.sound(SoundCue.FOX_DEFEATED, fox.position)
            events += WorldEvent.FoxDefeated(fox.id)
            fox.copy(
                phase = FoxPhase.DEAD,
                velocity = Vector2.ZERO,
                health = 0,
                targetChickenId = null,
                phaseTimer = config.foxRespawnAfterDefeatSeconds.coerceAtLeast(0f),
            )
        } else {
            fox.copy(health = health, biteCooldown = maxOf(fox.biteCooldown, 0.35f))
        }
        return world.copy(
            fox = updatedFox,
            player = world.player.startAction(ToolType.MACHETE, fox.position, 0.38f),
        )
    }

    private fun usePetBall(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        if (
            world.ball.phase == BallPhase.ON_GROUND &&
            world.ball.position.distanceTo(world.player.position) <= config.interactionRange
        ) {
            events += WorldEvent.BallPickedUp
            return world.copy(
                ball = world.ball.copy(
                    phase = BallPhase.WITH_PLAYER,
                    position = world.player.position,
                    velocity = Vector2.ZERO,
                    holderAnimalId = null,
                    targetPetId = null,
                ),
                player = world.player.startAction(ToolType.PET_BALL, world.ball.position, 0.24f),
            )
        }
        if (world.ball.phase != BallPhase.WITH_PLAYER) return world

        val direction = (interactionPoint(world, 1f) - world.player.position).normalized().let {
            if (it == Vector2.ZERO) facingVector(world.player.facing) else it
        }
        val velocity = direction * config.ballThrowSpeed
        val targetPet = world.animals
            .asSequence()
            .filter { it.type.isPet && it.handling == AnimalHandlingState.FREE }
            .minByOrNull { it.position.distanceSquaredTo(world.player.position) }
        val start = world.player.position + direction * 0.55f
        events += WorldEvent.BallThrown(start, velocity)
        events.sound(SoundCue.BALL_THROW, world.player.position)
        return world.copy(
            ball = world.ball.copy(
                phase = BallPhase.FLYING,
                position = start,
                velocity = velocity,
                flightTimer = config.ballFlightSeconds.coerceAtLeast(0.05f),
                holderAnimalId = null,
                targetPetId = targetPet?.id,
            ),
            player = world.player.startAction(ToolType.PET_BALL, start + direction, 0.34f),
        )
    }

    private fun useHoe(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val plotSize = (config.placementGridCellSize * config.plotGridCells.coerceAtLeast(1))
            .coerceAtLeast(0.5f)
        val target = snapToTileCenter(interactionPoint(world, config.plotPlacementDistance), plotSize)
        val existingTilledPlot = nearestPlotToTarget(world, target) { it.soil == SoilState.TILLED }
        if (existingTilledPlot != null && existingTilledPlot.crop == null) {
            return world.copy(
                player = world.player.startAction(ToolType.HOE, existingTilledPlot.position, 0.32f),
            )
        }
        val nearbyRawPlot = nearestPlotToTarget(world, target) { it.soil == SoilState.RAW }
        val plot = nearbyRawPlot ?: SoilPlotState(
            id = world.nextEntityId,
            position = target,
            size = plotSize,
        ).takeIf { isValidNewPlot(it, world) }
            ?: return world.copy(
                player = world.player.startAction(ToolType.HOE, target, 0.32f),
            )

        events.sound(SoundCue.SOIL_TILL, plot.position)
        events += WorldEvent.PlotChanged(plot.id, null)
        val plots = if (nearbyRawPlot == null) {
            world.plots + plot.copy(soil = SoilState.TILLED, moisture = 0.28f)
        } else {
            world.plots.replacePlotById(plot.id) { it.copy(soil = SoilState.TILLED, moisture = 0.28f) }
        }
        return world.copy(
            plots = plots,
            nextEntityId = if (nearbyRawPlot == null) world.nextEntityId + 1 else world.nextEntityId,
            player = world.player.startAction(ToolType.HOE, plot.position, 0.32f),
        )
    }

    private fun removePlotUnderOrInFrontOfPlayer(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val plotSize = (config.placementGridCellSize * config.plotGridCells.coerceAtLeast(1)).coerceAtLeast(0.5f)
        val target = snapToTileCenter(interactionPoint(world, config.plotPlacementDistance), plotSize)
        val targetPlot = nearestPlotToTarget(world, target) { it.soil == SoilState.TILLED }
            ?: nearestPlot(world) { it.soil == SoilState.TILLED && it.collider.distanceTo(world.player.position) <= config.interactionRange }
            ?: return world
        events.sound(SoundCue.SOIL_TILL, targetPlot.position)
        events += WorldEvent.PlotChanged(targetPlot.id, null)
        val plots = world.plots.filterNot { it.id == targetPlot.id }
        val (playerWithTool, broken) = world.player.startAction(ToolType.HOE, targetPlot.position, 0.32f).useTool(ToolType.HOE)
        if (broken) {
            events += WorldEvent.ToolBroken(ToolType.HOE)
            events.sound(SoundCue.ROCK_BREAK, playerWithTool.position, 0.65f)
        }
        return world.copy(
            plots = plots,
            player = playerWithTool,
        )
    }

    private fun useMilkPail(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        if (world.player.inventory.count(ItemType.MILK_PAIL) <= 0) return world
        val playerPosition = world.player.position
        val adultCow = world.animals
            .asSequence()
            .filter {
                    it.type == DomesticAnimalType.COW &&
                    it.handling == AnimalHandlingState.FREE &&
                    it.lifeStage == AnimalLifeStage.ADULT &&
                    it.milkReadyInSeconds <= 0f
            }
            .filter { it.position.distanceTo(playerPosition) <= config.interactionRange + it.collisionRadius }
            .minByOrNull { it.position.distanceSquaredTo(playerPosition) }
        if (adultCow != null) {
            val amount = 1
            events += WorldEvent.CowMilked(adultCow.id, adultCow.name, amount)
            events += WorldEvent.ItemCollected(ItemType.MILK, amount)
            events.sound(SoundCue.COW_MILK, adultCow.position)
            return world.copy(
                animals = world.animals.map { cow ->
                    if (cow.id == adultCow.id) {
                        cow.copy(milkReadyInSeconds = config.cowMilkIntervalSeconds)
                    } else cow
                },
                player = world.player.startAction(ToolType.MILK_PAIL, adultCow.position, 0.4f).copy(
                    inventory = world.player.inventory
                        .remove(ItemType.MILK_PAIL, amount)
                        .add(ItemType.MILK, amount),
                    selectedTool = ToolType.HANDS,
                    activeItemId = ItemType.MILK,
                ),
            )
        }
        return world
    }

    private fun useBlueprint(world: FarmWorldState, tool: ToolType, events: MutableList<WorldEvent>): FarmWorldState {
        val fType = when (tool) {
            ToolType.BLUEPRINT_DAIRY -> FactoryType.DAIRY
            ToolType.BLUEPRINT_SLAUGHTERHOUSE -> FactoryType.SLAUGHTERHOUSE
            ToolType.BLUEPRINT_EGG_PACKER -> FactoryType.EGG_PACKER
            ToolType.BLUEPRINT_VEGGIE_PACKER -> FactoryType.VEGGIE_PACKER
            ToolType.BLUEPRINT_BAKERY -> FactoryType.BAKERY
            ToolType.BLUEPRINT_FISH_PROCESSOR -> FactoryType.FISH_PROCESSOR
            else -> return world
        }
        val blueprintCount = world.player.toolDurability[tool] ?: 0
        if (blueprintCount <= 0) return world
        val orientation = world.selectedBlueprintOrientation
        val target = snapToTileCenter(interactionPoint(world, 2.5f), 1f)
        val halfW = fType.defaultWidth * 0.5f
        val halfH = fType.defaultHeight * 0.5f
        val cellX = floor(target.x - halfW).toInt()
        val cellY = floor(target.y - halfH).toInt()
        val collider = CollisionRect(cellX.toFloat(), cellY.toFloat(), (cellX + fType.defaultWidth).toFloat(), (cellY + fType.defaultHeight).toFloat())

        if (collider.left < 4.5f || collider.right > 59.5f || collider.top < 4.5f || collider.bottom > 42.5f) return world
        if (world.waterBodies.any { rectIntersectsShape(collider, it.shape) }) return world
        if (world.staticObstacles.any { rectIntersectsShape(collider, it.collider) }) return world
        if (collider.intersects(world.barn.collider)) return world
        if (world.trees.any { rectIntersectsShape(collider, it.collider) }) return world
        if (world.plots.any { collider.intersects(it.collider) }) return world
        if (world.pathTiles.any { collider.intersects(it.collider) }) return world
        if (world.factories.any { collider.intersects(it.collider) }) return world

        val newFactory = FactoryBuildingState(
            id = world.nextEntityId,
            type = fType,
            cellX = cellX,
            cellY = cellY,
            widthCells = fType.defaultWidth,
            heightCells = fType.defaultHeight,
            orientation = orientation,
            phase = ConstructionPhase.SCAFFOLD,
            woodContributed = 0,
            stoneContributed = 0,
        )

        events += WorldEvent.FactoryPlaced(newFactory.id, fType)
        events.sound(SoundCue.CONSTRUCTION_HAMMER, target)

        val dur = blueprintCount - 1
        val newDurabilities = if (dur <= 0) world.player.toolDurability - tool else world.player.toolDurability + (tool to dur)
        val nextTool = if (dur <= 0) ToolType.HANDS else world.player.selectedTool

        return world.copy(
            factories = world.factories + newFactory,
            nextEntityId = world.nextEntityId + 1,
            player = world.player.startAction(tool, target, 0.35f).copy(
                selectedTool = nextTool,
                toolDurability = newDurabilities,
            ),
        )
    }

    private fun useArchitectPencil(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val newMode = !world.isPencilMode
        events.sound(SoundCue.TOOL_SELECT, world.player.position)
        val targetZoom = if (newMode) 0.52f else 1.45f
        val newCamera = clampCamera(world.camera.copy(zoom = targetZoom), world.bounds)
        return world.copy(isPencilMode = newMode, selectedMoveStructureId = null, camera = newCamera)
    }

    private fun handlePencilClick(world: FarmWorldState, targetCell: Pair<Int, Int>, events: MutableList<WorldEvent>): FarmWorldState {
        val cellCenter = Vector2(targetCell.first + 0.5f, targetCell.second + 0.5f)
        val selectedId = world.selectedMoveStructureId
        if (selectedId == null) {
            if (world.barn.collider.contains(cellCenter)) {
                events.sound(SoundCue.TOOL_SELECT, cellCenter)
                return world.copy(selectedMoveStructureId = -1)
            }
            val clickedFactory = world.factories.firstOrNull { it.collider.contains(cellCenter) }
            if (clickedFactory != null) {
                events.sound(SoundCue.TOOL_SELECT, cellCenter)
                return world.copy(selectedMoveStructureId = clickedFactory.id)
            }
            return world
        } else {
            if (selectedId == -1) {
                val barnW = world.barn.collider.width
                val barnH = world.barn.collider.height
                val newLeft = targetCell.first.toFloat()
                val newTop = targetCell.second.toFloat()
                val newCollider = CollisionRect(newLeft, newTop, newLeft + barnW, newTop + barnH)
                if (newCollider.left < 4.5f || newCollider.right > 59.5f || newCollider.top < 4.5f || newCollider.bottom > 42.5f) return world
                if (world.waterBodies.any { rectIntersectsShape(newCollider, it.shape) }) return world
                if (world.factories.any { newCollider.intersects(it.collider) }) return world
                val newDoor = Vector2(newLeft + barnW * 0.5f, newCollider.bottom + 0.55f)
                events.sound(SoundCue.CONSTRUCTION_HAMMER, cellCenter)
                events += WorldEvent.BarnMoved(newCollider)
                return world.copy(
                    barn = world.barn.copy(collider = newCollider, doorPosition = newDoor),
                    selectedMoveStructureId = null,
                )
            } else {
                val factory = world.factories.firstOrNull { it.id == selectedId } ?: return world.copy(selectedMoveStructureId = null)
                val newCellX = targetCell.first
                val newCellY = targetCell.second
                val newCollider = CollisionRect(newCellX.toFloat(), newCellY.toFloat(), (newCellX + factory.widthCells).toFloat(), (newCellY + factory.heightCells).toFloat())
                if (newCollider.left < 4.5f || newCollider.right > 59.5f || newCollider.top < 4.5f || newCollider.bottom > 42.5f) return world
                if (world.waterBodies.any { rectIntersectsShape(newCollider, it.shape) }) return world
                if (newCollider.intersects(world.barn.collider)) return world
                if (world.factories.any { it.id != factory.id && newCollider.intersects(it.collider) }) return world
                events.sound(SoundCue.CONSTRUCTION_HAMMER, cellCenter)
                events += WorldEvent.FactoryMoved(factory.id, newCellX, newCellY)
                val updatedFactories = world.factories.map {
                    if (it.id == factory.id) it.copy(cellX = newCellX, cellY = newCellY) else it
                }
                return world.copy(
                    factories = updatedFactories,
                    selectedMoveStructureId = null,
                )
            }
        }
    }

    private fun contributeToScaffold(
        world: FarmWorldState,
        factory: FactoryBuildingState,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var woodToAdd = 0
        var stoneToAdd = 0
        val inv = world.player.inventory
        if (factory.woodContributed < factory.woodRequired && inv.count(ItemType.WOOD) > 0) {
            woodToAdd = 1
        }
        if (factory.stoneContributed < factory.stoneRequired && inv.count(ItemType.STONE) > 0) {
            stoneToAdd = 1
        }
        if (woodToAdd == 0 && stoneToAdd == 0) return world

        val newWood = factory.woodContributed + woodToAdd
        val newStone = factory.stoneContributed + stoneToAdd
        val isComplete = newWood >= factory.woodRequired && newStone >= factory.stoneRequired

        var newInv = inv
        if (woodToAdd > 0) {
            newInv = newInv.remove(ItemType.WOOD, woodToAdd)
            events += WorldEvent.FactoryMaterialAdded(factory.id, ItemType.WOOD, factory.woodRequired - newWood)
        }
        if (stoneToAdd > 0) {
            newInv = newInv.remove(ItemType.STONE, stoneToAdd)
            events += WorldEvent.FactoryMaterialAdded(factory.id, ItemType.STONE, factory.stoneRequired - newStone)
        }

        if (isComplete) {
            events += WorldEvent.FactoryCompleted(factory.id, factory.type)
            events.sound(SoundCue.FACTORY_COMPLETE, factory.position)
        } else {
            events.sound(SoundCue.CONSTRUCTION_HAMMER, factory.position)
        }

        val updatedFactory = factory.copy(
            woodContributed = newWood,
            stoneContributed = newStone,
            phase = if (isComplete) ConstructionPhase.OPERATIONAL else ConstructionPhase.SCAFFOLD,
        )

        return world.copy(
            factories = world.factories.map { if (it.id == factory.id) updatedFactory else it },
            player = world.player.startAction(ToolType.HANDS, factory.position, 0.22f).copy(
                inventory = newInv,
            ),
        )
    }

    private fun updateFactories(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        if (world.factories.isEmpty()) return world
        val updated = world.factories.map { factory ->
            if (factory.type == FactoryType.DAIRY && factory.phase == ConstructionPhase.OPERATIONAL) {
                if (factory.storedInputs > 0) {
                    val newTimer = factory.productionTimer - delta * 60f
                    if (newTimer <= 0f) {
                        factory.copy(
                            storedInputs = factory.storedInputs - 1,
                            storedOutputs = factory.storedOutputs + 1,
                            productionTimer = if (factory.storedInputs > 1) 6f * 60f * 60f else 0f,
                        )
                    } else {
                        factory.copy(productionTimer = newTimer)
                    }
                } else {
                    factory
                }
            } else if (factory.type == FactoryType.BAKERY && factory.phase == ConstructionPhase.OPERATIONAL) {
                var o1Timer = factory.oven1Timer
                var o1Active = factory.oven1Active
                var o1Ready = factory.oven1Ready
                if (o1Active) {
                    o1Timer -= delta
                    if (o1Timer <= 0f) {
                        o1Timer = 0f
                        o1Active = false
                        o1Ready = true
                    }
                }
                var o2Timer = factory.oven2Timer
                var o2Active = factory.oven2Active
                var o2Ready = factory.oven2Ready
                if (o2Active) {
                    o2Timer -= delta
                    if (o2Timer <= 0f) {
                        o2Timer = 0f
                        o2Active = false
                        o2Ready = true
                    }
                }
                factory.copy(
                    oven1Timer = o1Timer,
                    oven1Active = o1Active,
                    oven1Ready = o1Ready,
                    oven2Timer = o2Timer,
                    oven2Active = o2Active,
                    oven2Ready = o2Ready,
                )
            } else {
                factory
            }
        }
        return world.copy(factories = updated)
    }

    private fun updateCowGrazing(
        world: FarmWorldState,
        delta: Float,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        if (world.grassTufts.isEmpty() || world.animals.none { it.type == DomesticAnimalType.COW }) return world
        var tufts = world.grassTufts.toMutableList()
        var modified = false
        val updatedAnimals = world.animals.map { animal ->
            if (animal.type == DomesticAnimalType.COW && animal.lifeStage == AnimalLifeStage.ADULT && animal.handling == AnimalHandlingState.FREE) {
                val nearbyWeed = tufts.firstOrNull { it.position.distanceTo(animal.position) <= 1.5f }
                if (nearbyWeed != null) {
                    tufts.removeIf { it.id == nearbyWeed.id }
                    modified = true
                    events += WorldEvent.CowGrazed(animal.id)
                    events.sound(SoundCue.FOOTSTEP_GRASS, animal.position, 0.6f)
                    animal.copy(milkReadyInSeconds = (animal.milkReadyInSeconds - 45f).coerceAtLeast(0f))
                } else animal
            } else animal
        }
        return if (modified) world.copy(animals = updatedAnimals, grassTufts = tufts) else world
    }

    private fun updateGrassTuftRespawn(world: FarmWorldState, delta: Float): FarmWorldState {
        if (world.grassTufts.size >= 22) return world
        if (random.nextFloat() < delta * 0.08f) {
            val pos = Vector2(randomBetween(5.2f, 58.8f), randomBetween(5.2f, 41.8f))
            val isClear = isInsideFarmFence(pos, 0.55f) &&
                world.waterBodies.none { it.shape.distanceTo(pos) < 1.2f } &&
                world.barn.collider.distanceTo(pos) > 1.2f &&
                world.pathTiles.none { it.collider.distanceTo(pos) < 0.8f } &&
                world.plots.none { it.collider.distanceTo(pos) < 1f } &&
                world.factories.none { it.collider.distanceTo(pos) < 1.2f } &&
                world.staticObstacles.none { it.collider.distanceTo(pos) < 0.55f }
            if (isClear) {
                val newTuft = GrassTuftState(world.nextEntityId, pos, randomPhase())
                return world.copy(
                    grassTufts = world.grassTufts + newTuft,
                    nextEntityId = world.nextEntityId + 1,
                )
            }
        }
        return world
    }

    private fun petAnimal(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val nearbyPet = world.animals.firstOrNull {
            it.type.isPet && it.handling == AnimalHandlingState.FREE &&
                it.position.distanceTo(world.player.position) <= config.interactionRange + 0.4f
        } ?: return world
        events.sound(SoundCue.ANIMAL_PICK_UP, nearbyPet.position, 0.85f)
        events += WorldEvent.PetPetted(nearbyPet.id, nearbyPet.name)
        val updatedAnimals = world.animals.map {
            if (it.id == nearbyPet.id) {
                it.copy(
                    behavior = DomesticAnimalBehavior.IDLE,
                    behaviorTimer = 1.8f,
                    animationTime = 0f,
                )
            } else it
        }
        val playerWithEmote = world.player.copy(
            activeEmote = PlayerEmoteState(
                type = EmoteType.HEART,
                durationSeconds = config.emoteDurationSeconds,
            ),
        )
        return world.copy(animals = updatedAnimals, player = playerWithEmote)
    }

    private fun useWateringCan(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val plot = nearestPlot(world) { it.soil == SoilState.TILLED && it.moisture < 0.98f } ?: return world
        events.sound(SoundCue.WATER_CROP, plot.position)
        return world.copy(
            plots = world.plots.replacePlotById(plot.id) { it.copy(moisture = 1f) },
            player = world.player.startAction(ToolType.WATERING_CAN, plot.position, 0.36f),
        )
    }

    private fun useSeedBag(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val seedItem = when {
            world.player.activeItemId == ItemType.WHEAT_SEED &&
                world.player.inventory.count(ItemType.WHEAT_SEED) > 0 -> ItemType.WHEAT_SEED
            world.player.activeItemId == ItemType.SEED &&
                world.player.inventory.count(ItemType.SEED) > 0 -> ItemType.SEED
            world.player.inventory.count(ItemType.SEED) > 0 -> ItemType.SEED
            world.player.inventory.count(ItemType.WHEAT_SEED) > 0 -> ItemType.WHEAT_SEED
            else -> return world
        }
        val cropType = if (seedItem == ItemType.WHEAT_SEED) CropType.WHEAT else CropType.CARROT
        val plot = nearestPlot(world) { it.soil == SoilState.TILLED && it.crop == null } ?: return world
        events.sound(SoundCue.SEED_PLANT, plot.position)
        events += WorldEvent.ItemSpent(seedItem, 1)
        events += WorldEvent.PlotChanged(plot.id, CropStage.SEED)
        val remainingSeeds = world.player.inventory.count(seedItem) - 1
        return world.copy(
            plots = world.plots.replacePlotById(plot.id) { it.copy(crop = CropState(cropType)) },
            player = world.player.copy(
                inventory = world.player.inventory.remove(seedItem),
                activeItemId = if (remainingSeeds > 0) seedItem else null,
                selectedTool = if (remainingSeeds > 0) world.player.selectedTool else ToolType.HANDS,
                actionCooldown = 0.28f,
                actionAnimation = PlayerActionAnimationState(
                    tool = ToolType.SEED_BAG,
                    targetPosition = plot.position,
                    durationSeconds = 0.28f,
                ),
            ),
        )
    }

    private fun useFishingRod(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val water = world.waterBodies
            .filter { it.shape.distanceTo(world.player.position) <= config.fishingShoreRange }
            .minByOrNull { it.shape.distanceTo(world.player.position) }
            ?: return world
        val bobber = water.shape.nearestPointTo(world.player.position)
        events.sound(SoundCue.FISHING_CAST, world.player.position)
        events += WorldEvent.FishingChanged(FishingPhase.CASTING)
        return world.copy(
            fishing = FishingState(
                phase = FishingPhase.CASTING,
                waterBodyId = water.id,
                bobberPosition = bobber,
                phaseTimer = 0.42f,
                biteDelay = randomBetween(2.2f, 5.8f),
            ),
            player = world.player.startAction(ToolType.FISHING_ROD, bobber, 0.48f).copy(isMoving = false),
        )
    }

    private fun collectNearbyItems(
        world: FarmWorldState,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val playerPosition = world.player.position
        val pickedEggs = world.eggs.filter { it.position.distanceTo(playerPosition) <= config.pickupRange }
        val remainingEggs = if (pickedEggs.isEmpty()) world.eggs else world.eggs - pickedEggs.toSet()
        val pickedItems = world.groundItems.filter { it.position.distanceTo(playerPosition) <= config.pickupRange }
        val remainingItems = if (pickedItems.isEmpty()) world.groundItems else world.groundItems - pickedItems.toSet()
        if (pickedEggs.isEmpty() && pickedItems.isEmpty()) return world

        var inventory = world.player.inventory
        if (pickedEggs.isNotEmpty()) {
            inventory = inventory.add(ItemType.EGG, pickedEggs.size)
            events += WorldEvent.ItemCollected(ItemType.EGG, pickedEggs.size)
        }
        pickedItems.groupBy { it.item }.forEach { (item, entries) ->
            val amount = entries.sumOf { it.amount }
            inventory = inventory.add(item, amount)
            events += WorldEvent.ItemCollected(item, amount)
        }
        events.sound(SoundCue.ITEM_PICKUP, playerPosition)
        return world.copy(
            eggs = remainingEggs,
            groundItems = remainingItems,
            player = world.player.copy(inventory = inventory),
        )
    }

    private fun nearestAnimalReference(world: FarmWorldState, range: Float): AnimalReference? {
        val chicken = world.chickens
            .asSequence()
            .filter { it.handling == AnimalHandlingState.FREE }
            .filter { it.position.distanceTo(world.player.position) <= range + it.collisionRadius }
            .minByOrNull { it.position.distanceSquaredTo(world.player.position) }
        val domestic = world.animals
            .asSequence()
            .filter { it.handling == AnimalHandlingState.FREE }
            .filter { it.position.distanceTo(world.player.position) <= range + it.collisionRadius }
            .minByOrNull { it.position.distanceSquaredTo(world.player.position) }
        return when {
            chicken == null -> domestic?.reference
            domestic == null -> AnimalReference(AnimalKind.CHICKEN, chicken.id)
            chicken.position.distanceSquaredTo(world.player.position) <=
                domestic.position.distanceSquaredTo(world.player.position) -> {
                AnimalReference(AnimalKind.CHICKEN, chicken.id)
            }
            else -> domestic.reference
        }
    }

    private fun grabAnimal(
        world: FarmWorldState,
        reference: AnimalReference,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        var chickens = world.chickens
        var animals = world.animals
        var ball = world.ball
        val targetPosition = when (reference.kind) {
            AnimalKind.CHICKEN -> {
                val chicken = chickens.firstOrNull { it.id == reference.id } ?: return world
                chickens = chickens.map {
                    if (it.id == reference.id) it.copy(
                        handling = AnimalHandlingState.CARRIED,
                        velocity = Vector2.ZERO,
                        behavior = ChickenBehavior.IDLE,
                    ) else it
                }
                chicken.position
            }
            else -> {
                val animal = animals.firstOrNull { it.id == reference.id } ?: return world
                animals = animals.map {
                    if (it.id == reference.id) it.copy(
                        handling = AnimalHandlingState.CARRIED,
                        velocity = Vector2.ZERO,
                        behavior = DomesticAnimalBehavior.CARRIED,
                    ) else it
                }
                if (ball.holderAnimalId == reference.id) {
                    ball = ball.copy(
                        phase = BallPhase.ON_GROUND,
                        position = animal.position,
                        holderAnimalId = null,
                        targetPetId = null,
                    )
                }
                animal.position
            }
        }
        events += WorldEvent.AnimalGrabbed(reference)
        events.sound(SoundCue.ANIMAL_PICK_UP, targetPosition)
        return world.copy(
            chickens = chickens,
            animals = animals,
            ball = ball,
            player = world.player.startAction(ToolType.HANDS, targetPosition, 0.28f).copy(
                carriedAnimal = reference,
            ),
        )
    }

    private fun dropCarriedAnimal(
        world: FarmWorldState,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val reference = world.player.carriedAnimal ?: return world
        val radius = animalRadius(world, reference) ?: return world.copy(
            player = world.player.copy(carriedAnimal = null),
        )
        val desired = interactionPoint(world, 0.95f)
        val position = if (canOccupy(desired, radius, world)) desired else world.player.position
        val chickens = if (reference.kind == AnimalKind.CHICKEN) {
            world.chickens.map {
                if (it.id == reference.id) it.copy(
                    position = position,
                    velocity = Vector2.ZERO,
                    handling = AnimalHandlingState.FREE,
                    thrownTimer = 0f,
                ) else it
            }
        } else world.chickens
        val animals = if (reference.kind != AnimalKind.CHICKEN) {
            world.animals.map {
                if (it.id == reference.id) it.copy(
                    position = position,
                    velocity = Vector2.ZERO,
                    handling = AnimalHandlingState.FREE,
                    thrownTimer = 0f,
                    behavior = DomesticAnimalBehavior.IDLE,
                ) else it
            }
        } else world.animals
        events += WorldEvent.AnimalDropped(reference, position)
        events.sound(SoundCue.ANIMAL_DROP, position)
        return world.copy(
            chickens = chickens,
            animals = animals,
            player = world.player.startAction(ToolType.HANDS, position, 0.24f).copy(carriedAnimal = null),
        )
    }

    private fun throwCarriedAnimal(
        world: FarmWorldState,
        requestedDirection: Vector2,
        events: MutableList<WorldEvent>,
    ): FarmWorldState {
        val reference = world.player.carriedAnimal ?: return world
        val fallback = (interactionPoint(world, 1f) - world.player.position).normalized()
        val direction = requestedDirection.normalized().let {
            if (it == Vector2.ZERO) fallback else it
        }
        val velocity = direction * config.animalThrowSpeed
        val position = world.player.position + direction * 0.62f
        val chickens = if (reference.kind == AnimalKind.CHICKEN) {
            world.chickens.map {
                if (it.id == reference.id) it.copy(
                    position = position,
                    velocity = velocity,
                    handling = AnimalHandlingState.THROWN,
                    thrownTimer = config.animalThrowSeconds,
                    behavior = ChickenBehavior.FLEE,
                ) else it
            }
        } else world.chickens
        val animals = if (reference.kind != AnimalKind.CHICKEN) {
            world.animals.map {
                if (it.id == reference.id) it.copy(
                    position = position,
                    velocity = velocity,
                    handling = AnimalHandlingState.THROWN,
                    thrownTimer = config.animalThrowSeconds,
                    behavior = DomesticAnimalBehavior.THROWN,
                ) else it
            }
        } else world.animals
        events += WorldEvent.AnimalThrown(reference, velocity)
        events.sound(SoundCue.ANIMAL_THROW, world.player.position)
        return world.copy(
            chickens = chickens,
            animals = animals,
            player = world.player.startAction(ToolType.HANDS, position, 0.32f).copy(carriedAnimal = null),
        )
    }

    private fun synchronizeCarriedEntities(world: FarmWorldState): FarmWorldState {
        val carried = world.player.carriedAnimal
        val carryPosition = world.player.position + Vector2(0f, -0.72f)
        var found = carried == null
        val chickens = world.chickens.map {
            if (carried?.kind == AnimalKind.CHICKEN && it.id == carried.id) {
                found = true
                it.copy(position = carryPosition, velocity = Vector2.ZERO, handling = AnimalHandlingState.CARRIED)
            } else it
        }
        val animals = world.animals.map {
            if (carried != null && carried.kind != AnimalKind.CHICKEN && it.id == carried.id) {
                found = true
                it.copy(position = carryPosition, velocity = Vector2.ZERO, handling = AnimalHandlingState.CARRIED)
            } else it
        }
        val player = if (found) world.player else world.player.copy(carriedAnimal = null)
        val ball = if (world.ball.phase == BallPhase.WITH_PLAYER) {
            world.ball.copy(position = world.player.position)
        } else world.ball
        val bicycle = if (world.player.isMounted) {
            world.bicycle.copy(position = world.player.position, isMounted = true)
        } else world.bicycle
        return world.copy(
            chickens = chickens,
            animals = animals,
            player = player,
            ball = ball,
            bicycle = bicycle,
        )
    }

    private fun animalRadius(world: FarmWorldState, reference: AnimalReference): Float? =
        if (reference.kind == AnimalKind.CHICKEN) {
            world.chickens.firstOrNull { it.id == reference.id }?.collisionRadius
        } else {
            world.animals.firstOrNull { it.id == reference.id }?.collisionRadius
        }

    private fun nearestStandingTree(world: FarmWorldState, range: Float): TreeState? = world.trees
        .asSequence()
        .filter { it.lifeState == TreeLifeState.STANDING }
        .filter { it.position.distanceTo(world.player.position) <= range + it.trunkRadius }
        .minByOrNull { it.position.distanceSquaredTo(world.player.position) }

    private fun nearestAxeTarget(world: FarmWorldState, range: Float): TreeState? = world.trees
        .asSequence()
        .filter { it.lifeState == TreeLifeState.STANDING || it.lifeState == TreeLifeState.STUMP }
        .filter { it.position.distanceTo(world.player.position) <= range + it.trunkRadius }
        .minByOrNull { it.position.distanceSquaredTo(world.player.position) }

    private fun nearestRock(world: FarmWorldState, range: Float): StaticObstacleState? = world.staticObstacles
        .asSequence()
        .filter { it.kind == ObstacleKind.ROCK }
        .filter { it.collider.distanceTo(world.player.position) <= range }
        .minByOrNull { it.collider.distanceTo(world.player.position) }

    private fun nearestFence(world: FarmWorldState, range: Float): StaticObstacleState? = world.staticObstacles
        .asSequence()
        .filter { it.kind == ObstacleKind.FENCE }
        .filter { it.collider.distanceTo(world.player.position) <= range }
        .minByOrNull { it.collider.distanceTo(world.player.position) }

    private fun nearestPlot(world: FarmWorldState, predicate: (SoilPlotState) -> Boolean): SoilPlotState? =
        world.plots.asSequence()
            .filter(predicate)
            .filter { it.position.distanceTo(world.player.position) <= config.interactionRange }
            .minByOrNull { it.position.distanceSquaredTo(interactionPoint(world, config.plotPlacementDistance)) }

    private fun nearestPlotToTarget(
        world: FarmWorldState,
        target: Vector2,
        predicate: (SoilPlotState) -> Boolean,
    ): SoilPlotState? = world.plots.asSequence()
        .filter(predicate)
        .filter { it.position.distanceTo(world.player.position) <= config.interactionRange + it.size * 0.5f }
        .filter { it.collider.expanded(0.18f).contains(target) }
        .minByOrNull { it.position.distanceSquaredTo(target) }

    private fun interactionPoint(world: FarmWorldState, distance: Float): Vector2 {
        val preciseDirection = world.player.movementDirection.normalized()
        val direction = if (
            preciseDirection != Vector2.ZERO &&
            facingFor(preciseDirection, world.player.facing) == world.player.facing
        ) preciseDirection else facingVector(world.player.facing)
        return world.player.position + direction * distance
    }

    private fun isValidNewPlot(plot: SoilPlotState, world: FarmWorldState): Boolean {
        val candidate = plot.collider
        if (
            candidate.left < 4.5f || candidate.right > 59.5f ||
            candidate.top < 4.5f || candidate.bottom > 42.5f
        ) return false
        if (world.waterBodies.any { rectIntersectsShape(candidate, it.shape) }) return false
        if (world.staticObstacles.any { rectIntersectsShape(candidate, it.collider) }) return false
        if (candidate.intersects(world.barn.collider)) return false
        if (world.trees.any { rectIntersectsShape(candidate, it.collider) }) return false
        if (world.pathTiles.any { candidate.intersects(it.collider) }) return false
        if (world.factories.any { candidate.intersects(it.collider) }) return false
        // Touching edges are intentional: neighboring 2x2 cells form one continuous field.
        if (world.plots.any { candidate.intersects(it.collider) }) return false
        if (
            world.truck.phase != TruckPhase.ABSENT &&
            candidate.intersects(truckCollider(world.truck))
        ) return false
        return true
    }

    /**
     * The playable farm is deliberately smaller than [FarmWorldState.bounds]. The extra world
     * space is only scenery and the delivery driveway, so procedural growth must never use it.
     */
    private fun isInsideFarmFence(position: Vector2, padding: Float): Boolean =
        position.x >= FARM_FENCE_LEFT + padding &&
            position.x <= FARM_FENCE_RIGHT - padding &&
            position.y >= FARM_FENCE_TOP + padding &&
            position.y <= FARM_FENCE_BOTTOM - padding

    /** Cleans old saves that already contain unreachable growth on or beyond the perimeter. */
    private fun sanitizeUnreachableGrowth(world: FarmWorldState): FarmWorldState {
        val trees = world.trees.filter { tree ->
            isInsideFarmFence(tree.position, 2.4f) &&
                world.staticObstacles.none { it.kind == ObstacleKind.FENCE && it.collider.distanceTo(tree.position) < 1.2f }
        }
        val obstacles = world.staticObstacles.filter { obstacle ->
            if (obstacle.kind != ObstacleKind.ROCK) return@filter true
            val center = shapeCenter(obstacle.collider)
            val padding = when (val collider = obstacle.collider) {
                is CollisionCircle -> collider.radius + 0.6f
                is CollisionRect -> (kotlin.math.max(collider.width, collider.height) * 0.5f) + 0.6f
            }
            isInsideFarmFence(center, padding) &&
                world.staticObstacles.none { it.id != obstacle.id && it.kind == ObstacleKind.FENCE && it.collider.distanceTo(center) < 0.8f }
        }
        val grass = world.grassTufts.filter { tuft ->
            isInsideFarmFence(tuft.position, 0.55f)
        }
        return if (trees.size == world.trees.size &&
            obstacles.size == world.staticObstacles.size &&
            grass.size == world.grassTufts.size
        ) world else world.copy(trees = trees, staticObstacles = obstacles, grassTufts = grass)
    }

    /** Converts the older durable-tool bucket into the new physical empty-container item once. */
    private fun migrateLegacyMilkPail(world: FarmWorldState): FarmWorldState {
        if (ToolType.MILK_PAIL !in world.player.toolDurability) return world
        val inventory = world.player.inventory
        val migratedInventory = if (
            inventory.count(ItemType.MILK_PAIL) == 0 && inventory.count(ItemType.MILK) == 0
        ) inventory.add(ItemType.MILK_PAIL) else inventory
        return world.copy(
            player = world.player.copy(
                inventory = migratedInventory,
                toolDurability = world.player.toolDurability - ToolType.MILK_PAIL,
            ),
        )
    }

    private fun canSpawnRockAt(position: Vector2, radius: Float, world: FarmWorldState): Boolean {
        val candidate = CollisionCircle(position, radius)
        if (!isInsideFarmFence(position, radius + 1.2f)) return false
        if (position.distanceTo(world.player.position) < 4f + radius) return false
        if (world.waterBodies.any { it.shape.intersects(candidate) }) return false
        if (world.staticObstacles.any {
            val minFenceDist = 1.6f + radius
            it.collider.distanceTo(position) < if (it.kind == ObstacleKind.FENCE) minFenceDist else radius
        }) return false
        if (world.gates.any { it.collider.distanceTo(position) < 1.8f + radius }) return false
        if (world.barn.collider.intersects(candidate)) return false
        if (world.trees.any { it.collider.intersects(candidate) }) return false
        if (world.plots.any { it.collider.expanded(0.3f).intersects(candidate) }) return false
        if (world.pathTiles.any { it.collider.intersects(candidate) }) return false
        if (world.factories.any { it.collider.intersects(candidate) }) return false
        if (world.chickens.any { it.position.distanceTo(position) < it.collisionRadius + radius + 0.35f }) return false
        if (world.eggs.any { it.position.distanceTo(position) < radius + 0.4f }) return false
        if (world.groundItems.any { it.position.distanceTo(position) < radius + 0.4f }) return false
        if (
            world.truck.phase != TruckPhase.ABSENT &&
            truckCollider(world.truck).intersects(candidate)
        ) return false
        return true
    }

    private fun canSpawnTreeAt(position: Vector2, world: FarmWorldState): Boolean {
        val trunk = CollisionCircle(position, 0.55f)
        if (!isInsideFarmFence(position, 2.8f)) return false
        if (position.distanceTo(world.player.position) < 4.2f) return false
        if (world.waterBodies.any { it.shape.distanceTo(position) < 2.1f }) return false
        if (world.staticObstacles.any {
            val minFenceDist = 2.2f
            it.collider.distanceTo(position) < if (it.kind == ObstacleKind.FENCE) minFenceDist else 0.8f
        }) return false
        if (world.gates.any { it.collider.distanceTo(position) < 2.5f }) return false
        if (world.barn.collider.distanceTo(position) < 2.1f) return false
        if (world.pathTiles.any { it.collider.distanceTo(position) < 1.1f }) return false
        if (world.factories.any { it.collider.intersects(trunk) }) return false
        if (world.trees.any { it.position.distanceTo(position) < 3.5f }) return false
        if (world.plots.any { it.collider.distanceTo(position) < 1.5f }) return false
        if (world.animals.any { it.position.distanceTo(position) < it.collisionRadius + 1.2f }) return false
        if (world.chickens.any { it.position.distanceTo(position) < it.collisionRadius + 1f }) return false
        if (!world.bicycle.isMounted && world.bicycle.position.distanceTo(position) < 1.5f) return false
        return true
    }

    private fun findFoxSpawnPosition(world: FarmWorldState): Vector2? {
        repeat(24) {
            val position = when (random.nextInt(4)) {
                0 -> Vector2(randomBetween(30.5f, 33.5f), 43.5f)
                1 -> Vector2(5.5f, randomBetween(5.5f, 41.5f))
                2 -> Vector2(58.5f, randomBetween(5.5f, 41.5f))
                else -> Vector2(randomBetween(5.5f, 58.5f), 5.5f)
            }
            if (canOccupy(position, 0.43f, world)) return position
        }
        return null
    }

    private fun updateCamera(world: FarmWorldState, delta: Float): FarmWorldState {
        if (world.isPencilMode) return world
        val follow = 1f - exp(-7.5f * delta)
        val target = world.player.position
        val center = lerp(world.camera.center, target, follow)
        return world.copy(camera = clampCamera(world.camera.copy(center = center), world.bounds))
    }

    private fun canOccupy(position: Vector2, radius: Float, world: FarmWorldState): Boolean {
        val circle = CollisionCircle(position, radius)
        val allowed = world.bounds.inset(radius).contains(position)
        if (!allowed) return false

        // Perimeter fence boundaries: North y=4, West x=4, East x=60, South y=43 (gate at 29.75..34.25)
        val fenceThick = 0.35f
        if (rectIntersectsShape(CollisionRect(4f, 4f - fenceThick, 60f, 4f + fenceThick), circle)) return false
        if (rectIntersectsShape(CollisionRect(4f - fenceThick, 4f, 4f + fenceThick, 43f), circle)) return false
        if (rectIntersectsShape(CollisionRect(60f - fenceThick, 4f, 60f + fenceThick, 43f), circle)) return false
        if (rectIntersectsShape(CollisionRect(4f, 43f - fenceThick, 29.75f, 43f + fenceThick), circle)) return false
        if (rectIntersectsShape(CollisionRect(34.25f, 43f - fenceThick, 60f, 43f + fenceThick), circle)) return false

        // Outer forest: no entity can go outside the fenced farm area,
        // EXCEPT the south driveway down to the delivery truck (x in 29.75..36.5, y in 43..46.8)
        if (position.x < 4f || position.x > 60f || position.y < 4f) return false
        if (position.y > 43f) {
            if (position.x < 29.75f || position.x > 36.5f || position.y > 46.8f) return false
        }

        if (world.waterBodies.any { it.shape.intersects(circle) }) return false
        if (world.staticObstacles.any { it.collider.intersects(circle) }) return false
        if (world.barn.collider.intersects(circle)) return false
        if (world.trees.any { it.blocksMovement && it.collider.intersects(circle) }) return false
        if (world.truck.phase != TruckPhase.ABSENT && truckCollider(world.truck).intersects(circle)) return false
        if (world.factories.any { it.collider.intersects(circle) }) return false
        if (world.gates.any { !it.isOpen && it.collider.intersects(circle) }) return false
        return true
    }

    private fun terrainAt(position: Vector2, world: FarmWorldState): TerrainType =
        world.terrainRegions.lastOrNull { it.shape.contains(position) }?.terrain ?: TerrainType.GRASS

    private fun truckCollider(truck: TruckState) = CollisionRect(
        truck.position.x - 2.2f,
        truck.position.y - 0.85f,
        truck.position.x + 2.2f,
        truck.position.y + 0.85f,
    )

    private fun randomEggDelay(): Float = randomBetween(config.chickenMinEggSeconds, config.chickenMaxEggSeconds)
    private fun randomRockSpawnDelay(): Float = randomBetween(config.rockMinSpawnSeconds, config.rockMaxSpawnSeconds)
    private fun randomTreeSpawnDelay(): Float = randomBetween(config.treeMinSpawnSeconds, config.treeMaxSpawnSeconds)
    private fun randomFoxReturnDelay(): Float = randomBetween(config.foxMinReturnSeconds, config.foxMaxReturnSeconds)
    private fun randomWorldPosition(bounds: CollisionRect, margin: Float): Vector2 = Vector2(
        randomBetween(bounds.left + margin, bounds.right - margin),
        randomBetween(bounds.top + margin, bounds.bottom - margin),
    )
    private fun randomBetween(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)
    private fun randomPhase(): Float = randomBetween(0f, TWO_PI)
    private fun randomDirection(): Vector2 {
        val angle = randomBetween(0f, TWO_PI)
        return Vector2(cos(angle), sin(angle))
    }

    /** Dirt-road waypoints keep the slow courier out of the ponds while remaining interceptable. */
    private fun courierRoute(world: FarmWorldState): List<Vector2> = listOf(
        world.barn.doorPosition + Vector2(0f, 0.35f),
        Vector2(12.5f, 14f),
        Vector2(18f, 12.7f),
        Vector2(24.5f, 16.2f),
        Vector2(31.2f, 20.2f),
        Vector2(35.7f, 25.9f),
        Vector2(36.7f, 32.5f),
        Vector2(42.5f, 34.8f),
        Vector2(50.5f, 38.3f),
        Vector2(57.05f, 43.45f),
    ).map { it.coerceIn(world.bounds, 0.5f) }

    private fun MutableList<WorldEvent>.sound(cue: SoundCue, position: Vector2, volume: Float = 1f) {
        add(WorldEvent.Sound(cue, position, volume))
    }

    private fun purchasePrice(type: DomesticAnimalType): Int = when (type) {
        DomesticAnimalType.COW -> config.cowPurchasePrice
        DomesticAnimalType.PIG -> config.pigPurchasePrice
        DomesticAnimalType.DOG -> config.dogPurchasePrice
        DomesticAnimalType.CAT -> config.catPurchasePrice
    }.coerceAtLeast(0)

    private fun persistentAnimalName(raw: String, type: DomesticAnimalType, id: Int): String {
        val supplied = raw.trim().replace(Regex("\\s+"), " ").take(24)
        if (supplied.isNotEmpty()) return supplied
        val prefix = when (type) {
            DomesticAnimalType.COW -> "Vaca"
            DomesticAnimalType.PIG -> "Cerdito"
            DomesticAnimalType.DOG -> "Perro"
            DomesticAnimalType.CAT -> "Gato"
        }
        return "$prefix $id"
    }

    private fun animalPurchaseSpawnCandidates(door: Vector2): List<Vector2> = listOf(
        door + Vector2(0f, 1.6f),
        door + Vector2(1.5f, 1.4f),
        door + Vector2(-1.5f, 1.4f),
        door + Vector2(2.6f, 2f),
        door + Vector2(-2.6f, 2f),
    )

    private fun useGate(world: FarmWorldState): FarmWorldState {
        val raw = interactionPoint(world, 1.15f)
        val target = snapToTileCenter(raw, FENCE_TILE_SIZE)
        val existingGate = world.gates.firstOrNull { it.position.distanceSquaredTo(target) < 0.2f }
        if (existingGate != null) {
            return world.copy(
                gates = world.gates.filterNot { it.id == existingGate.id },
                player = world.player.startAction(ToolType.GATE, target, 0.22f).copy(
                    inventory = world.player.inventory.add(ItemType.GATE),
                ),
            )
        }
        if (world.player.inventory.count(ItemType.GATE) <= 0) return world
        // Exact half-cell geometry makes neighboring pieces meet without transparent seams.
        val halfSize = FENCE_TILE_SIZE * 0.5f
        val collider = CollisionRect(target.x - halfSize, target.y - halfSize, target.x + halfSize, target.y + halfSize)
        val blocked = world.waterBodies.any { rectIntersectsShape(collider, it.shape) } ||
            world.trees.any { rectIntersectsShape(collider, it.collider) } ||
            collider.intersects(world.barn.collider) ||
            world.factories.any { collider.intersects(it.collider) } ||
            world.gates.any { it.position.distanceSquaredTo(target) < 0.2f }
        if (!world.bounds.inset(halfSize).contains(target) || blocked) return world
        val cellX = kotlin.math.floor(target.x).toInt()
        val cellY = kotlin.math.floor(target.y).toInt()
        val gate = FenceGateState(
            id = world.nextEntityId,
            cellX = cellX,
            cellY = cellY,
            isOpen = false,
        )
        val remaining = world.player.inventory.count(ItemType.GATE) - 1
        return world.copy(
            gates = world.gates + gate,
            nextEntityId = world.nextEntityId + 1,
            player = world.player.startAction(ToolType.GATE, target, 0.22f).copy(
                inventory = world.player.inventory.remove(ItemType.GATE),
                activeItemId = if (remaining <= 0) null else world.player.activeItemId,
                selectedTool = if (remaining <= 0) ToolType.HANDS else world.player.selectedTool,
            ),
        )
    }

    private fun toggleGate(world: FarmWorldState, gateId: Int, events: MutableList<WorldEvent>): FarmWorldState {
        val gate = world.gates.firstOrNull { it.id == gateId } ?: return world
        val newGate = gate.copy(isOpen = !gate.isOpen)
        events += WorldEvent.GateToggled(gateId, newGate.isOpen)
        events.sound(SoundCue.TOOL_SELECT, gate.position, 0.6f)
        return world.copy(gates = world.gates.map { if (it.id == gateId) newGate else it })
    }

    private fun toggleWheelbarrow(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val wb = world.wheelbarrow
        val pushing = !wb.isBeingPushed
        events.sound(SoundCue.TOOL_SELECT, wb.position, 0.5f)
        val facingVec = when (world.player.facing) {
            Facing.UP -> Vector2(0f, -1f)
            Facing.DOWN -> Vector2(0f, 1f)
            Facing.LEFT -> Vector2(-1f, 0f)
            Facing.RIGHT -> Vector2(1f, 0f)
        }
        val newPos = if (pushing) world.player.position + facingVec * 0.95f else wb.position
        return world.copy(
            wheelbarrow = wb.copy(isBeingPushed = pushing, position = newPos),
            player = world.player.startAction(ToolType.HANDS, wb.position, 0.2f),
        )
    }

    private fun loadWheelbarrow(world: FarmWorldState, item: ItemType, events: MutableList<WorldEvent>): FarmWorldState {
        if (world.player.inventory.count(item) <= 0) return world
        if (world.wheelbarrow.isFull) return world
        val currentCount = world.wheelbarrow.cargo[item] ?: 0
        val newCargo = world.wheelbarrow.cargo + (item to currentCount + 1)
        events.sound(SoundCue.ITEM_PICKUP, world.wheelbarrow.position, 0.6f)
        return world.copy(
            wheelbarrow = world.wheelbarrow.copy(cargo = newCargo),
            player = world.player.copy(inventory = world.player.inventory.remove(item, 1)),
        )
    }

    private fun sellWheelbarrowCargo(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val wb = world.wheelbarrow
        if (wb.cargo.isEmpty()) return world
        var totalGain = 0
        wb.cargo.forEach { (item, count) ->
            val price = when (item) {
                ItemType.EGG -> 12
                ItemType.EGG_CARTON -> 90
                ItemType.CARROT -> 8
                ItemType.VEGGIE_BOX -> 55
                ItemType.WHEAT -> 10
                ItemType.FLOUR -> 25
                ItemType.DOUGH -> 50
                ItemType.BREAD -> 140
                ItemType.MILK -> 35
                ItemType.CHEESE -> 250
                ItemType.MEAT -> 120
                ItemType.FISH -> 25
                ItemType.FISH_FILLET -> 150
                ItemType.WOOD -> 5
                ItemType.STONE -> 5
                else -> 10
            }
            totalGain += price * count
        }
        events.sound(SoundCue.COINS, wb.position)
        events += WorldEvent.WheelbarrowCargoSold(totalGain)
        return world.copy(
            wheelbarrow = wb.copy(cargo = emptyMap()),
            player = world.player.copy(inventory = world.player.inventory.addCoins(totalGain)),
        )
    }

    private fun depositAllToBarn(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val playerInv = world.player.inventory
        if (playerInv.items.isEmpty()) return world
        val newStorage = world.barn.storage.toMutableMap()
        playerInv.items.forEach { (item, count) ->
            if (item != ItemType.GATE) {
                newStorage[item] = (newStorage[item] ?: 0) + count
            }
        }
        val remainingItems = playerInv.items.filterKeys { it == ItemType.GATE }
        events.sound(SoundCue.BARN_DEPOSIT, world.barn.doorPosition)
        events.add(WorldEvent.BarnStorageChanged(newStorage.values.sum()))
        return world.copy(
            barn = world.barn.copy(storage = newStorage),
            player = world.player.copy(inventory = playerInv.copy(items = remainingItems)),
        )
    }

    private fun depositItemToBarn(world: FarmWorldState, item: ItemType, events: MutableList<WorldEvent>): FarmWorldState {
        if (world.player.inventory.count(item) <= 0) return world
        val count = world.player.inventory.count(item)
        val newStorage = world.barn.storage.toMutableMap()
        newStorage[item] = (newStorage[item] ?: 0) + count
        events.sound(SoundCue.BARN_DEPOSIT, world.barn.doorPosition)
        events.add(WorldEvent.BarnStorageChanged(newStorage.values.sum()))
        return world.copy(
            barn = world.barn.copy(storage = newStorage),
            player = world.player.copy(inventory = world.player.inventory.remove(item, count)),
        )
    }

    private fun unloadWheelbarrowItem(world: FarmWorldState, item: ItemType, events: MutableList<WorldEvent>): FarmWorldState {
        val wb = world.wheelbarrow
        val current = wb.cargo[item] ?: 0
        if (current <= 0) return world
        val newCargo = wb.cargo.toMutableMap()
        if (current <= 1) {
            newCargo.remove(item)
        } else {
            newCargo[item] = current - 1
        }
        events.sound(SoundCue.ITEM_PICKUP, wb.position, 0.6f)
        return world.copy(
            wheelbarrow = wb.copy(cargo = newCargo),
            player = world.player.copy(inventory = world.player.inventory.add(item, 1)),
        )
    }

    private fun unloadWheelbarrowToBarn(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val wb = world.wheelbarrow
        if (wb.cargo.isEmpty()) return world
        val newStorage = world.barn.storage.toMutableMap()
        var newStoredEggs = world.barn.storedEggs
        wb.cargo.forEach { (item, count) ->
            if (item == ItemType.EGG) {
                newStoredEggs += count
            } else {
                newStorage[item] = (newStorage[item] ?: 0) + count
            }
        }
        events.sound(SoundCue.BARN_DEPOSIT, world.barn.doorPosition)
        events.add(WorldEvent.BarnStorageChanged(newStorage.values.sum()))
        return world.copy(
            barn = world.barn.copy(storage = newStorage, storedEggs = newStoredEggs),
            wheelbarrow = wb.copy(cargo = emptyMap()),
        )
    }

    private fun withdrawBarnEggs(world: FarmWorldState, count: Int, events: MutableList<WorldEvent>): FarmWorldState {
        val stored = world.barn.storedEggs
        if (stored <= 0) return world
        val withdrawCount = minOf(stored, if (count <= 0) stored else count)
        val newStored = stored - withdrawCount
        events.sound(SoundCue.ITEM_PICKUP, world.player.position)
        return world.copy(
            barn = world.barn.copy(storedEggs = newStored),
            player = world.player.copy(inventory = world.player.inventory.add(ItemType.EGG, withdrawCount)),
        )
    }

    private fun withdrawBarnItem(world: FarmWorldState, item: ItemType, amount: Int, events: MutableList<WorldEvent>): FarmWorldState {
        val stored = world.barn.storage[item] ?: 0
        if (stored <= 0) return world
        val withdrawCount = minOf(stored, if (amount <= 0) stored else amount)
        val newStorage = world.barn.storage.toMutableMap()
        if (stored - withdrawCount <= 0) {
            newStorage.remove(item)
        } else {
            newStorage[item] = stored - withdrawCount
        }
        events.sound(SoundCue.ITEM_PICKUP, world.player.position)
        events.add(WorldEvent.BarnStorageChanged(newStorage.values.sum()))
        return world.copy(
            barn = world.barn.copy(storage = newStorage),
            player = world.player.copy(inventory = world.player.inventory.add(item, withdrawCount)),
        )
    }

    private fun handleNudgeStructure(world: FarmWorldState, dx: Int, dy: Int, events: MutableList<WorldEvent>): FarmWorldState {
        val selectedId = world.selectedMoveStructureId ?: return world
        if (selectedId == -1) {
            val barnW = world.barn.collider.width
            val barnH = world.barn.collider.height
            val newLeft = world.barn.collider.left + dx
            val newTop = world.barn.collider.top + dy
            val newCollider = CollisionRect(newLeft, newTop, newLeft + barnW, newTop + barnH)
            if (newCollider.left < 4.5f || newCollider.right > 59.5f || newCollider.top < 4.5f || newCollider.bottom > 42.5f) return world
            if (world.waterBodies.any { rectIntersectsShape(newCollider, it.shape) }) return world
            if (world.factories.any { newCollider.intersects(it.collider) }) return world
            val newDoor = Vector2(newLeft + barnW * 0.5f, newCollider.bottom + 0.55f)
            events.sound(SoundCue.CONSTRUCTION_HAMMER, newCollider.center)
            events += WorldEvent.BarnMoved(newCollider)
            return world.copy(barn = world.barn.copy(collider = newCollider, doorPosition = newDoor))
        } else {
            val factory = world.factories.firstOrNull { it.id == selectedId } ?: return world
            val newCellX = factory.cellX + dx
            val newCellY = factory.cellY + dy
            val newCollider = CollisionRect(newCellX.toFloat(), newCellY.toFloat(), (newCellX + factory.widthCells).toFloat(), (newCellY + factory.heightCells).toFloat())
            if (newCollider.left < 4.5f || newCollider.right > 59.5f || newCollider.top < 4.5f || newCollider.bottom > 42.5f) return world
            if (world.waterBodies.any { rectIntersectsShape(newCollider, it.shape) }) return world
            if (newCollider.intersects(world.barn.collider)) return world
            if (world.factories.any { it.id != factory.id && newCollider.intersects(it.collider) }) return world
            events.sound(SoundCue.CONSTRUCTION_HAMMER, newCollider.center)
            events += WorldEvent.FactoryMoved(factory.id, newCellX, newCellY)
            val updated = world.factories.map { if (it.id == factory.id) it.copy(cellX = newCellX, cellY = newCellY) else it }
            return world.copy(factories = updated)
        }
    }

    private fun handleRotateStructure(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val selectedId = world.selectedMoveStructureId ?: return world
        if (selectedId == -1) return world
        val factory = world.factories.firstOrNull { it.id == selectedId } ?: return world
        val newOrient = factory.orientation.next()
        val newW = factory.heightCells
        val newH = factory.widthCells
        val newCollider = CollisionRect(factory.cellX.toFloat(), factory.cellY.toFloat(), (factory.cellX + newW).toFloat(), (factory.cellY + newH).toFloat())
        if (newCollider.left < 4.5f || newCollider.right > 59.5f || newCollider.top < 4.5f || newCollider.bottom > 42.5f) return world
        if (world.waterBodies.any { rectIntersectsShape(newCollider, it.shape) }) return world
        if (newCollider.intersects(world.barn.collider)) return world
        if (world.factories.any { it.id != factory.id && newCollider.intersects(it.collider) }) return world
        events.sound(SoundCue.CONSTRUCTION_HAMMER, newCollider.center)
        val updated = world.factories.map {
            if (it.id == factory.id) it.copy(orientation = newOrient, widthCells = newW, heightCells = newH) else it
        }
        return world.copy(factories = updated)
    }

    private fun throwActiveItem(world: FarmWorldState, events: MutableList<WorldEvent>): FarmWorldState {
        val active = world.player.activeItemId ?: return world
        if (world.player.inventory.count(active) <= 0) return world
        if (active != ItemType.EGG && active != ItemType.STONE) return world
        val dir = when (world.player.facing) {
            Facing.UP -> Vector2(0f, -1f)
            Facing.DOWN -> Vector2(0f, 1f)
            Facing.LEFT -> Vector2(-1f, 0f)
            Facing.RIGHT -> Vector2(1f, 0f)
        }
        val startPos = world.player.position + dir * 0.5f
        val velocity = dir * 9.5f
        val projectile = ThrownProjectileState(
            id = world.nextEntityId,
            item = active,
            position = startPos,
            velocity = velocity,
            height = 0.4f,
            verticalVelocity = 2.8f,
            lifeSeconds = 0f,
            bounces = if (active == ItemType.STONE) 3 else 0,
        )
        events.sound(SoundCue.ANIMAL_THROW, startPos, 0.7f)
        events.add(WorldEvent.ItemThrown(active, startPos, velocity))
        return world.copy(
            projectiles = world.projectiles + projectile,
            nextEntityId = world.nextEntityId + 1,
            player = world.player.startAction(ToolType.HANDS, startPos + dir * 2f, 0.25f).copy(
                inventory = world.player.inventory.remove(active, 1),
            ),
        )
    }

    private fun updateProjectiles(world: FarmWorldState, delta: Float, events: MutableList<WorldEvent>): FarmWorldState {
        if (world.projectiles.isEmpty()) return world
        val chickens = world.chickens.toMutableList()
        val ripples = world.ripples.toMutableList()
        var nextId = world.nextEntityId
        val remainingProjectiles = mutableListOf<ThrownProjectileState>()

        for (proj in world.projectiles) {
            val newLife = proj.lifeSeconds + delta
            val newPos = proj.position + proj.velocity * delta
            val newVVel = proj.verticalVelocity - 9.8f * delta
            val newHeight = (proj.height + newVVel * delta).coerceAtLeast(0f)
            val inWater = world.waterBodies.any { it.shape.contains(newPos) }

            if (inWater && proj.item == ItemType.STONE && proj.bounces > 0 && (newHeight <= 0.05f || newLife >= 0.2f)) {
                ripples.add(WaterRippleState(id = nextId++, position = newPos, radius = 0.2f, maxRadius = 1.3f, progress = 0f))
                events.sound(SoundCue.WATER_BLOCKED, newPos, 0.5f)
                events.add(WorldEvent.StoneSkipped(newPos, proj.bounces))
                val newVel = proj.velocity * 0.75f
                remainingProjectiles.add(proj.copy(
                    position = newPos,
                    velocity = newVel,
                    height = 0.35f,
                    verticalVelocity = 2.2f,
                    lifeSeconds = 0f,
                    bounces = proj.bounces - 1,
                ))
            } else if (newHeight <= 0f || newLife >= 0.7f || (inWater && proj.item != ItemType.STONE) || (inWater && proj.bounces <= 0)) {
                if (inWater) {
                    ripples.add(WaterRippleState(id = nextId++, position = newPos, radius = 0.2f, maxRadius = 1.5f, progress = 0f))
                    events.sound(SoundCue.WATER_BLOCKED, newPos, 0.6f)
                } else if (proj.item == ItemType.EGG) {
                    events.sound(SoundCue.ROCK_BREAK, newPos, 0.4f)
                    if (random.nextFloat() < 0.10f && chickens.size < config.maxChickenPopulation) {
                        val chick = ChickenState(
                            id = nextId++,
                            name = "Pollito #${nextId % 100}",
                            position = newPos,
                            lifeStage = ChickenLifeStage.CHICK,
                            eggTimer = 180f,
                        )
                        chickens.add(chick)
                        events.sound(SoundCue.CHICKEN_CLUCK, newPos)
                    }
                }
            } else {
                remainingProjectiles.add(proj.copy(
                    position = newPos,
                    height = newHeight,
                    verticalVelocity = newVVel,
                    lifeSeconds = newLife,
                ))
            }
        }
        return world.copy(
            projectiles = remainingProjectiles,
            ripples = ripples,
            chickens = chickens,
            nextEntityId = nextId,
        )
    }

    private fun updateRipples(world: FarmWorldState, delta: Float): FarmWorldState {
        if (world.ripples.isEmpty()) return world
        val updated = world.ripples.mapNotNull {
            val newProgress = it.progress + delta / 1.0f
            if (newProgress >= 1f) null
            else {
                val newRadius = it.radius + delta * 1.1f
                it.copy(
                    radius = newRadius.coerceAtMost(it.maxRadius),
                    progress = newProgress,
                )
            }
        }
        return world.copy(ripples = updated)
    }

    companion object {
        private const val SECONDS_PER_DAY = 86_400f
        private const val TWO_PI = (PI * 2.0).toFloat()
        private const val FARM_FENCE_LEFT = 4f
        private const val FARM_FENCE_TOP = 4f
        private const val FARM_FENCE_RIGHT = 60f
        private const val FARM_FENCE_BOTTOM = 43f
        val TRUCK_START_POSITION = Vector2(67f, 44.5f)
        val TRUCK_PARKED_POSITION = Vector2(35f, 44.5f)
        private val AVOIDANCE_ANGLES = listOf(0.62f, -0.62f, 1.2f, -1.2f, 2.2f)
        private val AVOIDANCE_ANGLES_REVERSED = AVOIDANCE_ANGLES.reversed()
    }
}

private fun PlayerState.startAction(
    tool: ToolType,
    target: Vector2,
    duration: Float,
): PlayerState = copy(
    isMoving = false,
    actionCooldown = duration,
    actionAnimation = PlayerActionAnimationState(
        tool = tool,
        targetPosition = target,
        durationSeconds = duration,
    ),
)

private fun ItemType.equipmentTool(): ToolType = when (this) {
    ItemType.SEED, ItemType.WHEAT_SEED -> ToolType.SEED_BAG
    ItemType.FENCE -> ToolType.FENCE
    ItemType.GATE -> ToolType.GATE
    ItemType.MILK_PAIL -> ToolType.MILK_PAIL
    else -> ToolType.HANDS
}

private fun ItemType.matchesTool(tool: ToolType): Boolean = when (tool) {
    ToolType.SEED_BAG -> this == ItemType.SEED || this == ItemType.WHEAT_SEED
    ToolType.FENCE -> this == ItemType.FENCE
    ToolType.GATE -> this == ItemType.GATE
    ToolType.MILK_PAIL -> this == ItemType.MILK_PAIL
    ToolType.HANDS -> this !in setOf(
        ItemType.SEED,
        ItemType.WHEAT_SEED,
        ItemType.FENCE,
        ItemType.GATE,
        ItemType.MILK_PAIL,
    )
    else -> false
}

private fun StaticObstacleState.anchorPosition(): Vector2 = when (val shape = collider) {
    is CollisionCircle -> shape.center
    is CollisionRect -> shape.center
}


private fun facingVector(facing: Facing): Vector2 = when (facing) {
    Facing.UP -> Vector2.UP
    Facing.DOWN -> Vector2.DOWN
    Facing.LEFT -> Vector2.LEFT
    Facing.RIGHT -> Vector2.RIGHT
}

internal fun rectIntersectsShape(rect: CollisionRect, shape: CollisionShape): Boolean = when (shape) {
    is CollisionCircle -> rect.intersects(shape)
    is CollisionRect -> rect.intersects(shape)
}

private fun cropStageFor(progress: Float): CropStage = when {
    progress >= 1f -> CropStage.MATURE
    progress >= 0.62f -> CropStage.LEAFY
    progress >= 0.24f -> CropStage.SPROUT
    else -> CropStage.SEED
}

private fun TerrainType.footstepCue(): SoundCue = when (this) {
    TerrainType.GRASS -> SoundCue.FOOTSTEP_GRASS
    TerrainType.DIRT, TerrainType.WOOD -> SoundCue.FOOTSTEP_DIRT
    TerrainType.SAND -> SoundCue.FOOTSTEP_SAND
    TerrainType.WATER -> SoundCue.WATER_BLOCKED
}

private fun facingFor(direction: Vector2, fallback: Facing): Facing {
    if (direction.lengthSquared() <= 0.00001f) return fallback
    return if (abs(direction.x) > abs(direction.y)) {
        if (direction.x < 0f) Facing.LEFT else Facing.RIGHT
    } else {
        if (direction.y < 0f) Facing.UP else Facing.DOWN
    }
}

private fun rotate(vector: Vector2, radians: Float): Vector2 {
    val cosine = cos(radians)
    val sine = sin(radians)
    return Vector2(vector.x * cosine - vector.y * sine, vector.x * sine + vector.y * cosine)
}

private fun lerp(start: Vector2, end: Vector2, amount: Float): Vector2 =
    start + (end - start) * amount.coerceIn(0f, 1f)

private fun wrapRadians(value: Float): Float {
    val twoPi = (PI * 2.0).toFloat()
    return if (value >= twoPi) value % twoPi else value
}

private fun clampCamera(camera: CameraState, bounds: CollisionRect): CameraState {
    val halfWidth = camera.baseViewportWorldSize.x / (camera.zoom * 2f)
    val halfHeight = camera.baseViewportWorldSize.y / (camera.zoom * 2f)
    val x = if (halfWidth * 2f >= bounds.width) bounds.center.x
    else camera.center.x.coerceIn(bounds.left + halfWidth, bounds.right - halfWidth)
    val y = if (halfHeight * 2f >= bounds.height) bounds.center.y
    else camera.center.y.coerceIn(bounds.top + halfHeight, bounds.bottom - halfHeight)
    return camera.copy(center = Vector2(x, y))
}

private inline fun List<TreeState>.replaceTreeById(id: Int, transform: (TreeState) -> TreeState): List<TreeState> =
    map { if (it.id == id) transform(it) else it }

private inline fun List<SoilPlotState>.replacePlotById(
    id: Int,
    transform: (SoilPlotState) -> SoilPlotState,
): List<SoilPlotState> = map { if (it.id == id) transform(it) else it }

private inline fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { current, item -> if (current == index) transform(item) else item }

fun toolPurchasePrice(tool: ToolType): Int = when (tool) {
    ToolType.AXE -> 120
    ToolType.PICKAXE -> 140
    ToolType.HOE -> 80
    ToolType.WATERING_CAN -> 70
    ToolType.FISHING_ROD -> 150
    ToolType.MACHETE -> 90
    ToolType.MILK_PAIL -> 90
    ToolType.ARCHITECT_PENCIL -> 100
    ToolType.BLUEPRINT_DAIRY -> 500
    ToolType.BLUEPRINT_SLAUGHTERHOUSE -> 450
    ToolType.BLUEPRINT_EGG_PACKER -> 350
    ToolType.BLUEPRINT_VEGGIE_PACKER -> 350
    ToolType.BLUEPRINT_BAKERY -> 550
    ToolType.BLUEPRINT_FISH_PROCESSOR -> 500
    ToolType.GATE -> 50
    ToolType.CARROT_BAIT, ToolType.FENCE, ToolType.HANDS, ToolType.PET_BALL,
    ToolType.SEED_BAG, ToolType.PATH_TOOL -> 50
}

fun defaultMainPathTiles(): List<PathTileState> = buildList {
    var id = 8_000
    for (cx in 30..33) {
        for (cy in 13..14) {
            add(PathTileState(id++, cx, cy, PathStyle.GRAVEL))
        }
    }
    for (cy in 15..43) {
        for (cx in 31..32) {
            add(PathTileState(id++, cx, cy, PathStyle.GRAVEL))
        }
    }
}

/** A roomy hand-authored test farm. Rendering can replace every visual without changing simulation. */
fun createDefaultFarmWorld(): FarmWorldState {
    val bounds = CollisionRect(0f, 0f, 64f, 48f)
    val barn = BarnState(
        collider = CollisionRect(26.75f, 4.5f, 37.25f, 13.2f),
        doorPosition = Vector2(32f, 13.75f),
    )
    val waterBodies = listOf(
        WaterBodyState(1, CollisionCircle(Vector2(17.2f, 23.7f), 6.3f), 0.4f),
        WaterBodyState(2, CollisionCircle(Vector2(51.2f, 8.8f), 3.3f), 2.1f),
        WaterBodyState(3, CollisionCircle(Vector2(11.9f, 23.9f), 3.5f), 1.2f),
        WaterBodyState(4, CollisionCircle(Vector2(24.1f, 24.2f), 4.8f), 2.8f),
    )
    val terrainRegions = listOf(
        TerrainRegion(1, TerrainType.SAND, CollisionCircle(Vector2(17.2f, 23.7f), 7.05f), 0.7f),
        TerrainRegion(2, TerrainType.SAND, CollisionCircle(Vector2(51.2f, 8.8f), 4f), 1.4f),
        TerrainRegion(20, TerrainType.WOOD, CollisionRect(30.8f, 12.6f, 33.2f, 14.6f), 0f),
    )
    val pathTiles = defaultMainPathTiles()
    val staticObstacles = buildList {
        add(StaticObstacleState(20, ObstacleKind.ROCK, CollisionCircle(Vector2(40.5f, 36f), 0.75f), 36.5f))
        add(StaticObstacleState(21, ObstacleKind.ROCK, CollisionCircle(Vector2(42.2f, 35.3f), 0.5f), 35.7f))
        var fenceId = 22
        // Perimeter boundary fences
        val fenceThickness = 0.35f
        val gateHalfWidth = 2.25f
        add(StaticObstacleState(fenceId++, ObstacleKind.FENCE, CollisionRect(4f, 4f - fenceThickness * 0.5f, 60f, 4f + fenceThickness * 0.5f), 4.2f))
        add(StaticObstacleState(fenceId++, ObstacleKind.FENCE, CollisionRect(4f - fenceThickness * 0.5f, 4f, 4f + fenceThickness * 0.5f, 43f), 43.1f))
        add(StaticObstacleState(fenceId++, ObstacleKind.FENCE, CollisionRect(60f - fenceThickness * 0.5f, 4f, 60f + fenceThickness * 0.5f, 43f), 43.1f))
        add(StaticObstacleState(fenceId++, ObstacleKind.FENCE, CollisionRect(4f, 43f - fenceThickness * 0.5f, 32f - gateHalfWidth, 43f + fenceThickness * 0.5f), 43.2f))
        add(StaticObstacleState(fenceId++, ObstacleKind.FENCE, CollisionRect(32f + gateHalfWidth, 43f - fenceThickness * 0.5f, 60f, 43f + fenceThickness * 0.5f), 43.2f))

        // Unit segments share exact endpoints, so the renderer can join them like Minecraft fences.
        for (cellX in 4 until 9) {
            val center = Vector2(cellX + 0.5f, 14.5f)
            add(
                StaticObstacleState(
                    fenceId++,
                    ObstacleKind.FENCE,
                    CollisionRect(center.x - 0.5f, center.y - 0.11f, center.x + 0.5f, center.y + 0.11f),
                    center.y + 0.11f,
                ),
            )
        }
        for (cellX in 14 until 20) {
            val center = Vector2(cellX + 0.5f, 14.5f)
            add(
                StaticObstacleState(
                    fenceId++,
                    ObstacleKind.FENCE,
                    CollisionRect(center.x - 0.5f, center.y - 0.11f, center.x + 0.5f, center.y + 0.11f),
                    center.y + 0.11f,
                ),
            )
        }
    }
    val treePositions = listOf(
        Vector2(7f, 8f), Vector2(7f, 17.5f), Vector2(7.2f, 33f), Vector2(7.3f, 39f),
        Vector2(19f, 7.2f), Vector2(22.5f, 8f), Vector2(42f, 7f), Vector2(46f, 7.2f),
        Vector2(56.5f, 16f), Vector2(56.4f, 23f), Vector2(56f, 31f), Vector2(55.5f, 38f),
        Vector2(12f, 39f), Vector2(18f, 39.5f), Vector2(23f, 38f), Vector2(42f, 39f),
        Vector2(48f, 39.5f), Vector2(52.5f, 36.5f), Vector2(19f, 13f),
    )
    val trees = treePositions.mapIndexed { index, position ->
        TreeState(id = 100 + index, position = position, windPhase = index * 0.73f)
    }
    val plots = emptyList<SoilPlotState>()
    val chickens = listOf(
        ChickenState(500, Vector2(18.4f, 14.8f), animationPhase = 0.2f, eggTimer = 8f, cluckTimer = 3f),
        ChickenState(501, Vector2(20.2f, 12.8f), animationPhase = 1.3f, eggTimer = 15f, cluckTimer = 7f),
        ChickenState(502, Vector2(18.7f, 10.6f), animationPhase = 2.7f, eggTimer = 24f, cluckTimer = 11f),
        ChickenState(503, Vector2(21.2f, 15.5f), animationPhase = 4.2f, eggTimer = 32f, cluckTimer = 15f),
    )
    val eggs = listOf(
        EggState(600, Vector2(19.2f, 16.1f), fertile = false, animationPhase = 0.3f),
        EggState(601, Vector2(20.8f, 14.2f), fertile = true, hatchAtSeconds = 75f, animationPhase = 1.5f),
    )
    val grassTufts = buildList {
        var id = 700
        for (row in 0 until 8) {
            for (column in 0 until 11) {
                val x = 7f + column * 4.7f + (row % 2) * 1.1f
                val y = 7f + row * 4.3f
                val position = Vector2(x, y)
                val isClear = position.x in 4.55f..59.45f && position.y in 4.55f..42.45f &&
                    waterBodies.none { it.shape.distanceTo(position) < 1f } &&
                    barn.collider.distanceTo(position) > 1f &&
                    pathTiles.none { it.collider.contains(position) } &&
                    !(position inPlotArea plots)
                if (isClear) add(GrassTuftState(id++, position, (row * 1.7f + column * 0.61f)))
            }
        }
    }
    val player = PlayerState(position = Vector2(32f, 20f))
    val initialGates = listOf(
        FenceGateState(id = 850, cellX = 31, cellY = 43, isOpen = false),
        FenceGateState(id = 851, cellX = 32, cellY = 43, isOpen = false),
    )
    val wheelbarrow = WheelbarrowState(position = Vector2(33f, 22f))
    return FarmWorldState(
        bounds = bounds,
        player = player,
        camera = CameraState(center = player.position),
        terrainRegions = terrainRegions,
        waterBodies = waterBodies,
        staticObstacles = staticObstacles,
        grassTufts = grassTufts,
        pathTiles = pathTiles,
        trees = trees,
        plots = plots,
        chickens = chickens,
        eggs = eggs,
        groundItems = emptyList(),
        barn = barn,
        truck = TruckState(position = FarmWorldEngine.TRUCK_START_POSITION, phaseTimer = 18f),
        gates = initialGates,
        wheelbarrow = wheelbarrow,
        nextEntityId = 1_000,
    )
}

private infix fun Vector2.inPlotArea(plots: List<SoilPlotState>): Boolean =
    plots.any { it.collider.expanded(0.4f).contains(this) }
