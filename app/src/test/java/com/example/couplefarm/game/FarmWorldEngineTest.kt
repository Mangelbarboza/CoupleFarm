package com.example.couplefarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmWorldEngineTest {

    @Test
    fun playerCannotEnterWaterOrStaticObstacle() {
        val base = quietWorld()
        val waterStart = Vector2(19.3f, 30f)
        val waterEngine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(position = waterStart),
                staticObstacles = emptyList(),
            ),
        )

        val waterFrame = waterEngine.update(
            0.2f,
            WorldInput(movement = Vector2.LEFT),
        )

        assertEquals(waterStart.x, waterFrame.state.player.position.x, 0.0001f)
        assertEquals(waterStart.y, waterFrame.state.player.position.y, 0.0001f)
        assertTrue(
            waterFrame.events.any {
                it is WorldEvent.Sound && it.cue == SoundCue.WATER_BLOCKED
            },
        )

        val rock = base.staticObstacles.first { it.kind == ObstacleKind.ROCK }
        val rockCircle = rock.collider as CollisionCircle
        val obstacleStart = rockCircle.center + Vector2(rockCircle.radius + 0.75f, 0f)
        val obstacleEngine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(position = obstacleStart),
                waterBodies = emptyList(),
                staticObstacles = listOf(rock),
            ),
        )

        val obstacleFrame = obstacleEngine.update(
            0.2f,
            WorldInput(movement = Vector2.LEFT),
        )

        assertEquals(obstacleStart.x, obstacleFrame.state.player.position.x, 0.0001f)
        assertEquals(obstacleStart.y, obstacleFrame.state.player.position.y, 0.0001f)
        assertTrue(
            obstacleFrame.events.any {
                it is WorldEvent.Sound && it.cue == SoundCue.COLLISION
            },
        )
    }

    @Test
    fun plotCanBeTilledPlantedWateredGrownAndHarvested() {
        val base = quietWorld()
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(31f, 33.5f),
                    facing = Facing.RIGHT,
                ),
            ),
            config = FarmWorldConfig(maxDeltaSeconds = 1f),
        )

        engine.update(
            0f,
            WorldInput(selectedTool = ToolType.HOE, actionPressed = true),
        )
        val tilled = engine.state.plots.single { it.soil == SoilState.TILLED }
        assertTrue(tilled.position.x > 31f)
        assertEquals(null, tilled.crop)

        clearActionCooldown(engine)
        engine.update(
            0f,
            WorldInput(selectedTool = ToolType.SEED_BAG, actionPressed = true),
        )
        assertEquals(CropStage.SEED, engine.state.plots.first { it.id == tilled.id }.crop?.stage)
        assertEquals(11, engine.state.player.inventory.count(ItemType.SEED))

        clearActionCooldown(engine)
        engine.update(
            0f,
            WorldInput(selectedTool = ToolType.WATERING_CAN, actionPressed = true),
        )
        assertEquals(1f, engine.state.plots.first { it.id == tilled.id }.moisture, 0.0001f)

        advance(engine, 140f)
        val mature = engine.state.plots.first { it.id == tilled.id }
        assertEquals(CropStage.MATURE, mature.crop?.stage)

        clearActionCooldown(engine)
        val harvestFrame = engine.update(
            0f,
            WorldInput(selectedTool = ToolType.HANDS, actionPressed = true),
        )
        val harvested = harvestFrame.state.plots.first { it.id == tilled.id }
        assertEquals(null, harvested.crop)
        assertEquals(CropType.CARROT.harvestYield, harvestFrame.state.player.inventory.count(ItemType.CARROT))
        assertTrue(
            harvestFrame.events.any {
                it is WorldEvent.ItemCollected &&
                    it.item == ItemType.CARROT &&
                    it.amount == CropType.CARROT.harvestYield
            },
        )
    }

    @Test
    fun threeAxeHitsFellTreeAndCreateWoodDrop() {
        val base = quietWorld()
        val target = createDefaultFarmWorld().trees.first().copy(health = 3)
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(position = target.position + Vector2(0f, 1.7f)),
                trees = listOf(target),
            ),
            config = FarmWorldConfig(treeFallSeconds = 0.2f, maxDeltaSeconds = 0.25f),
        )

        repeat(3) { hit ->
            engine.update(
                0f,
                WorldInput(selectedTool = ToolType.AXE, actionPressed = true),
            )
            val tree = engine.state.trees.single()
            assertEquals(2 - hit, tree.health)
            if (hit < 2) {
                assertEquals(TreeLifeState.STANDING, tree.lifeState)
                clearActionCooldown(engine)
            }
        }

        assertEquals(TreeLifeState.FALLING, engine.state.trees.single().lifeState)
        val fallFrame = engine.update(0.25f)
        assertTrue(fallFrame.state.trees.none { it.id == target.id })
        val wood = fallFrame.state.groundItems.single { it.item == ItemType.WOOD }
        assertEquals(5, wood.amount)
        assertTrue(fallFrame.events.any { it is WorldEvent.TreeFelled && it.treeId == target.id })
    }

    @Test
    fun stumpTakesTwoAxeHitsThenDisappearsAndDropsWood() {
        val base = quietWorld()
        val stump = TreeState(
            id = 77,
            position = Vector2(30f, 30f),
            lifeState = TreeLifeState.STUMP,
            health = 2,
            fallProgress = 1f,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(position = Vector2(30f, 31.4f), facing = Facing.UP),
                trees = listOf(stump),
            ),
            config = FarmWorldConfig(stumpAxeHits = 2, stumpWoodYield = 2),
        )

        engine.update(0f, WorldInput(selectedTool = ToolType.AXE, actionPressed = true))
        assertEquals(1, engine.state.trees.single().health)
        clearActionCooldown(engine)
        val removed = engine.update(0f, WorldInput(actionPressed = true))

        assertTrue(removed.state.trees.isEmpty())
        assertEquals(2, removed.state.groundItems.single { it.item == ItemType.WOOD }.amount)
        assertTrue(removed.events.any { it is WorldEvent.TreeStumpRemoved && it.treeId == stump.id })
        assertEquals(ToolType.AXE, removed.state.player.actionAnimation?.tool)
    }

    @Test
    fun hoeSnapsTwoByTwoPlotToInvisibleGridAndRejectsWaterAndOverlap() {
        val base = quietWorld().copy(plots = emptyList(), staticObstacles = emptyList())
        val player = base.player.copy(position = Vector2(30f, 35f), facing = Facing.RIGHT)
        val engine = FarmWorldEngine(initialState = base.copy(player = player))

        val created = engine.update(0f, WorldInput(selectedTool = ToolType.HOE, actionPressed = true))
        val plot = created.state.plots.single()
        assertEquals(31.5f, plot.position.x, 0.0001f)
        assertEquals(35.5f, plot.position.y, 0.0001f)
        assertEquals(1f, plot.size, 0.0001f)
        assertEquals(SoilState.TILLED, plot.soil)
        assertEquals(created.state.nextEntityId - 1, plot.id)

        clearActionCooldown(engine)
        engine.update(0f, WorldInput(actionPressed = true))
        assertEquals("overlapping press must not make a second plot", 1, engine.state.plots.size)

        val blockedWorld = base.copy(
            player = player,
            waterBodies = listOf(
                WaterBodyState(9, CollisionCircle(Vector2(31.5f, 35.5f), 0.9f), 0f),
            ),
        )
        val blockedEngine = FarmWorldEngine(initialState = blockedWorld)
        blockedEngine.update(0f, WorldInput(selectedTool = ToolType.HOE, actionPressed = true))
        assertTrue("water cannot be converted into soil", blockedEngine.state.plots.isEmpty())
    }

    @Test
    fun pickaxeBreaksRockAndDropsStone() {
        val base = quietWorld()
        val rock = StaticObstacleState(
            id = 88,
            kind = ObstacleKind.ROCK,
            collider = CollisionCircle(Vector2(31.2f, 30f), 0.55f),
            sortY = 30.4f,
            hitPoints = 2,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(position = Vector2(30f, 30f), facing = Facing.RIGHT),
                staticObstacles = listOf(rock),
            ),
            config = FarmWorldConfig(rockPickaxeHits = 2, rockStoneYield = 3),
        )

        val firstHit = engine.update(
            0f,
            WorldInput(selectedTool = ToolType.PICKAXE, actionPressed = true),
        )
        assertEquals(1, firstHit.state.staticObstacles.single().hitPoints)
        assertTrue(firstHit.events.any { it is WorldEvent.RockHit && it.health == 1 })
        assertEquals(ToolType.PICKAXE, firstHit.state.player.actionAnimation?.tool)

        clearActionCooldown(engine)
        val broken = engine.update(0f, WorldInput(actionPressed = true))
        assertTrue(broken.state.staticObstacles.none { it.id == rock.id })
        assertEquals(3, broken.state.groundItems.single { it.item == ItemType.STONE }.amount)
        assertTrue(broken.events.any { it is WorldEvent.RockBroken && it.obstacleId == rock.id })
        assertTrue(
            broken.events.any { it is WorldEvent.Sound && it.cue == SoundCue.ROCK_BREAK },
        )
    }

    @Test
    fun rocksRespawnFromSerializableTimerAndRespectPopulationCap() {
        val base = quietWorld().copy(
            plots = emptyList(),
            staticObstacles = emptyList(),
            waterBodies = emptyList(),
            rockSpawning = RockSpawnState(secondsUntilNext = 0f),
        )
        val engine = FarmWorldEngine(
            initialState = base,
            seed = 42,
            config = FarmWorldConfig(
                rockMinSpawnSeconds = 0f,
                rockMaxSpawnSeconds = 0f,
                maxWorldRocks = 2,
                rockSpawnAttempts = 64,
            ),
        )

        val first = engine.update(0f)
        assertEquals(1, first.state.staticObstacles.count { it.kind == ObstacleKind.ROCK })
        assertEquals(1, first.state.rockSpawning.totalSpawned)
        assertTrue(first.events.any { it is WorldEvent.RockSpawned })

        engine.update(0f)
        engine.update(0f)
        assertEquals(2, engine.state.staticObstacles.count { it.kind == ObstacleKind.ROCK })
        assertEquals(2, engine.state.rockSpawning.totalSpawned)
    }

    @Test
    fun emoteAndToolActionExposeTimedAnimationState() {
        val base = quietWorld().copy(staticObstacles = emptyList())
        val engine = FarmWorldEngine(
            initialState = base,
            config = FarmWorldConfig(emoteDurationSeconds = 0.5f, maxDeltaSeconds = 0.3f),
        )

        engine.update(0f, WorldInput(emote = EmoteType.HEART))
        assertEquals(EmoteType.HEART, engine.state.player.activeEmote?.type)
        engine.update(0.3f)
        assertEquals(0.6f, engine.state.player.activeEmote?.progress ?: -1f, 0.0001f)
        engine.update(0.3f)
        assertEquals(null, engine.state.player.activeEmote)

        engine.update(0f, WorldInput(selectedTool = ToolType.PICKAXE, actionPressed = true))
        val animation = engine.state.player.actionAnimation
        assertNotNull(animation)
        assertEquals(ToolType.PICKAXE, animation?.tool)
        assertEquals(0f, animation?.progress ?: -1f, 0.0001f)
    }

    @Test
    fun adultChickenLaysFertileEggAndEggHatchesIntoChick() {
        val base = quietWorld()
        val adult = ChickenState(
            id = 40,
            position = Vector2(28f, 10f),
            lifeStage = ChickenLifeStage.ADULT,
            behavior = ChickenBehavior.IDLE,
            behaviorTimer = 100f,
            eggTimer = 0.05f,
            cluckTimer = 100f,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(chickens = listOf(adult), eggs = emptyList()),
            seed = 7,
            config = FarmWorldConfig(
                chickenMinEggSeconds = 10f,
                chickenMaxEggSeconds = 10f,
                eggHatchChance = 1f,
                eggHatchSeconds = 0.2f,
                maxDeltaSeconds = 0.1f,
            ),
        )

        val layingFrame = engine.update(0.1f)
        val laidEgg = layingFrame.state.eggs.single()
        assertTrue(laidEgg.fertile)
        assertFalse(layingFrame.events.any { it is WorldEvent.Sound })

        engine.update(0.1f)
        val hatchFrame = engine.update(0.1f)
        assertFalse(hatchFrame.state.eggs.any { it.id == laidEgg.id })
        val chick = hatchFrame.state.chickens.singleOrNull { it.lifeStage == ChickenLifeStage.CHICK }
        assertNotNull(chick)
        assertEquals(2, hatchFrame.state.chickens.size)
        assertTrue(
            hatchFrame.events.any {
                it is WorldEvent.EggHatched && it.eggId == laidEgg.id && it.chickenId == chick?.id
            },
        )
        assertFalse(hatchFrame.events.any { it is WorldEvent.Sound })
    }

    @Test
    fun eggsDepositInBarnAndParkedTruckSellsThem() {
        val base = quietWorld()
        val eggCount = 5
        val initialCoins = 3
        val price = 9
        val inventory = InventoryState(
            items = mapOf(ItemType.EGG to eggCount),
            coins = initialCoins,
        )
        val parkedTruck = TruckState(
            phase = TruckPhase.PARKED,
            position = Vector2(59.5f, 43f),
            phaseTimer = 10f,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = base.barn.doorPosition,
                    inventory = inventory,
                ),
                barn = base.barn.copy(storedEggs = 0),
                truck = parkedTruck,
            ),
            config = FarmWorldConfig(eggSalePrice = price, maxDeltaSeconds = 0.25f),
        )

        val depositFrame = engine.update(
            0f,
            WorldInput(selectedTool = ToolType.HANDS, actionPressed = true),
        )
        assertEquals(eggCount, depositFrame.state.barn.storedEggs)
        assertEquals(0, depositFrame.state.player.inventory.count(ItemType.EGG))
        assertTrue(
            depositFrame.events.any {
                it is WorldEvent.EggsStored && it.amount == eggCount && it.totalStored == eggCount
            },
        )

        val saleFrame = engine.update(0.1f)
        assertEquals(0, saleFrame.state.barn.storedEggs)
        assertEquals(initialCoins + eggCount * price, saleFrame.state.player.inventory.coins)
        assertEquals(eggCount, saleFrame.state.truck.eggsLoaded)
        assertTrue(saleFrame.state.truck.saleCompletedThisVisit)
        assertTrue(
            saleFrame.events.any {
                it is WorldEvent.TruckSale && it.eggsSold == eggCount && it.coinsEarned == eggCount * price
            },
        )
    }

    @Test
    fun purchasedPetKeepsBreedAndStartsAsBaby() {
        val base = quietWorld()
        val engine = FarmWorldEngine(
            initialState = base.copy(player = base.player.copy(inventory = InventoryState(coins = 1_000))),
            config = FarmWorldConfig(courierWalkSpeed = 100f),
        )

        engine.update(0f, WorldInput(animalPurchase = AnimalPurchaseRequest(DomesticAnimalType.DOG, breedIndex = 2)))
        advance(engine, engine.config.truckArrivalSeconds + 3f)

        val dog = engine.state.animals.single()
        assertEquals(DomesticAnimalType.DOG, dog.type)
        assertEquals(2, dog.breedIndex)
        assertEquals(AnimalLifeStage.BABY, dog.lifeStage)
        assertEquals(580, engine.state.player.inventory.coins)
    }

    @Test
    fun nearbyDogScaresHuntingFox() {
        val base = quietWorld()
        val dog = DomesticAnimalState(
            id = 900,
            name = "Rex",
            type = DomesticAnimalType.DOG,
            position = Vector2(30f, 30f),
            lifeStage = AnimalLifeStage.ADULT,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                animals = listOf(dog),
                fox = FoxState(phase = FoxPhase.HUNTING, position = Vector2(30.7f, 30f)),
            ),
        )

        val frame = engine.update(0.05f)

        assertEquals(FoxPhase.FLEEING, frame.state.fox.phase)
        assertTrue(frame.events.any { it is WorldEvent.FoxScaredByDog && it.dogId == dog.id })
    }

    @Test
    fun fenceBundleCanBeBoughtAndPlaced() {
        val base = quietWorld()
        val engine = FarmWorldEngine(
            initialState = base.copy(
                staticObstacles = emptyList(),
                player = base.player.copy(position = Vector2(30f, 30f), inventory = InventoryState(coins = 500)),
            ),
            config = FarmWorldConfig(courierWalkSpeed = 100f),
        )
        engine.update(0f, WorldInput(purchaseFences = 8))
        advance(engine, engine.config.truckArrivalSeconds + 3f)
        engine.update(0f, WorldInput(selectedTool = ToolType.FENCE, actionPressed = true))

        assertEquals(7, engine.state.player.inventory.count(ItemType.FENCE))
        assertTrue(engine.state.staticObstacles.any { it.kind == ObstacleKind.FENCE })
    }

    @Test
    fun adjacentPlotsShareAnExactGridEdgeWithoutGap() {
        val base = quietWorld().copy(
            waterBodies = emptyList(),
            staticObstacles = emptyList(),
            plots = listOf(
                SoilPlotState(70, Vector2(31.5f, 35.5f), size = 1f, soil = SoilState.TILLED),
            ),
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(position = Vector2(31.3f, 35.5f), facing = Facing.RIGHT),
            ),
        )

        engine.update(0f, WorldInput(selectedTool = ToolType.HOE, actionPressed = true))

        val plots = engine.state.plots.sortedBy { it.position.x }
        assertEquals(2, plots.size)
        assertEquals(plots[0].collider.right, plots[1].collider.left, 0.0001f)
        assertEquals(32.5f, plots[1].position.x, 0.0001f)
    }

    @Test
    fun gridFenceTouchesNeighborAndAxeReturnsItToInventory() {
        val existing = StaticObstacleState(
            id = 71,
            kind = ObstacleKind.FENCE,
            collider = CollisionRect(31f, 30.39f, 32f, 30.61f),
            sortY = 30.61f,
        )
        val base = quietWorld().copy(
            waterBodies = emptyList(),
            staticObstacles = listOf(existing),
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(31.65f, 29.66f),
                    facing = Facing.DOWN,
                    movementDirection = Vector2(1f, 1f).normalized(),
                    inventory = InventoryState(items = mapOf(ItemType.FENCE to 2)),
                ),
            ),
        )

        engine.update(0f, WorldInput(selectedTool = ToolType.FENCE, actionPressed = true))
        val fences = engine.state.staticObstacles.filter { it.kind == ObstacleKind.FENCE }
            .sortedBy { shapeCenterForTest(it.collider).x }
        assertEquals(2, fences.size)
        assertEquals(
            (fences[0].collider as CollisionRect).right,
            (fences[1].collider as CollisionRect).left,
            0.0001f,
        )
        assertEquals(1, engine.state.player.inventory.count(ItemType.FENCE))

        clearActionCooldown(engine)
        val removed = engine.update(0f, WorldInput(selectedTool = ToolType.AXE, actionPressed = true))
        assertEquals(1, removed.state.staticObstacles.count { it.kind == ObstacleKind.FENCE })
        assertEquals(2, removed.state.player.inventory.count(ItemType.FENCE))
        assertTrue(removed.events.any { it is WorldEvent.FenceRemoved })
    }

    @Test
    fun stoppedPlayerOutsideRadiusMakesFollowingPetApproachUntilCompanionRadius() {
        val base = quietWorld()
        val dog = DomesticAnimalState(
            id = 910,
            name = "Rex",
            type = DomesticAnimalType.DOG,
            position = base.player.position + Vector2(-5f, 0f),
            velocity = Vector2.RIGHT,
            behavior = DomesticAnimalBehavior.FOLLOW_PLAYER,
            petFollowState = PetFollowState.FOLLOWING,
            lifeStage = AnimalLifeStage.ADULT,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(isMoving = false),
                animals = listOf(dog),
            ),
        )

        val frame = engine.update(0.1f, WorldInput(movement = Vector2.ZERO))
        val approaching = frame.state.animals.single()
        assertEquals(DomesticAnimalBehavior.FOLLOW_PLAYER, approaching.behavior)
        assertTrue("Pet should move right toward the player", approaching.position.x > dog.position.x)
    }

    @Test
    fun foxFaintDoesNotTeleportParkedBicycleOntoPlayer() {
        val base = quietWorld()
        val parkedPosition = Vector2(8f, 8f)
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(foxBites = 2, isMounted = false),
                bicycle = BicycleState(position = parkedPosition, owned = true, isMounted = false),
                fox = FoxState(
                    phase = FoxPhase.HUNTING,
                    position = base.player.position,
                    biteCooldown = 0f,
                ),
            ),
            config = FarmWorldConfig(foxSpeed = 0f, playerBitesBeforeFaint = 3),
        )

        val frame = engine.update(0.05f)
        assertTrue(frame.state.player.faintTimer > 0f)
        assertFalse(frame.state.player.isMounted)
        assertFalse(frame.state.bicycle.isMounted)
        assertEquals(parkedPosition, frame.state.bicycle.position)
    }

    @Test
    fun legacyMountedFlagsAreClearedWhilePlayerIsFainted() {
        val base = quietWorld()
        val frame = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(faintTimer = 2f, isMounted = true),
                bicycle = BicycleState(position = base.player.position, owned = true, isMounted = true),
            ),
        ).update(0f)

        assertFalse(frame.state.player.isMounted)
        assertFalse(frame.state.bicycle.isMounted)
    }

    @Test
    fun courierWalksSlowlyAndCanBeInterceptedWithHands() {
        val base = quietWorld()
        val truck = TruckState(
            phase = TruckPhase.PARKED,
            position = Vector2(59.5f, 43f),
            phaseTimer = 20f,
        )
        val order = DeliveryOrderState(
            id = 920,
            kind = DeliveryKind.SEEDS,
            amount = 4,
            secondsRemaining = 0f,
        )
        val courierExit = Vector2(57.05f, 43.45f)
        val slowEngine = FarmWorldEngine(
            initialState = base.copy(truck = truck, deliveries = listOf(order)),
            config = FarmWorldConfig(courierWalkSpeed = 1f),
        )

        val walking = slowEngine.update(0.25f).state.deliveries.single()
        assertEquals(CourierPhase.TO_BARN, walking.courierPhase)
        assertTrue(walking.courierPosition.distanceTo(courierExit) <= 0.251f)
        assertTrue(walking.courierPosition.distanceTo(base.barn.doorPosition) > 30f)

        val interceptEngine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = courierExit,
                    inventory = InventoryState(),
                ),
                truck = truck,
                deliveries = listOf(order),
            ),
        )
        val intercepted = interceptEngine.update(
            0f,
            WorldInput(selectedTool = ToolType.HANDS, actionPressed = true),
        )
        assertEquals(4, intercepted.state.player.inventory.count(ItemType.SEED))
        assertEquals(CourierPhase.RETURNING, intercepted.state.deliveries.single().courierPhase)
        assertTrue(intercepted.events.any { it is WorldEvent.OrderIntercepted && it.orderId == order.id })
    }

    @Test
    fun ambientCreatureSoundsOnlyInsideProximityRadius() {
        val base = quietWorld()
        val nearbyBird = AmbientCreatureState(
            id = 930,
            type = AmbientCreatureType.BIRD,
            position = base.player.position + Vector2(1f, 0f),
            velocity = Vector2.RIGHT,
            lifeTimer = 10f,
            movementTimer = 10f,
            soundCooldown = 0f,
        )
        val nearby = FarmWorldEngine(
            initialState = base.copy(
                ambientCreatures = listOf(nearbyBird),
                ambientCreatureSpawnTimer = 10_000f,
            ),
        ).update(0.1f)
        assertTrue(nearby.events.any { it is WorldEvent.Sound && it.cue == SoundCue.BIRD_CHIRP })

        val far = FarmWorldEngine(
            initialState = base.copy(
                ambientCreatures = listOf(nearbyBird.copy(position = Vector2(1f, 1f))),
                ambientCreatureSpawnTimer = 10_000f,
            ),
        ).update(0.1f)
        assertFalse(far.events.any {
            it is WorldEvent.Sound && it.cue in setOf(SoundCue.BIRD_CHIRP, SoundCue.BUTTERFLY_FLUTTER)
        })
    }

    @Test
    fun wheatSeedEquippedFromBackpackPlantsWheat() {
        val base = quietWorld()
        val plot = SoilPlotState(
            id = 940,
            position = Vector2(31f, 30f),
            soil = SoilState.TILLED,
            crop = null,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(30f, 30f),
                    facing = Facing.RIGHT,
                    selectedTool = ToolType.SEED_BAG,
                    activeItemId = ItemType.WHEAT_SEED,
                    inventory = InventoryState(items = mapOf(ItemType.WHEAT_SEED to 1)),
                ),
                plots = listOf(plot),
            ),
        )

        val planted = engine.update(0f, WorldInput(actionPressed = true))

        assertEquals(CropType.WHEAT, planted.state.plots.single().crop?.type)
        assertEquals(0, planted.state.player.inventory.count(ItemType.WHEAT_SEED))
        assertTrue(planted.events.any { it is WorldEvent.ItemSpent && it.item == ItemType.WHEAT_SEED })
    }

    @Test
    fun emptyPailBecomesFullMilkPailAndCowStartsCooldown() {
        val base = quietWorld()
        val cow = DomesticAnimalState(
            id = 941,
            name = "Luna",
            type = DomesticAnimalType.COW,
            position = Vector2(31f, 30f),
            lifeStage = AnimalLifeStage.ADULT,
            milkReadyInSeconds = 0f,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(30f, 30f),
                    selectedTool = ToolType.MILK_PAIL,
                    activeItemId = ItemType.MILK_PAIL,
                    inventory = InventoryState(items = mapOf(ItemType.MILK_PAIL to 1)),
                ),
                animals = listOf(cow),
            ),
            config = FarmWorldConfig(cowMilkIntervalSeconds = 150f),
        )

        val milked = engine.update(0f, WorldInput(actionPressed = true))

        assertEquals(0, milked.state.player.inventory.count(ItemType.MILK_PAIL))
        assertEquals(1, milked.state.player.inventory.count(ItemType.MILK))
        assertEquals(ItemType.MILK, milked.state.player.activeItemId)
        assertEquals(150f, milked.state.animals.single().milkReadyInSeconds, 0.001f)
        assertTrue(milked.events.any { it is WorldEvent.CowMilked && it.animalId == cow.id })
    }

    @Test
    fun unreachableTreesAndRocksFromOldSaveAreRemoved() {
        val base = quietWorld()
        val outsideTree = TreeState(942, Vector2(4.1f, 20f))
        val insideTree = TreeState(943, Vector2(10f, 10f))
        val outsideRock = StaticObstacleState(
            944,
            ObstacleKind.ROCK,
            CollisionCircle(Vector2(59.8f, 25f), 0.6f),
            25f,
        )
        val insideRock = StaticObstacleState(
            945,
            ObstacleKind.ROCK,
            CollisionCircle(Vector2(45f, 25f), 0.6f),
            25f,
        )
        val sanitized = FarmWorldEngine(
            initialState = base.copy(
                trees = listOf(outsideTree, insideTree),
                staticObstacles = base.staticObstacles + outsideRock + insideRock,
            ),
        ).update(0f).state

        assertFalse(sanitized.trees.any { it.id == outsideTree.id })
        assertTrue(sanitized.trees.any { it.id == insideTree.id })
        assertFalse(sanitized.staticObstacles.any { it.id == outsideRock.id })
        assertTrue(sanitized.staticObstacles.any { it.id == insideRock.id })
    }

    @Test
    fun useHandsNearAdultCowMilksWhenHoldingMilkPail() {
        val base = quietWorld()
        val cow = DomesticAnimalState(
            id = 950,
            name = "Margarita",
            type = DomesticAnimalType.COW,
            position = Vector2(31f, 30f),
            lifeStage = AnimalLifeStage.ADULT,
            milkReadyInSeconds = 0f,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(30f, 30f),
                    selectedTool = ToolType.HANDS,
                    activeItemId = ItemType.MILK_PAIL,
                    inventory = InventoryState(items = mapOf(ItemType.MILK_PAIL to 1)),
                ),
                animals = listOf(cow),
            ),
        )

        val result = engine.update(0f, WorldInput(actionPressed = true))

        assertEquals(0, result.state.player.inventory.count(ItemType.MILK_PAIL))
        assertEquals(1, result.state.player.inventory.count(ItemType.MILK))
        assertEquals(ItemType.MILK, result.state.player.activeItemId)
        assertTrue(result.events.any { it is WorldEvent.CowMilked })
    }

    @Test
    fun useHandsNearTilledPlotPlantsWheatWhenHoldingWheatSeeds() {
        val base = quietWorld()
        val plot = SoilPlotState(
            id = 951,
            position = Vector2(31f, 30f),
            soil = SoilState.TILLED,
            crop = null,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(30f, 30f),
                    facing = Facing.RIGHT,
                    selectedTool = ToolType.HANDS,
                    activeItemId = ItemType.WHEAT_SEED,
                    inventory = InventoryState(items = mapOf(ItemType.WHEAT_SEED to 1)),
                ),
                plots = listOf(plot),
            ),
        )

        val result = engine.update(0f, WorldInput(actionPressed = true))

        assertEquals(CropType.WHEAT, result.state.plots.single().crop?.type)
        assertEquals(0, result.state.player.inventory.count(ItemType.WHEAT_SEED))
        assertEquals(ToolType.HANDS, result.state.player.selectedTool)
        assertNull(result.state.player.activeItemId)
    }

    @Test
    fun placingLastFenceOrGateAutoUnequipsBackToHands() {
        val base = quietWorld()
        val fenceEngine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(10f, 30f),
                    facing = Facing.RIGHT,
                    selectedTool = ToolType.FENCE,
                    activeItemId = ItemType.FENCE,
                    inventory = InventoryState(items = mapOf(ItemType.FENCE to 1)),
                ),
            ),
        )
        val fenceResult = fenceEngine.update(0f, WorldInput(actionPressed = true))
        assertEquals(0, fenceResult.state.player.inventory.count(ItemType.FENCE))
        assertEquals(ToolType.HANDS, fenceResult.state.player.selectedTool)
        assertNull(fenceResult.state.player.activeItemId)

        val gateEngine = FarmWorldEngine(
            initialState = base.copy(
                player = base.player.copy(
                    position = Vector2(10f, 30f),
                    facing = Facing.RIGHT,
                    selectedTool = ToolType.GATE,
                    activeItemId = ItemType.GATE,
                    inventory = InventoryState(items = mapOf(ItemType.GATE to 1)),
                ),
            ),
        )
        val gateResult = gateEngine.update(0f, WorldInput(actionPressed = true))
        assertEquals(0, gateResult.state.player.inventory.count(ItemType.GATE))
        assertEquals(ToolType.HANDS, gateResult.state.player.selectedTool)
        assertNull(gateResult.state.player.activeItemId)
    }

    @Test
    fun defaultAuthoredWorldFencesAreStrictlyInsideFarmBounds() {
        val world = createDefaultFarmWorld()
        for (obstacle in world.staticObstacles) {
            if (obstacle.kind == ObstacleKind.FENCE) {
                when (val c = obstacle.collider) {
                    is CollisionRect -> {
                        assertTrue("Fence left must be >= 3.8f, got ${c.left}", c.left >= 3.8f)
                    }
                    is CollisionCircle -> {
                        assertTrue("Fence circle must be >= 3.8f, got ${c.center.x}", c.center.x >= 3.8f)
                    }
                }
            }
        }
    }

    @Test
    fun bakeryWorkflowGrindingKneadingDualOvensAndSelling() {
        val base = quietWorld()
        val bakery = FactoryBuildingState(
            id = 101,
            type = FactoryType.BAKERY,
            cellX = 20,
            cellY = 20,
            phase = ConstructionPhase.OPERATIONAL,
        )
        val engine = FarmWorldEngine(
            initialState = base.copy(
                factories = listOf(bakery),
                player = base.player.copy(
                    inventory = base.player.inventory
                        .add(ItemType.WHEAT, 2)
                ),
            ),
            config = FarmWorldConfig(maxDeltaSeconds = 1f),
        )

        // 1. Grind wheat into flour
        assertEquals(2, engine.state.player.inventory.count(ItemType.WHEAT))
        assertEquals(0, engine.state.player.inventory.count(ItemType.FLOUR))
        engine.update(0f, WorldInput(grindWheat = true))
        assertEquals(1, engine.state.player.inventory.count(ItemType.WHEAT))
        assertEquals(1, engine.state.player.inventory.count(ItemType.FLOUR))
        assertEquals(ItemType.FLOUR, engine.state.player.activeItemId)

        // Grind second wheat
        engine.update(0f, WorldInput(grindWheat = true))
        assertEquals(0, engine.state.player.inventory.count(ItemType.WHEAT))
        assertEquals(2, engine.state.player.inventory.count(ItemType.FLOUR))

        // 2. Knead flour with water into dough
        assertEquals(0, engine.state.player.inventory.count(ItemType.DOUGH))
        engine.update(0f, WorldInput(kneadDough = true))
        assertEquals(1, engine.state.player.inventory.count(ItemType.FLOUR))
        assertEquals(1, engine.state.player.inventory.count(ItemType.DOUGH))
        assertEquals(ItemType.DOUGH, engine.state.player.activeItemId)

        engine.update(0f, WorldInput(kneadDough = true))
        assertEquals(0, engine.state.player.inventory.count(ItemType.FLOUR))
        assertEquals(2, engine.state.player.inventory.count(ItemType.DOUGH))

        // 3. Put dough into Oven 1 and Oven 2
        engine.update(0f, WorldInput(startBakeryOven = Pair(101, 1)))
        assertEquals(1, engine.state.player.inventory.count(ItemType.DOUGH))
        var currentBakery = engine.state.factories.first { it.id == 101 }
        assertTrue(currentBakery.oven1Active)
        assertEquals(20f, currentBakery.oven1Timer, 0.01f)
        assertFalse(currentBakery.oven1Ready)

        engine.update(0f, WorldInput(startBakeryOven = Pair(101, 2)))
        assertEquals(0, engine.state.player.inventory.count(ItemType.DOUGH))
        assertNull(engine.state.player.activeItemId)
        currentBakery = engine.state.factories.first { it.id == 101 }
        assertTrue(currentBakery.oven2Active)
        assertEquals(20f, currentBakery.oven2Timer, 0.01f)
        assertFalse(currentBakery.oven2Ready)

        // 4. Advance time by 21 real seconds (baking is snappy ~20s)
        advance(engine, 21f)

        currentBakery = engine.state.factories.first { it.id == 101 }
        assertFalse(currentBakery.oven1Active)
        assertTrue(currentBakery.oven1Ready)
        assertFalse(currentBakery.oven2Active)
        assertTrue(currentBakery.oven2Ready)

        // 5. Collect bread from Oven 1 (yields 2 breads)
        assertEquals(0, engine.state.player.inventory.count(ItemType.BREAD))
        engine.update(0f, WorldInput(collectBakeryOven = Pair(101, 1)))
        assertEquals(2, engine.state.player.inventory.count(ItemType.BREAD))
        assertEquals(ItemType.BREAD, engine.state.player.activeItemId)
        currentBakery = engine.state.factories.first { it.id == 101 }
        assertFalse(currentBakery.oven1Ready)

        // Collect bread from Oven 2 (yields 2 breads -> total 4)
        engine.update(0f, WorldInput(collectBakeryOven = Pair(101, 2)))
        assertEquals(4, engine.state.player.inventory.count(ItemType.BREAD))
        assertEquals(ItemType.BREAD, engine.state.player.activeItemId)
        currentBakery = engine.state.factories.first { it.id == 101 }
        assertFalse(currentBakery.oven2Ready)

        // 6. Sell 1 bread via wheelbarrow to test 140 coin price
        engine.update(0f, WorldInput(toggleWheelbarrow = true))
        engine.update(0f, WorldInput(loadWheelbarrowItem = ItemType.BREAD))
        assertEquals(3, engine.state.player.inventory.count(ItemType.BREAD))
        assertEquals(1, engine.state.wheelbarrow.cargo[ItemType.BREAD])

        val coinsBefore = engine.state.player.inventory.coins
        engine.update(0f, WorldInput(sellWheelbarrowCargo = true))
        assertEquals(coinsBefore + 140, engine.state.player.inventory.coins)
    }

    private fun quietWorld(): FarmWorldState {
        val world = createDefaultFarmWorld()
        return world.copy(
            trees = emptyList(),
            chickens = emptyList(),
            eggs = emptyList(),
            groundItems = emptyList(),
            grassTufts = emptyList(),
            gates = emptyList(),
            truck = world.truck.copy(phase = TruckPhase.ABSENT, phaseTimer = 10_000f),
            rockSpawning = RockSpawnState(secondsUntilNext = 10_000f),
            ambientCreatures = emptyList(),
            ambientCreatureSpawnTimer = 10_000f,
            pathTiles = emptyList(),
        )
    }

    private fun shapeCenterForTest(shape: CollisionShape): Vector2 = when (shape) {
        is CollisionCircle -> shape.center
        is CollisionRect -> shape.center
    }

    private fun clearActionCooldown(engine: FarmWorldEngine) {
        repeat(10) {
            if (engine.state.player.actionCooldown <= 0f) return
            engine.update(engine.config.maxDeltaSeconds)
        }
        assertEquals(0f, engine.state.player.actionCooldown, 0.0001f)
    }

    private fun advance(engine: FarmWorldEngine, totalSeconds: Float) {
        var remaining = totalSeconds
        while (remaining > 0.0001f) {
            val delta = minOf(remaining, engine.config.maxDeltaSeconds)
            engine.update(delta)
            remaining -= delta
        }
    }
}
