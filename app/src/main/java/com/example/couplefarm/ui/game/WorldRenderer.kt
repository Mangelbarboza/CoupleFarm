package com.example.couplefarm.ui.game

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.couplefarm.R
import com.example.couplefarm.ui.PetBreedCatalog
import com.example.couplefarm.game.ChickenBehavior
import com.example.couplefarm.game.ChickenLifeStage
import com.example.couplefarm.game.ChickenState
import com.example.couplefarm.game.AnimalHandlingState
import com.example.couplefarm.game.AnimalLifeStage
import com.example.couplefarm.game.AnimalKind
import com.example.couplefarm.game.AmbientCreatureState
import com.example.couplefarm.game.AmbientCreatureType
import com.example.couplefarm.game.BallPhase
import com.example.couplefarm.game.CollisionCircle
import com.example.couplefarm.game.CollisionRect
import com.example.couplefarm.game.CollisionShape
import com.example.couplefarm.game.DomesticAnimalBehavior
import com.example.couplefarm.game.DomesticAnimalState
import com.example.couplefarm.game.DomesticAnimalType
import com.example.couplefarm.game.DeliveryKind
import com.example.couplefarm.game.CourierPhase
import com.example.couplefarm.game.EmoteType
import com.example.couplefarm.game.Facing
import com.example.couplefarm.game.FarmWorldState
import com.example.couplefarm.game.PlayerState
import com.example.couplefarm.game.FishingPhase
import com.example.couplefarm.game.FoxPhase
import com.example.couplefarm.game.GroundItemState
import com.example.couplefarm.game.ItemType
import com.example.couplefarm.game.ObstacleKind
import com.example.couplefarm.game.RenderEntityType
import com.example.couplefarm.game.CropType
import com.example.couplefarm.game.SoilPlotState
import com.example.couplefarm.game.SoilState
import com.example.couplefarm.game.TerrainType
import com.example.couplefarm.game.TerrainRegion
import com.example.couplefarm.game.ToolType
import com.example.couplefarm.game.TreeLifeState
import com.example.couplefarm.game.TreeState
import com.example.couplefarm.game.TruckPhase
import com.example.couplefarm.game.Vector2
import com.example.couplefarm.game.renderQueue
import com.example.couplefarm.game.snapToTileCenter
import com.example.couplefarm.game.FENCE_TILE_SIZE
import com.example.couplefarm.game.shapeCenter
import com.example.couplefarm.game.FactoryBuildingState
import com.example.couplefarm.game.FactoryType
import com.example.couplefarm.game.BuildingOrientation
import com.example.couplefarm.game.ConstructionPhase
import com.example.couplefarm.game.FenceGateState
import com.example.couplefarm.game.WheelbarrowState
import com.example.couplefarm.game.ThrownProjectileState
import com.example.couplefarm.game.WaterRippleState
import com.example.couplefarm.game.rectIntersectsShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the 64x48 farm from independent world objects. [overview] ignores the camera and fits the
 * complete farm inside the canvas, preserving its aspect ratio and adding a quiet letterbox.
 */
@Composable
fun FarmWorldCanvas(
    world: FarmWorldState,
    modifier: Modifier = Modifier,
    overview: Boolean = false,
) {
    val sprites = loadWorldSprites()
    Canvas(modifier = modifier) {
        drawFarmWorld(world, sprites, overview)
    }
}

@Composable
fun BakeryInteriorCanvas(
    world: FarmWorldState,
    player: PlayerState,
    bakery: FactoryBuildingState,
    modifier: Modifier = Modifier,
) {
    val sprites = loadWorldSprites()
    Canvas(modifier = modifier) {
        drawBakeryInterior(world, player, bakery, sprites)
    }
}

private var cachedWorldSprites: WorldSprites? = null

@Composable
private fun loadWorldSprites(): WorldSprites {
    cachedWorldSprites?.let { return it }
    val context = LocalContext.current
    fun optionalDrawable(name: String): ImageBitmap? {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) return null
        val bitmap = BitmapFactory.decodeResource(context.resources, id) ?: return null
        // Atlas extraction can occasionally yield an empty transparent cell. Reject it once at
        // load time so animation gracefully reuses a valid neighbouring frame instead of blinking.
        var visible = false
        pixelSearch@ for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                if (((bitmap.getPixel(x, y) ushr 24) and 0xFF) > 8) {
                    visible = true
                    break@pixelSearch
                }
            }
        }
        if (!visible) {
            bitmap.recycle()
            return null
        }
        return bitmap.asImageBitmap()
    }
    fun animalV5(prefix: String) = AnimalV5Sprites(
        downIdle = optionalDrawable("${prefix}_down_idle_v5"),
        downRightWalk = optionalDrawable("${prefix}_down_right_walk_v5"),
        upIdle = optionalDrawable("${prefix}_up_idle_v5"),
        upLeftWalk = optionalDrawable("${prefix}_up_left_walk_v5"),
    )
    fun directionalV7(prefix: String) = DirectionalAnimalSprites(
        idle = listOf("down", "right", "up", "left").map { optionalDrawable("${prefix}_sit_${it}_v7") },
        walkA = listOf("down", "right", "up", "left").map { optionalDrawable("${prefix}_walk_a_${it}_v7") },
        walkB = listOf("down", "right", "up", "left").map { optionalDrawable("${prefix}_walk_b_${it}_v7") },
    )
    return WorldSprites(
    farmGround = ImageBitmap.imageResource(R.drawable.farm_ground_v4),
    grassHd = ImageBitmap.imageResource(R.drawable.grass_hd_v7),
    birdIdle = optionalDrawable("bird_idle_v8"),
    birdFly = listOf(optionalDrawable("bird_fly_a_v8"), optionalDrawable("bird_fly_b_v8")),
    butterflyFly = listOf(optionalDrawable("butterfly_fly_a_v8"), optionalDrawable("butterfly_fly_b_v8")),
    water = listOf(
        ImageBitmap.imageResource(R.drawable.water_0_v3),
        ImageBitmap.imageResource(R.drawable.water_1_v3),
        ImageBitmap.imageResource(R.drawable.water_2_v3),
    ),
    dirt = ImageBitmap.imageResource(R.drawable.dirt_v3),
    treeIdle = ImageBitmap.imageResource(R.drawable.tree_idle_v3),
    treeBreezeLeft = ImageBitmap.imageResource(R.drawable.tree_breeze_left_v3),
    treeBreezeRight = ImageBitmap.imageResource(R.drawable.tree_breeze_right_v3),
    treeShake = ImageBitmap.imageResource(R.drawable.tree_shake_v3),
    treeDamage = ImageBitmap.imageResource(R.drawable.tree_damage_v3),
    treeLean = ImageBitmap.imageResource(R.drawable.tree_lean_v3),
    treeFall = ImageBitmap.imageResource(R.drawable.tree_fall_v3),
    treeStump = ImageBitmap.imageResource(R.drawable.tree_stump_v3),
    barn = ImageBitmap.imageResource(R.drawable.barn_v3),
    truckDrive = listOf(
        ImageBitmap.imageResource(R.drawable.truck_drive_0_v3),
        ImageBitmap.imageResource(R.drawable.truck_drive_1_v3),
    ),
    truckParked = ImageBitmap.imageResource(R.drawable.truck_parked_v3),
    truckOpen = ImageBitmap.imageResource(R.drawable.truck_open_v3),
    carrot = listOf(
        ImageBitmap.imageResource(R.drawable.crop_carrot_0_v3),
        ImageBitmap.imageResource(R.drawable.crop_carrot_1_v3),
        ImageBitmap.imageResource(R.drawable.crop_carrot_2_v3),
        ImageBitmap.imageResource(R.drawable.crop_carrot_3_v3),
    ),
    rock = ImageBitmap.imageResource(R.drawable.rock_v3),
    flowers = ImageBitmap.imageResource(R.drawable.flowers_v3),
    tufts = listOf(
        ImageBitmap.imageResource(R.drawable.tuft_0_v3),
        ImageBitmap.imageResource(R.drawable.tuft_1_v3),
        ImageBitmap.imageResource(R.drawable.tuft_2_v3),
    ),
    apple = ImageBitmap.imageResource(R.drawable.item_apple_v3),
    fish = ImageBitmap.imageResource(R.drawable.item_fish_v3),
    wood = ImageBitmap.imageResource(R.drawable.item_wood_v3),
    egg = ImageBitmap.imageResource(R.drawable.egg_v2),
    farmer = FarmerSprites(
        down = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_down_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_down_walk_v4),
        ),
        downLeft = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_down_left_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_down_left_walk_v4),
        ),
        left = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_left_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_left_walk_v4),
        ),
        upLeft = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_up_left_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_up_left_walk_v4),
        ),
        up = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_up_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_up_walk_v4),
        ),
        upRight = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_up_right_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_up_right_walk_v4),
        ),
        right = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_right_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_right_walk_v4),
        ),
        downRight = DirectionSprites(
            ImageBitmap.imageResource(R.drawable.farmer_down_right_idle_v4),
            ImageBitmap.imageResource(R.drawable.farmer_down_right_walk_v4),
        ),
    ),
    axeAction = listOf(
        ImageBitmap.imageResource(R.drawable.farmer_axe_0_v4),
        ImageBitmap.imageResource(R.drawable.farmer_axe_1_v4),
        ImageBitmap.imageResource(R.drawable.farmer_axe_2_v4),
        ImageBitmap.imageResource(R.drawable.farmer_axe_3_v4),
    ),
    pickaxeAction = listOf(
        ImageBitmap.imageResource(R.drawable.farmer_pickaxe_0_v4),
        ImageBitmap.imageResource(R.drawable.farmer_pickaxe_1_v4),
        ImageBitmap.imageResource(R.drawable.farmer_pickaxe_2_v4),
        ImageBitmap.imageResource(R.drawable.farmer_pickaxe_3_v4),
    ),
    hoeAction = listOf(
        ImageBitmap.imageResource(R.drawable.farmer_hoe_0_v4),
        ImageBitmap.imageResource(R.drawable.farmer_hoe_1_v4),
        ImageBitmap.imageResource(R.drawable.farmer_hoe_2_v4),
        ImageBitmap.imageResource(R.drawable.farmer_hoe_3_v4),
    ),
    waterAction = listOf(
        ImageBitmap.imageResource(R.drawable.farmer_water_0_v4),
        ImageBitmap.imageResource(R.drawable.farmer_water_1_v4),
        ImageBitmap.imageResource(R.drawable.farmer_water_2_v4),
        ImageBitmap.imageResource(R.drawable.farmer_water_3_v4),
    ),
    seedAction = listOf(
        ImageBitmap.imageResource(R.drawable.farmer_seed_0_v4),
        ImageBitmap.imageResource(R.drawable.farmer_seed_1_v4),
        ImageBitmap.imageResource(R.drawable.farmer_seed_2_v4),
        ImageBitmap.imageResource(R.drawable.farmer_seed_3_v4),
    ),
    fishingAction = listOf(
        ImageBitmap.imageResource(R.drawable.farmer_fish_cast_0_v4),
        ImageBitmap.imageResource(R.drawable.farmer_fish_cast_1_v4),
        ImageBitmap.imageResource(R.drawable.farmer_fish_cast_2_v4),
        ImageBitmap.imageResource(R.drawable.farmer_fish_cast_3_v4),
    ),
    emotes = mapOf(
        EmoteType.HEART to ImageBitmap.imageResource(R.drawable.emote_heart_v4),
        EmoteType.HAPPY to ImageBitmap.imageResource(R.drawable.emote_smile_v4),
        EmoteType.WAVE to ImageBitmap.imageResource(R.drawable.emote_wave_v4),
        EmoteType.SURPRISED to ImageBitmap.imageResource(R.drawable.emote_surprise_v4),
    ),
    axe = ImageBitmap.imageResource(R.drawable.tool_axe_v3),
    pickaxe = ImageBitmap.imageResource(R.drawable.tool_pickaxe_v4),
    hoe = ImageBitmap.imageResource(R.drawable.tool_hoe_v3),
    wateringCan = ImageBitmap.imageResource(R.drawable.tool_watering_v3),
    seedBag = ImageBitmap.imageResource(R.drawable.tool_seeds_v3),
    fishingRod = ImageBitmap.imageResource(R.drawable.tool_rod_v3),
    chicken = AnimalSprites(
        idle = ImageBitmap.imageResource(R.drawable.chicken_idle_v2),
        walk = ImageBitmap.imageResource(R.drawable.chicken_walk_v2),
        peck = ImageBitmap.imageResource(R.drawable.chicken_peck_v2),
        happy = ImageBitmap.imageResource(R.drawable.chicken_happy_v2),
    ),
    chick = AnimalSprites(
        idle = ImageBitmap.imageResource(R.drawable.chick_idle_v3),
        walk = ImageBitmap.imageResource(R.drawable.chick_walk_v3),
        peck = ImageBitmap.imageResource(R.drawable.chick_peck_v3),
        happy = ImageBitmap.imageResource(R.drawable.chick_happy_v3),
    ),
    v5 = OptionalV5Sprites(
        cow = animalV5("cow"),
        pig = animalV5("pig"),
        dog = animalV5("dog"),
        cat = animalV5("cat"),
        foxDownIdle = optionalDrawable("fox_down_idle_v5"),
        foxDownRightWalk = optionalDrawable("fox_down_right_walk_v5"),
        foxUpIdle = optionalDrawable("fox_up_idle_v5"),
        foxUpLeftWalk = optionalDrawable("fox_up_left_walk_v5"),
        foxAttack = optionalDrawable("fox_right_attack_v5"),
        foxHurt = optionalDrawable("fox_hurt_v5"),
        foxBite = optionalDrawable("fox_left_bite_v5"),
        foxDefeated = optionalDrawable("fox_defeated_v5"),
        bicycle = optionalDrawable("bicycle_parked_v5"),
        ball = optionalDrawable("item_ball_v5"),
        milk = optionalDrawable("item_milk_v5"),
        carrot = optionalDrawable("item_carrot_v5"),
        machete = optionalDrawable("item_machete_v5"),
        carryDown = optionalDrawable("farmer_carry_down_v5"),
        carryDownWalk = optionalDrawable("farmer_carry_down_walk_v5"),
        carryLeft = optionalDrawable("farmer_carry_left_v5"),
        carryUp = optionalDrawable("farmer_carry_up_v5"),
        macheteAction = listOfNotNull(
            optionalDrawable("farmer_machete_ready_v5"),
            optionalDrawable("farmer_machete_windup_v5"),
            optionalDrawable("farmer_machete_slash_v5"),
            optionalDrawable("farmer_machete_recover_v5"),
        ).takeIf { it.size == 4 },
        dogBreeds = listOf(null, optionalDrawable("dog_border_down_v6"), optionalDrawable("dog_cream_down_v6")),
        catBreeds = listOf(null, optionalDrawable("cat_gray_down_v6"), optionalDrawable("cat_calico_down_v6")),
        fenceHorizontal = optionalDrawable("fence_horizontal_v6"),
        fenceVertical = optionalDrawable("fence_vertical_v6"),
        fenceCorner = optionalDrawable("fence_corner_rd_v6"),
        fenceCross = optionalDrawable("fence_cross_v6"),
        fenceCenterPost = optionalDrawable("fence_center_post_v8"),
        fenceArmEast = optionalDrawable("fence_arm_east_v8"),
        fenceArmWest = optionalDrawable("fence_arm_west_v8"),
        fenceArmNorth = optionalDrawable("fence_arm_north_v8"),
        fenceArmSouth = optionalDrawable("fence_arm_south_v8"),
        factoryDairy = optionalDrawable("workshop_dairy_v2") ?: optionalDrawable("factory_dairy_v1"),
        factorySlaughterhouse = optionalDrawable("workshop_slaughterhouse_v2") ?: optionalDrawable("factory_slaughterhouse_v1"),
        factoryEggPacker = optionalDrawable("workshop_egg_packer_v2") ?: optionalDrawable("factory_egg_packer_v1"),
        factoryVeggiePacker = optionalDrawable("workshop_veggie_packer_v2") ?: optionalDrawable("factory_veggie_packer_v1"),
        factoryBakery = optionalDrawable("workshop_bakery_v3") ?: optionalDrawable("workshop_bakery_v2") ?: optionalDrawable("factory_bakery_v1"),
        factoryFishProcessor = optionalDrawable("workshop_fish_processor_v2"),
        fenceGateClosed = optionalDrawable("fence_gate_closed_v8"),
        fenceGateOpen = optionalDrawable("fence_gate_open_v8"),
        wheelbarrowEmpty = optionalDrawable("wheelbarrow_empty_v1"),
        wheelbarrowLoaded = optionalDrawable("wheelbarrow_loaded_v1"),
        itemFishFillet = optionalDrawable("item_fish_fillet_v1"),
        toolMilkPail = optionalDrawable("tool_milk_pail_v1"),
        toolPencil = optionalDrawable("tool_pencil_v1"),
        itemWheat = optionalDrawable("item_wheat_v1"),
        itemWheatSeed = optionalDrawable("item_wheat_seed_v1"),
        itemBread = optionalDrawable("item_bread_v1"),
        itemCheese = optionalDrawable("item_cheese_v1"),
        itemMeat = optionalDrawable("item_meat_v1"),
        itemEggCarton = optionalDrawable("item_egg_carton_v1"),
        itemVeggieBox = optionalDrawable("item_veggie_box_v1"),
        itemFlour = optionalDrawable("item_flour_v1"),
        itemDough = optionalDrawable("item_dough_v1"),
        bakeryInteriorRoom = optionalDrawable("bakery_interior_room_v1"),
        bakeryDoubleOvens = optionalDrawable("bakery_double_ovens_v1"),
        minigameMolcajete = optionalDrawable("minigame_molcajete_v2"),
        doughKneadStation = optionalDrawable("dough_knead_station_v1"),
        bakeryInteriorCabin = optionalDrawable("bakery_interior_cabin_v7"),
        bakeryOvens = optionalDrawable("bakery_ovens_v7"),
        bakeryMolcajete = optionalDrawable("bakery_molcajete_v7"),
        bakeryKneadTable = optionalDrawable("bakery_knead_table_v7"),
        wheatCrops = listOfNotNull(
            optionalDrawable("crop_wheat_0_v1"),
            optionalDrawable("crop_wheat_1_v1"),
            optionalDrawable("crop_wheat_2_v1"),
            optionalDrawable("crop_wheat_3_v1"),
        ).takeIf { it.size == 4 },
        puppyV7 = PetBreedCatalog.dogs.associate { it.breedIndex to directionalV7(it.spritePrefix) },
        kittenV7 = PetBreedCatalog.cats.associate { it.breedIndex to directionalV7(it.spritePrefix) },
        calfV7 = directionalV7("calf"),
        pigletV7 = directionalV7("piglet"),
        bicycleV7 = directionalV7("farmer_bike"),
        courierV7 = DirectionalAnimalSprites(
            idle = listOf("down", "right", "up", "left").map { optionalDrawable("courier_walk_a_${it}_v7") },
            walkA = listOf("down", "right", "up", "left").map { optionalDrawable("courier_walk_a_${it}_v7") },
            walkB = listOf("down", "right", "up", "left").map { optionalDrawable("courier_walk_b_${it}_v7") },
        ),
        helperV9 = DirectionalAnimalSprites(
            idle = listOf("down", "right", "up", "left").map { optionalDrawable("helper_idle_${it}_v9") },
            walkA = listOf("down", "right", "up", "left").map { optionalDrawable("helper_walk_a_${it}_v9") },
            walkB = listOf("down", "right", "up", "left").map { optionalDrawable("helper_walk_b_${it}_v9") },
        ),
    ),
    ).also { cachedWorldSprites = it }
}

private class AnimalSprites(
    val idle: ImageBitmap,
    val walk: ImageBitmap,
    val peck: ImageBitmap,
    val happy: ImageBitmap,
)

private data class AnimalV5Sprites(
    val downIdle: ImageBitmap?,
    val downRightWalk: ImageBitmap?,
    val upIdle: ImageBitmap?,
    val upLeftWalk: ImageBitmap?,
)

/** Direction indices: down, right, up, left. All images are decoded once for the process. */
private data class DirectionalAnimalSprites(
    val idle: List<ImageBitmap?>,
    val walkA: List<ImageBitmap?>,
    val walkB: List<ImageBitmap?>,
) {
    fun frame(facing: Facing, moving: Boolean, alternate: Boolean): ImageBitmap? {
        val index = when (facing) {
            Facing.DOWN -> 0
            Facing.RIGHT -> 1
            Facing.UP -> 2
            Facing.LEFT -> 3
        }
        return when {
            !moving -> idle[index]
            alternate -> walkB[index]
            else -> walkA[index]
        }
    }
}

private data class OptionalV5Sprites(
    val cow: AnimalV5Sprites,
    val pig: AnimalV5Sprites,
    val dog: AnimalV5Sprites,
    val cat: AnimalV5Sprites,
    val foxDownIdle: ImageBitmap?,
    val foxDownRightWalk: ImageBitmap?,
    val foxUpIdle: ImageBitmap?,
    val foxUpLeftWalk: ImageBitmap?,
    val foxAttack: ImageBitmap?,
    val foxHurt: ImageBitmap?,
    val foxBite: ImageBitmap?,
    val foxDefeated: ImageBitmap?,
    val bicycle: ImageBitmap?,
    val ball: ImageBitmap?,
    val milk: ImageBitmap?,
    val carrot: ImageBitmap?,
    val machete: ImageBitmap?,
    val carryDown: ImageBitmap?,
    val carryDownWalk: ImageBitmap?,
    val carryLeft: ImageBitmap?,
    val carryUp: ImageBitmap?,
    val macheteAction: List<ImageBitmap>?,
    val dogBreeds: List<ImageBitmap?>,
    val catBreeds: List<ImageBitmap?>,
    val fenceHorizontal: ImageBitmap?,
    val fenceVertical: ImageBitmap?,
    val fenceCorner: ImageBitmap?,
    val fenceCross: ImageBitmap?,
    val fenceCenterPost: ImageBitmap?,
    val fenceArmEast: ImageBitmap?,
    val fenceArmWest: ImageBitmap?,
    val fenceArmNorth: ImageBitmap?,
    val fenceArmSouth: ImageBitmap?,
    val factoryDairy: ImageBitmap?,
    val factorySlaughterhouse: ImageBitmap?,
    val factoryEggPacker: ImageBitmap?,
    val factoryVeggiePacker: ImageBitmap?,
    val factoryBakery: ImageBitmap?,
    val factoryFishProcessor: ImageBitmap? = null,
    val fenceGateClosed: ImageBitmap? = null,
    val fenceGateOpen: ImageBitmap? = null,
    val wheelbarrowEmpty: ImageBitmap? = null,
    val wheelbarrowLoaded: ImageBitmap? = null,
    val itemFishFillet: ImageBitmap? = null,
    val toolMilkPail: ImageBitmap?,
    val toolPencil: ImageBitmap?,
    val itemWheat: ImageBitmap?,
    val itemWheatSeed: ImageBitmap?,
    val itemBread: ImageBitmap?,
    val itemCheese: ImageBitmap?,
    val itemMeat: ImageBitmap?,
    val itemEggCarton: ImageBitmap?,
    val itemVeggieBox: ImageBitmap?,
    val itemFlour: ImageBitmap? = null,
    val itemDough: ImageBitmap? = null,
    val bakeryInteriorRoom: ImageBitmap? = null,
    val bakeryDoubleOvens: ImageBitmap? = null,
    val minigameMolcajete: ImageBitmap? = null,
    val doughKneadStation: ImageBitmap? = null,
    val bakeryInteriorCabin: ImageBitmap? = null,
    val bakeryOvens: ImageBitmap? = null,
    val bakeryMolcajete: ImageBitmap? = null,
    val bakeryKneadTable: ImageBitmap? = null,
    val wheatCrops: List<ImageBitmap>?,
    val puppyV7: Map<Int, DirectionalAnimalSprites>,
    val kittenV7: Map<Int, DirectionalAnimalSprites>,
    val calfV7: DirectionalAnimalSprites,
    val pigletV7: DirectionalAnimalSprites,
    val bicycleV7: DirectionalAnimalSprites,
    val courierV7: DirectionalAnimalSprites,
    val helperV9: DirectionalAnimalSprites,
)

private class DirectionSprites(
    val idle: ImageBitmap,
    val walk: ImageBitmap,
)

private class FarmerSprites(
    val down: DirectionSprites,
    val downLeft: DirectionSprites,
    val left: DirectionSprites,
    val upLeft: DirectionSprites,
    val up: DirectionSprites,
    val upRight: DirectionSprites,
    val right: DirectionSprites,
    val downRight: DirectionSprites,
)

private class WorldSprites(
    val farmGround: ImageBitmap,
    val grassHd: ImageBitmap,
    val birdIdle: ImageBitmap?,
    val birdFly: List<ImageBitmap?>,
    val butterflyFly: List<ImageBitmap?>,
    val water: List<ImageBitmap>,
    val dirt: ImageBitmap,
    val treeIdle: ImageBitmap,
    val treeBreezeLeft: ImageBitmap,
    val treeBreezeRight: ImageBitmap,
    val treeShake: ImageBitmap,
    val treeDamage: ImageBitmap,
    val treeLean: ImageBitmap,
    val treeFall: ImageBitmap,
    val treeStump: ImageBitmap,
    val barn: ImageBitmap,
    val truckDrive: List<ImageBitmap>,
    val truckParked: ImageBitmap,
    val truckOpen: ImageBitmap,
    val carrot: List<ImageBitmap>,
    val rock: ImageBitmap,
    val flowers: ImageBitmap,
    val tufts: List<ImageBitmap>,
    val apple: ImageBitmap,
    val fish: ImageBitmap,
    val wood: ImageBitmap,
    val egg: ImageBitmap,
    val farmer: FarmerSprites,
    val axeAction: List<ImageBitmap>,
    val pickaxeAction: List<ImageBitmap>,
    val hoeAction: List<ImageBitmap>,
    val waterAction: List<ImageBitmap>,
    val seedAction: List<ImageBitmap>,
    val fishingAction: List<ImageBitmap>,
    val emotes: Map<EmoteType, ImageBitmap>,
    val axe: ImageBitmap,
    val pickaxe: ImageBitmap,
    val hoe: ImageBitmap,
    val wateringCan: ImageBitmap,
    val seedBag: ImageBitmap,
    val fishingRod: ImageBitmap,
    val chicken: AnimalSprites,
    val chick: AnimalSprites,
    val v5: OptionalV5Sprites,
)

private data class WorldViewport(
    val pixelsPerUnit: Float,
    val origin: Offset,
    val visibleBounds: CollisionRect,
) {
    fun toScreen(position: Vector2) = Offset(
        origin.x + position.x * pixelsPerUnit,
        origin.y + position.y * pixelsPerUnit,
    )
}

private fun DrawScope.drawBakeryInterior(
    world: FarmWorldState,
    player: PlayerState,
    bakery: FactoryBuildingState,
    sprites: WorldSprites,
) {
    // 1. Dark warm border surrounding the room
    drawRect(Color(0xFF140D07))

    // 2. Interior room dimensions: 16.0f width x 9.0f height (16:9 aspect ratio matching bitmap)
    val roomWidth = 16.0f
    val roomHeight = 9.0f
    val ppu = minOf(size.width / roomWidth, size.height / roomHeight)
    val roomPixelW = roomWidth * ppu
    val roomPixelH = roomHeight * ppu
    val origin = Offset((size.width - roomPixelW) / 2f, (size.height - roomPixelH) / 2f)
    val viewport = WorldViewport(
        pixelsPerUnit = ppu,
        origin = origin,
        visibleBounds = CollisionRect(0f, 0f, roomWidth, roomHeight),
    )

    // 3. Draw Room Cabin Background Image
    val bg = sprites.v5.bakeryInteriorCabin ?: sprites.v5.bakeryInteriorRoom
    if (bg != null) {
        drawImage(
            image = bg,
            dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
            dstSize = IntSize(roomPixelW.toInt(), roomPixelH.toInt()),
            filterQuality = FilterQuality.Low,
        )
    } else {
        // Warm wood plank fallback
        drawRect(Color(0xFF8D6E63), topLeft = origin, size = Size(roomPixelW, roomPixelH))
    }

    val elapsed = world.animation.elapsedSeconds

    // 4. Standalone Stone Ovens against the north wall
    val ovensImg = sprites.v5.bakeryOvens ?: sprites.v5.bakeryDoubleOvens
    if (ovensImg != null) {
        drawContactShadow(
            center = Vector2(8.0f, 3.78f),
            worldWidth = 4.95f,
            worldHeight = 0.55f,
            viewport = viewport,
            alpha = 0.48f,
        )
        drawBottomAnchoredSprite(
            image = ovensImg,
            bottomCenter = Vector2(8.0f, 3.8f),
            worldWidth = 4.95f,
            worldHeight = 3.6f,
            viewport = viewport,
        )
    }

    // Positions for Left Oven (#1) and Right Oven (#2)
    val oven1Pos = Vector2(6.75f, 3.2f)
    val oven1Hearth = Vector2(6.75f, 3.05f)
    val oven1Chimney = Vector2(6.75f, 0.85f)

    val oven2Pos = Vector2(9.25f, 3.2f)
    val oven2Hearth = Vector2(9.25f, 3.05f)
    val oven2Chimney = Vector2(9.25f, 0.85f)

    // Oven 1 visual state (Fire, Smoke, or Ready Bread)
    if (bakery.oven1Active) {
        val flicker = 0.85f + 0.15f * sin(elapsed * 12f)
        val hearthScreen = viewport.toScreen(oven1Hearth)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF9800).copy(alpha = 0.85f * flicker),
                    Color(0xFFFF3D00).copy(alpha = 0.5f * flicker),
                    Color.Transparent,
                ),
                center = hearthScreen,
                radius = ppu * 0.9f,
            ),
            radius = ppu * 0.9f,
            center = hearthScreen,
        )
        // Rising smoke puffs from left chimney
        val chimScreen = viewport.toScreen(oven1Chimney)
        for (i in 0..2) {
            val p = ((elapsed * 1.6f + i * 0.65f) % 2.0f) / 2.0f
            val px = chimScreen.x + sin(p * 5f + i) * ppu * 0.15f
            val py = chimScreen.y - p * ppu * 1.3f
            drawCircle(Color.White.copy(alpha = (1f - p) * 0.5f), ppu * (0.1f + p * 0.18f), Offset(px, py))
        }
    } else if (bakery.oven1Ready) {
        val pulse = 0.85f + 0.15f * sin(elapsed * 6f)
        val oven1Screen = viewport.toScreen(oven1Pos)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD54F).copy(alpha = 0.75f * pulse),
                    Color(0xFFFFB300).copy(alpha = 0.35f * pulse),
                    Color.Transparent,
                ),
                center = oven1Screen,
                radius = ppu * 1.8f,
            ),
            radius = ppu * 1.8f,
            center = oven1Screen,
        )
        // Floating fresh bread icon above oven 1
        sprites.v5.itemBread?.let { breadImg ->
            val bob = sin(elapsed * 4.5f) * 0.12f
            drawBottomAnchoredSprite(breadImg, oven1Pos + Vector2(0f, -0.6f - bob), 1.0f, 1.0f, viewport)
            for (s in 0..2) {
                val sp = ((elapsed * 2f + s * 0.65f) % 1.5f) / 1.5f
                val sx = oven1Screen.x + sin(sp * 7f + s) * ppu * 0.4f
                val sy = oven1Screen.y - ppu * 0.3f - sp * ppu * 0.7f
                drawCircle(Color(0xFFFFF9C4).copy(alpha = (1f - sp) * 0.85f), ppu * 0.05f * (1f - sp), Offset(sx, sy))
            }
        }
    }

    // Oven 2 visual state (Fire, Smoke, or Ready Bread)
    if (bakery.oven2Active) {
        val flicker = 0.85f + 0.15f * sin(elapsed * 12f + 1.5f)
        val hearthScreen = viewport.toScreen(oven2Hearth)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF9800).copy(alpha = 0.85f * flicker),
                    Color(0xFFFF3D00).copy(alpha = 0.5f * flicker),
                    Color.Transparent,
                ),
                center = hearthScreen,
                radius = ppu * 0.9f,
            ),
            radius = ppu * 0.9f,
            center = hearthScreen,
        )
        // Rising smoke puffs from right chimney
        val chimScreen = viewport.toScreen(oven2Chimney)
        for (i in 0..2) {
            val p = ((elapsed * 1.6f + i * 0.65f + 0.33f) % 2.0f) / 2.0f
            val px = chimScreen.x + sin(p * 5f + i + 1f) * ppu * 0.15f
            val py = chimScreen.y - p * ppu * 1.3f
            drawCircle(Color.White.copy(alpha = (1f - p) * 0.5f), ppu * (0.1f + p * 0.18f), Offset(px, py))
        }
    } else if (bakery.oven2Ready) {
        val pulse = 0.85f + 0.15f * sin(elapsed * 6f + 1.2f)
        val oven2Screen = viewport.toScreen(oven2Pos)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD54F).copy(alpha = 0.75f * pulse),
                    Color(0xFFFFB300).copy(alpha = 0.35f * pulse),
                    Color.Transparent,
                ),
                center = oven2Screen,
                radius = ppu * 1.8f,
            ),
            radius = ppu * 1.8f,
            center = oven2Screen,
        )
        // Floating fresh bread icon above oven 2
        sprites.v5.itemBread?.let { breadImg ->
            val bob = sin(elapsed * 4.5f + 1f) * 0.12f
            drawBottomAnchoredSprite(breadImg, oven2Pos + Vector2(0f, -0.6f - bob), 1.0f, 1.0f, viewport)
            for (s in 0..2) {
                val sp = ((elapsed * 2f + s * 0.65f + 0.5f) % 1.5f) / 1.5f
                val sx = oven2Screen.x + sin(sp * 7f + s + 2f) * ppu * 0.4f
                val sy = oven2Screen.y - ppu * 0.3f - sp * ppu * 0.7f
                drawCircle(Color(0xFFFFF9C4).copy(alpha = (1f - sp) * 0.85f), ppu * 0.05f * (1f - sp), Offset(sx, sy))
            }
        }
    }

    // 5. South Door / Exit mat
    val matTL = viewport.toScreen(Vector2(6.8f, 8.2f))
    val matW = 2.4f * ppu
    val matH = 0.45f * ppu
    drawRoundRect(
        color = Color(0x663E2723),
        topLeft = matTL,
        size = Size(matW, matH),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawRoundRect(
        color = Color(0x99D4AF37),
        topLeft = matTL,
        size = Size(matW, matH),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 2f),
    )

    // 6. Draw Tables and Player with 2.5D Y-depth sorting
    fun drawStations() {
        // Left Table: Molcajete Molienda Station
        val molcajeteImg = sprites.v5.bakeryMolcajete ?: sprites.v5.minigameMolcajete
        if (molcajeteImg != null) {
            drawContactShadow(
                center = Vector2(3.2f, 5.55f),
                worldWidth = 2.65f,
                worldHeight = 0.52f,
                viewport = viewport,
                alpha = 0.45f,
            )
            drawBottomAnchoredSprite(
                image = molcajeteImg,
                bottomCenter = Vector2(3.2f, 5.55f),
                worldWidth = 2.66f,
                worldHeight = 2.5f,
                viewport = viewport,
            )
        }

        // Right Table: Dough Kneading Station
        val kneadImg = sprites.v5.bakeryKneadTable ?: sprites.v5.doughKneadStation
        if (kneadImg != null) {
            drawContactShadow(
                center = Vector2(12.8f, 5.55f),
                worldWidth = 2.9f,
                worldHeight = 0.52f,
                viewport = viewport,
                alpha = 0.45f,
            )
            drawBottomAnchoredSprite(
                image = kneadImg,
                bottomCenter = Vector2(12.8f, 5.55f),
                worldWidth = 2.93f,
                worldHeight = 2.5f,
                viewport = viewport,
            )
        }
    }

    val tableY = 5.5f
    if (player.position.y < tableY) {
        // Player is behind the tables
        drawPlayer(player, world, sprites, viewport, isHost = true)
        drawStations()
    } else {
        // Player is in front of the tables
        drawStations()
        drawPlayer(player, world, sprites, viewport, isHost = true)
    }

    // 7. Soft warm room vignette
    val centerScreen = Offset(origin.x + roomPixelW / 2f, origin.y + roomPixelH / 2f)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x22100802), Color(0x66080401)),
            center = centerScreen,
            radius = maxOf(roomPixelW, roomPixelH) * 0.75f,
        ),
        topLeft = origin,
        size = Size(roomPixelW, roomPixelH),
    )
}

private fun DrawScope.drawFarmWorld(
    world: FarmWorldState,
    sprites: WorldSprites,
    overview: Boolean,
) {
    val viewport = viewportFor(world, overview)
    drawRect(Color(0xFF172E24))

    drawContinuousGround(world, sprites, viewport)
    drawFarmPerimeterBackdrop(world, sprites, viewport)
    drawTerrainRegions(world, sprites, viewport)
    drawPathTiles(world, sprites, viewport)
    drawSoilPlots(world, viewport)
    drawWater(world, sprites, viewport)
    drawAmbientGrass(world, viewport, overview)
    drawFarmBoundary(world, sprites, viewport)
    drawGroundProps(world, sprites, viewport, overview)
    drawFishingRig(world, viewport)

    val treesById = world.trees.associateBy { it.id }
    val plotsById = world.plots.associateBy { it.id }
    val chickensById = world.chickens.associateBy { it.id }
    val animalsById = world.animals.associateBy { it.id }
    val eggsById = world.eggs.associateBy { it.id }
    val itemsById = world.groundItems.associateBy { it.id }
    val ambientById = world.ambientCreatures.associateBy { it.id }
    val factoriesById = world.factories.associateBy { it.id }
    val gatesById = world.gates.associateBy { it.id }
    val projectilesById = world.projectiles.associateBy { it.id }
    val ripplesById = world.ripples.associateBy { it.id }

    world.renderQueue().forEach { entry ->
        when (entry.type) {
            RenderEntityType.BARN -> drawBarn(world, sprites, viewport)
            RenderEntityType.FACTORY -> factoriesById[entry.id]?.let { drawFactory(it, world, sprites, viewport) }
            RenderEntityType.TREE -> treesById[entry.id]?.let { drawTree(it, world, sprites, viewport) }
            RenderEntityType.PLAYER -> drawPlayer(world.player, world, sprites, viewport, isHost = true)
            RenderEntityType.GUEST_PLAYER -> world.guestPlayer?.let { drawPlayer(it, world, sprites, viewport, isHost = false) }
            RenderEntityType.CHICKEN,
            RenderEntityType.CHICK,
            -> chickensById[entry.id]?.let { drawChicken(it, sprites, viewport) }
            RenderEntityType.COW,
            RenderEntityType.PIG,
            RenderEntityType.DOG,
            RenderEntityType.CAT,
            -> animalsById[entry.id]?.let { drawDomesticAnimal(it, sprites, viewport) }
            RenderEntityType.EGG -> eggsById[entry.id]?.let {
                drawContactShadow(
                    center = it.position + Vector2(0f, 0.16f),
                    worldWidth = 0.34f,
                    worldHeight = 0.11f,
                    viewport = viewport,
                    alpha = 0.19f,
                )
                drawBottomAnchoredSprite(
                    image = sprites.egg,
                    bottomCenter = it.position + Vector2(0f, 0.2f),
                    worldWidth = 0.58f,
                    worldHeight = 0.58f,
                    viewport = viewport,
                )
            }
            RenderEntityType.GROUND_ITEM -> itemsById[entry.id]?.let {
                drawGroundItem(it, world, sprites, viewport)
            }
            RenderEntityType.CROP -> plotsById[entry.id]?.let {
                val crop = it.crop ?: return@let
                val frame = when (crop.type) {
                    CropType.WHEAT -> sprites.v5.wheatCrops?.getOrNull(crop.stage.ordinal.coerceIn(0, 3))
                        ?: sprites.carrot[crop.stage.ordinal.coerceIn(sprites.carrot.indices)]
                    CropType.CARROT -> sprites.carrot[crop.stage.ordinal.coerceIn(sprites.carrot.indices)]
                }
                drawContactShadow(
                    center = it.position + Vector2(0f, it.size * 0.39f),
                    worldWidth = it.size * 0.7f,
                    worldHeight = it.size * 0.12f,
                    viewport = viewport,
                    alpha = 0.15f,
                )
                drawBottomAnchoredSprite(
                    image = frame,
                    bottomCenter = it.position + Vector2(0f, it.size * 0.44f),
                    worldWidth = it.size * 0.86f,
                    worldHeight = it.size * 0.86f,
                    viewport = viewport,
                )
            }
            RenderEntityType.BALL -> drawPetBall(world, sprites, viewport)
            // A faint caused by the fox dismounts the player in the engine. Hiding the parked
            // bicycle during that short pose prevents it from reading as the faint animation.
            RenderEntityType.BICYCLE -> if (world.player.faintTimer <= 0f) {
                drawBicycle(world.bicycle.position, sprites, viewport)
            }
            RenderEntityType.FOX -> drawFox(world, sprites, viewport)
            RenderEntityType.TRUCK -> drawTruck(world, sprites, viewport)
            RenderEntityType.COURIER -> drawCourier(world, entry.id, sprites, viewport)
            RenderEntityType.BIRD,
            RenderEntityType.BUTTERFLY,
            -> ambientById[entry.id]?.let { drawAmbientCreature(it, sprites, viewport) }
            RenderEntityType.HELPER -> drawHelper(world, sprites, viewport)
            RenderEntityType.GATE -> gatesById[entry.id]?.let { drawGate(it, sprites, viewport) }
            RenderEntityType.WHEELBARROW -> drawWheelbarrow(world.wheelbarrow, sprites, viewport)
            RenderEntityType.PROJECTILE -> projectilesById[entry.id]?.let { drawProjectile(it, sprites, viewport) }
            RenderEntityType.RIPPLE -> ripplesById[entry.id]?.let { drawRipple(it, viewport) }
        }
    }

    drawDayLighting(world, overview)
    if (overview) drawOverviewMarker(world, viewport)
    if (world.isPencilMode) drawArchitectGrid(world, viewport)
}

/**
 * A two-gradient colour grade gives the farm a readable time of day without a costly runtime
 * blur or per-sprite shader. Night is deliberately not modelled yet: late hours hold at a soft
 * twilight and very early hours transition directly into dawn.
 */
private fun DrawScope.drawDayLighting(world: FarmWorldState, overview: Boolean) {
    val hour = positiveMod(floor(world.secondsOfDay / 3_600f).toInt(), 24) +
        positiveFraction(world.secondsOfDay / 3_600f)
    val colors: List<Color>
    val sunColor: Color
    val sunStrength: Float
    val sunAtRight: Boolean
    when {
        hour < 5f -> {
            val t = smoothStep(hour / 5f)
            colors = listOf(
                lerpColor(Color(0x18E58A6A), Color(0x1FCF7E65), t),
                lerpColor(Color(0x14F0B36B), Color(0x24F0A05F), t),
            )
            sunColor = Color(0xFFFFB36F)
            sunStrength = 0.045f + t * 0.035f
            sunAtRight = false
        }
        hour < 8.5f -> {
            val t = smoothStep((hour - 5f) / 3.5f)
            colors = listOf(
                Color(0xFFFFB784).copy(alpha = lerp(0.13f, 0.025f, t)),
                Color(0xFFFFD36F).copy(alpha = lerp(0.1f, 0.018f, t)),
            )
            sunColor = Color(0xFFFFC06A)
            sunStrength = lerp(0.13f, 0.035f, t)
            sunAtRight = false
        }
        hour < 16f -> {
            colors = listOf(Color(0x08FFF6C4), Color(0x05FFE79C))
            sunColor = Color(0xFFFFF0B0)
            sunStrength = 0.025f
            sunAtRight = true
        }
        hour < 20.5f -> {
            val t = smoothStep((hour - 16f) / 4.5f)
            colors = listOf(
                Color(0xFFFFC16C).copy(alpha = lerp(0.025f, 0.15f, t)),
                Color(0xFFB96875).copy(alpha = lerp(0.012f, 0.105f, t)),
            )
            sunColor = Color(0xFFFF8B55)
            sunStrength = lerp(0.035f, 0.15f, t)
            sunAtRight = true
        }
        else -> {
            // Night is intentionally not implemented yet: hold a gentle warm sunset grade.
            colors = listOf(Color(0x25D77A67), Color(0x1FAF6C72))
            sunColor = Color(0xFFFFA168)
            sunStrength = 0.085f
            sunAtRight = true
        }
    }
    drawRect(
        brush = Brush.verticalGradient(colors = colors, startY = 0f, endY = size.height),
    )
    if (!overview || sunStrength > 0.06f) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    sunColor.copy(alpha = sunStrength),
                    sunColor.copy(alpha = sunStrength * 0.32f),
                    Color.Transparent,
                ),
                center = Offset(if (sunAtRight) size.width * 0.91f else size.width * 0.09f, size.height * 0.12f),
                radius = max(size.width, size.height) * 0.92f,
            ),
        )
    }
}

private fun DrawScope.viewportFor(world: FarmWorldState, overview: Boolean): WorldViewport {
    if (overview) {
        val pixelsPerUnit = min(
            size.width / world.bounds.width,
            size.height / world.bounds.height,
        ).coerceAtLeast(0.01f)
        val contentWidth = world.bounds.width * pixelsPerUnit
        val contentHeight = world.bounds.height * pixelsPerUnit
        val origin = Offset(
            (size.width - contentWidth) * 0.5f - world.bounds.left * pixelsPerUnit,
            (size.height - contentHeight) * 0.5f - world.bounds.top * pixelsPerUnit,
        )
        return WorldViewport(pixelsPerUnit, origin, world.bounds)
    }

    val visibleHeight = world.camera.baseViewportWorldSize.y / world.camera.zoom.coerceAtLeast(0.1f)
    val pixelsPerUnit = size.height / visibleHeight
    val visibleWidth = size.width / pixelsPerUnit
    val left = world.camera.center.x - visibleWidth * 0.5f
    val top = world.camera.center.y - visibleHeight * 0.5f
    return WorldViewport(
        pixelsPerUnit = pixelsPerUnit,
        origin = Offset(-left * pixelsPerUnit, -top * pixelsPerUnit),
        visibleBounds = CollisionRect(left, top, left + visibleWidth, top + visibleHeight),
    )
}

/** HD grass is sampled in large overlapping chunks; only visible chunks are submitted to Canvas. */
private fun DrawScope.drawContinuousGround(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val topLeft = viewport.toScreen(Vector2(world.bounds.left, world.bounds.top))
    val width = max(1, (world.bounds.width * viewport.pixelsPerUnit).roundToInt())
    val height = max(1, (world.bounds.height * viewport.pixelsPerUnit).roundToInt())
    clipPath(world.bounds.toPath(viewport)) {
        drawImage(
            image = sprites.farmGround,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(width, height),
            // Bilinear sampling prevents shimmer when the camera moves by sub-pixels. The source
            // texture already contains deliberate pixel-art clusters, so it remains visually crisp.
            filterQuality = FilterQuality.Medium,
            alpha = 0.32f,
        )
        val tileWorld = 10f
        val driftX = sin(world.animation.elapsedSeconds * 0.09f) * 0.025f
        val driftY = cos(world.animation.elapsedSeconds * 0.075f) * 0.018f
        val visible = viewport.visibleBounds.expanded(tileWorld)
        val firstColumn = floor((visible.left - driftX) / tileWorld).toInt()
        val lastColumn = ceil((visible.right - driftX) / tileWorld).toInt()
        val firstRow = floor((visible.top - driftY) / tileWorld).toInt()
        val lastRow = ceil((visible.bottom - driftY) / tileWorld).toInt()
        val tilePixels = max(2, (tileWorld * viewport.pixelsPerUnit).roundToInt() + 1)
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val worldPosition = Vector2(column * tileWorld + driftX, row * tileWorld + driftY)
            val screenPosition = viewport.toScreen(worldPosition)
            drawImage(
                image = sprites.grassHd,
                dstOffset = IntOffset(screenPosition.x.roundToInt(), screenPosition.y.roundToInt()),
                dstSize = IntSize(tilePixels, tilePixels),
                filterQuality = FilterQuality.Medium,
                alpha = 0.86f,
            )
        }
        drawRect(
            color = Color(0x0CFFF3B0),
            topLeft = topLeft,
            size = Size(width.toFloat(), height.toFloat()),
        )
    }
}

/**
 * The simulation still uses a compact rectangular world, but the art should read as a clearing
 * carved out of a much larger place.  This inexpensive backdrop keeps all decorative objects in
 * the existing world coordinate system: a dense tree belt closes the north/east/west horizons and
 * a gravel lane crosses behind the south gate.  Nothing here participates in collision.
 */
private fun DrawScope.drawFarmPerimeterBackdrop(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val playable = visualPlayableBounds(world)
    val visible = viewport.visibleBounds.expanded(5.5f)

    // Deep foliage underneath the sprite rows removes the obvious straight green map edge.
    val northBand = CollisionRect(world.bounds.left, world.bounds.top, world.bounds.right, playable.top + 0.45f)
    val westBand = CollisionRect(world.bounds.left, playable.top, playable.left + 0.45f, playable.bottom)
    val eastBand = CollisionRect(playable.right - 0.45f, playable.top, world.bounds.right, playable.bottom)
    listOf(northBand, westBand, eastBand).forEachIndexed { index, band ->
        if (!band.intersects(visible)) return@forEachIndexed
        val topLeft = viewport.toScreen(Vector2(band.left, band.top))
        drawRect(
            brush = Brush.verticalGradient(
                colors = if (index == 0) {
                    listOf(Color(0xFF173D2B), Color(0xFF285E35), Color(0xB438733A))
                } else {
                    listOf(Color(0xE6225431), Color(0xC5326E38), Color(0x8A477F3F))
                },
                startY = topLeft.y,
                endY = topLeft.y + band.height * viewport.pixelsPerUnit,
            ),
            topLeft = topLeft,
            size = Size(band.width * viewport.pixelsPerUnit, band.height * viewport.pixelsPerUnit),
        )
    }

    drawDecorativeGravelLane(world, playable, sprites, viewport)

    // Three staggered rows produce a continuous canopy while remaining cheap enough for overview.
    val decorativeTrees = buildList {
        val northSpacing = 2.45f
        repeat(3) { row ->
            val y = world.bounds.top + 1.45f + row * 1.15f
            val first = floor((visible.left - world.bounds.left) / northSpacing).toInt() - 1
            val last = ceil((visible.right - world.bounds.left) / northSpacing).toInt() + 1
            for (column in first..last) {
                val seed = 40_001 + row * 1_009 + column * 71
                val x = world.bounds.left + (column + 0.5f) * northSpacing + (hash01(seed) - 0.5f) * 0.72f
                add(DecorativeTree(Vector2(x, y + hash01(seed + 9) * 0.42f), seed))
            }
        }
        val sideSpacing = 2.55f
        val firstRow = floor((visible.top - playable.top) / sideSpacing).toInt() - 1
        val lastRow = ceil((visible.bottom - playable.top) / sideSpacing).toInt() + 1
        for (row in firstRow..lastRow) {
            val y = playable.top + (row + 0.5f) * sideSpacing
            repeat(2) { depth ->
                val westSeed = 51_007 + row * 103 + depth * 991
                val eastSeed = 72_011 + row * 109 + depth * 997
                add(
                    DecorativeTree(
                        Vector2(
                            world.bounds.left + 1.15f + depth * 1.25f + (hash01(westSeed) - 0.5f) * 0.48f,
                            y + (hash01(westSeed + 5) - 0.5f) * 0.72f,
                        ),
                        westSeed,
                    ),
                )
                add(
                    DecorativeTree(
                        Vector2(
                            world.bounds.right - 1.15f - depth * 1.25f + (hash01(eastSeed) - 0.5f) * 0.48f,
                            y + (hash01(eastSeed + 5) - 0.5f) * 0.72f,
                        ),
                        eastSeed,
                    ),
                )
            }
        }
    }
    decorativeTrees
        .asSequence()
        .filter { isNearViewport(it.position, 4.8f, viewport) }
        .sortedBy { it.position.y }
        .forEach { tree ->
            val breeze = sin(world.animation.elapsedSeconds * 0.31f + tree.seed * 0.017f)
            val image = when {
                breeze < -0.42f -> sprites.treeBreezeLeft
                breeze > 0.42f -> sprites.treeBreezeRight
                else -> sprites.treeIdle
            }
            val scale = 0.86f + hash01(tree.seed + 31) * 0.2f
            drawContactShadow(
                center = tree.position + Vector2(0f, 0.43f),
                worldWidth = 2.35f * scale,
                worldHeight = 0.55f,
                viewport = viewport,
                alpha = 0.2f,
            )
            drawBottomAnchoredSprite(
                image = image,
                bottomCenter = tree.position + Vector2(0f, 0.55f),
                worldWidth = 4.7f * scale,
                worldHeight = 5.1f * scale,
                viewport = viewport,
                alpha = 0.97f,
            )
        }
}

private data class DecorativeTree(val position: Vector2, val seed: Int)

/** A broad, softly ragged service road behind the lower gate. */
private fun DrawScope.drawDecorativeGravelLane(
    world: FarmWorldState,
    playable: CollisionRect,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val centerY = (playable.bottom + world.bounds.bottom) * 0.5f + 0.28f
    val halfWidth = max(1.35f, (world.bounds.bottom - playable.bottom) * 0.39f)
    val nodes = listOf(
        RibbonNode(Vector2(world.bounds.left - 1f, centerY + 0.18f), halfWidth),
        RibbonNode(Vector2(world.bounds.center.x * 0.54f, centerY - 0.14f), halfWidth * 1.05f),
        RibbonNode(Vector2(world.bounds.center.x, centerY + 0.08f), halfWidth * 0.98f),
        RibbonNode(Vector2(world.bounds.center.x * 1.47f, centerY - 0.2f), halfWidth * 1.04f),
        RibbonNode(Vector2(world.bounds.right + 1f, centerY + 0.12f), halfWidth),
    )
    val road = organicRibbonFromNodes(nodes, viewport, edgeExpand = 0f, seed = 91_117)
    val feather = organicRibbonFromNodes(nodes, viewport, edgeExpand = 0.42f, seed = 91_117)
    val core = organicRibbonFromNodes(nodes, viewport, edgeExpand = -0.2f, seed = 91_117)
    drawPath(feather, Color(0x71516835))
    drawPath(road, Color(0xFF9B805D))
    drawPath(core, Color(0x459E8B6D))
    val bounds = CollisionRect(world.bounds.left, centerY - halfWidth - 0.5f, world.bounds.right, centerY + halfWidth + 0.5f)
    clipPath(road) {
        drawTiledWorldTexture(sprites.dirt, bounds, viewport, tileWorld = 4.2f, alpha = 0.34f)
        val first = floor(world.bounds.left / 0.78f).toInt()
        val last = ceil(world.bounds.right / 0.78f).toInt()
        for (column in first..last) {
            val seed = 96_001 + column * 313
            val x = column * 0.78f + hash01(seed) * 0.54f
            val y = centerY + (hash01(seed + 17) - 0.5f) * halfWidth * 1.62f
            val center = viewport.toScreen(Vector2(x, y))
            val radius = max(0.6f, viewport.pixelsPerUnit * (0.025f + hash01(seed + 29) * 0.025f))
            drawOval(
                color = if (column % 3 == 0) Color(0x56675749) else Color(0x4FD5C29F),
                topLeft = center - Offset(radius, radius * 0.58f),
                size = Size(radius * 2f, radius * 1.16f),
            )
        }
        // Faint wheel tracks tell the player where delivery vehicles enter without hard outlines.
        listOf(centerY - halfWidth * 0.36f, centerY + halfWidth * 0.36f).forEach { trackY ->
            drawLine(
                color = Color(0x2747372B),
                start = viewport.toScreen(Vector2(world.bounds.left, trackY)),
                end = viewport.toScreen(Vector2(world.bounds.right, trackY + 0.05f)),
                strokeWidth = max(0.8f, viewport.pixelsPerUnit * 0.075f),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Visible farm limit. The south rail leaves a generous delivery gate at the road centre. */
private fun DrawScope.drawFarmBoundary(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val playable = visualPlayableBounds(world)
    val thickness = 0.2f
    val gateHalfWidth = 2.25f
    val segments = listOf(
        CollisionRect(playable.left, playable.top - thickness * 0.5f, playable.right, playable.top + thickness * 0.5f),
        CollisionRect(playable.left - thickness * 0.5f, playable.top, playable.left + thickness * 0.5f, playable.bottom),
        CollisionRect(playable.right - thickness * 0.5f, playable.top, playable.right + thickness * 0.5f, playable.bottom),
        CollisionRect(playable.left, playable.bottom - thickness * 0.5f, world.bounds.center.x - gateHalfWidth, playable.bottom + thickness * 0.5f),
        CollisionRect(world.bounds.center.x + gateHalfWidth, playable.bottom - thickness * 0.5f, playable.right, playable.bottom + thickness * 0.5f),
    ).filter { it.expanded(1f).intersects(viewport.visibleBounds) }
    drawConnectedFences(segments, sprites, viewport)

    // Paired posts make the opening unmistakable in both the camera and the overview map.
    drawFencePost(Vector2(world.bounds.center.x - gateHalfWidth, playable.bottom), viewport)
    drawFencePost(Vector2(world.bounds.center.x + gateHalfWidth, playable.bottom), viewport)
}

/**
 * A deliberately asymmetric inset reserves scenic space for woodland and the delivery lane.  The
 * engine uses the same proportions for its invisible collision barrier; keeping this helper local
 * also makes old saves render correctly before their generated obstacles are refreshed.
 */
private fun visualPlayableBounds(world: FarmWorldState): CollisionRect {
    val sideInset = min(4f, world.bounds.width * 0.065f)
    val northInset = min(4f, world.bounds.height * 0.085f)
    val southInset = min(5f, world.bounds.height * 0.105f)
    return CollisionRect(
        world.bounds.left + sideInset,
        world.bounds.top + northInset,
        world.bounds.right - sideInset,
        world.bounds.bottom - southInset,
    )
}

/**
 * Draws the farm road as one continuous irregular ribbon. The simulation still owns several
 * collision circles, but those implementation shapes must never become visible as brown discs.
 * Sand touching water is handled by [drawWater] so every pond gets one continuous shoreline.
 */
private fun DrawScope.drawTerrainRegions(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val dirtRegions = world.terrainRegions
        .filter { it.terrain == TerrainType.DIRT }
        .sortedBy { it.id }
    if (dirtRegions.isNotEmpty() && dirtRegions.any {
            it.shape.worldBounds().expanded(3f).intersects(viewport.visibleBounds)
        }
    ) {
        val roadBounds = boundsOfTerrain(dirtRegions).expanded(0.45f)
        val softEdge = organicRibbonPath(dirtRegions, viewport, edgeExpand = 0.34f, seed = 5_711)
        val road = organicRibbonPath(dirtRegions, viewport, edgeExpand = 0f, seed = 5_711)
        val innerRoad = organicRibbonPath(dirtRegions, viewport, edgeExpand = -0.13f, seed = 5_711)

        // A subdued mossy feather replaces hard outlines and lets the path settle into the grass.
        drawPath(softEdge, Color(0x664F6335))
        drawPath(road, Color(0xFFB98350))
        drawPath(innerRoad, Color(0x36E9C07C))
        clipPath(road) {
            drawStretchedTexture(sprites.dirt, roadBounds, viewport, alpha = 0.24f)
            drawNaturalRoadGrain(dirtRegions, 5_711, viewport)
        }
    }

    // Wood decks and any future sand patches that are not shores keep their own organic shape.
    world.terrainRegions.asSequence()
        .filter { region ->
            region.terrain == TerrainType.WOOD ||
                (region.terrain == TerrainType.SAND && world.waterBodies.none {
                    it.shape.distanceTo(region.shape.worldCenter()) < 1.2f
                })
        }
        .filter { it.shape.worldBounds().expanded(0.5f).intersects(viewport.visibleBounds) }
        .forEach { region ->
            val edge = region.shape.toOrganicPath(viewport, region.id * 31 + 5, expand = 0.18f)
            val path = region.shape.toOrganicPath(viewport, region.id * 31 + 5)
            drawPath(edge, if (region.terrain == TerrainType.WOOD) Color(0x66533525) else Color(0x55687942))
            drawPath(path, if (region.terrain == TerrainType.WOOD) Color(0xFF95613D) else Color(0xFFE2C47A))
            clipPath(path) {
                if (region.terrain == TerrainType.WOOD) {
                    drawWoodGrain(region.shape.worldBounds(), viewport)
                } else {
                    drawNaturalGroundGrain(region.shape.worldBounds(), region.id, region.terrain, viewport)
                }
            }
        }
}

private fun DrawScope.drawPathTiles(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    if (world.pathTiles.isEmpty()) return
    val visible = viewport.visibleBounds.expanded(1.5f)
    val tiles = world.pathTiles.filter { it.collider.intersects(visible) }
    if (tiles.isEmpty()) return

    val tileCoords = world.pathTiles.map { it.cellX to it.cellY }.toSet()

    // 1. Pass 1: outer soft mossy border only on exterior edges
    tiles.forEach { tile ->
        val cx = tile.cellX
        val cy = tile.cellY
        val x = cx.toFloat()
        val y = cy.toFloat()
        val hasN = (cx to cy - 1) in tileCoords
        val hasS = (cx to cy + 1) in tileCoords
        val hasW = (cx - 1 to cy) in tileCoords
        val hasE = (cx + 1 to cy) in tileCoords

        val outL = if (hasW) x else x - 0.16f
        val outR = if (hasE) x + 1f else x + 1.16f
        val outT = if (hasN) y else y - 0.16f
        val outB = if (hasS) y + 1f else y + 1.16f

        val outerTL = viewport.toScreen(Vector2(outL, outT))
        val outerBR = viewport.toScreen(Vector2(outR, outB))
        drawRect(
            color = Color(0x60566336),
            topLeft = outerTL,
            size = Size(outerBR.x - outerTL.x, outerBR.y - outerTL.y),
        )
    }

    // 2. Pass 2: solid continuous gravel body that overlaps seamlessly
    val gravelColor = Color(0xFFB59368)
    tiles.forEach { tile ->
        val cx = tile.cellX
        val cy = tile.cellY
        val x = cx.toFloat()
        val y = cy.toFloat()
        val hasN = (cx to cy - 1) in tileCoords
        val hasS = (cx to cy + 1) in tileCoords
        val hasW = (cx - 1 to cy) in tileCoords
        val hasE = (cx + 1 to cy) in tileCoords

        val l = if (hasW) x - 0.08f else x
        val r = if (hasE) x + 1.08f else x + 1f
        val t = if (hasN) y - 0.08f else y
        val b = if (hasS) y + 1.08f else y + 1f

        val tl = viewport.toScreen(Vector2(l, t))
        val br = viewport.toScreen(Vector2(r, b))
        drawRect(
            color = gravelColor,
            topLeft = tl,
            size = Size(br.x - tl.x, br.y - tl.y),
        )

        // Bridges between adjacent tiles
        if (hasE) {
            val bridgeTL = viewport.toScreen(Vector2(x + 0.5f, y))
            val bridgeBR = viewport.toScreen(Vector2(x + 1.5f, y + 1f))
            drawRect(gravelColor, bridgeTL, Size(bridgeBR.x - bridgeTL.x, bridgeBR.y - bridgeTL.y))
        }
        if (hasS) {
            val bridgeTL = viewport.toScreen(Vector2(x, y + 0.5f))
            val bridgeBR = viewport.toScreen(Vector2(x + 1f, y + 1.5f))
            drawRect(gravelColor, bridgeTL, Size(bridgeBR.x - bridgeTL.x, bridgeBR.y - bridgeTL.y))
        }

        // Inner lighter wear layer
        val coreL = if (hasW) x else x + 0.15f
        val coreR = if (hasE) x + 1f else x + 0.85f
        val coreT = if (hasN) y else y + 0.15f
        val coreB = if (hasS) y + 1f else y + 0.85f
        val cTL = viewport.toScreen(Vector2(coreL, coreT))
        val cBR = viewport.toScreen(Vector2(coreR, coreB))
        drawRect(
            color = Color(0x3AE8CA94),
            topLeft = cTL,
            size = Size(cBR.x - cTL.x, cBR.y - cTL.y),
        )

        // Pebble granules and stone texture specks
        val seed = tile.cellX * 73_856_093 xor tile.cellY * 19_349_663
        repeat(5) { i ->
            val pSeed = seed + i * 1_013
            val px = x + 0.18f + hash01(pSeed) * 0.64f
            val py = y + 0.18f + hash01(pSeed + 31) * 0.64f
            val pScreen = viewport.toScreen(Vector2(px, py))
            val pRad = max(0.9f, viewport.pixelsPerUnit * (0.024f + hash01(pSeed + 7) * 0.028f))
            val pebbleColor = if (i % 2 == 0) Color(0x66654F38) else Color(0x75D8C6A2)
            drawCircle(
                color = pebbleColor,
                radius = pRad,
                center = Offset(pScreen.x, pScreen.y),
            )
        }
    }
}

/** Samples grain along the road centreline instead of scanning its entire 60x40 bounding box. */
private fun DrawScope.drawNaturalRoadGrain(
    regions: List<TerrainRegion>,
    seed: Int,
    viewport: WorldViewport,
) {
    if (regions.size < 2) return
    val nodes = regions.map { region ->
        val radius = when (val shape = region.shape) {
            is CollisionCircle -> shape.radius * 0.82f
            is CollisionRect -> min(shape.width, shape.height) * 0.41f
        }
        RibbonNode(region.shape.worldCenter(), radius)
    }
    for (segmentIndex in 0 until nodes.lastIndex) {
        val start = nodes[segmentIndex]
        val end = nodes[segmentIndex + 1]
        val segment = end.position - start.position
        val distance = start.position.distanceTo(end.position)
        val tangent = segment.normalized()
        val normal = Vector2(-tangent.y, tangent.x)
        val count = max(3, ceil(distance / 0.48f).toInt())
        repeat(count) { sampleIndex ->
            val t = (sampleIndex + 0.5f) / count
            val sampleSeed = seed + segmentIndex * 911 + sampleIndex * 47
            val width = lerp(start.halfWidth, end.halfWidth, t)
            val across = (hash01(sampleSeed) - 0.5f) * width * 1.72f
            val along = (hash01(sampleSeed + 17) - 0.5f) * 0.32f
            val point = start.position + segment * t + normal * across + tangent * along
            val screen = viewport.toScreen(point)
            val radius = max(0.55f, viewport.pixelsPerUnit * (0.018f + hash01(sampleSeed + 31) * 0.016f))
            drawCircle(Color(0x304B3526), radius, screen)
        }
    }
}

/** Builds a variable-width Catmull-Rom ribbon with stable, tiny edge irregularities. */
private fun organicRibbonPath(
    regions: List<TerrainRegion>,
    viewport: WorldViewport,
    edgeExpand: Float,
    seed: Int,
): Path {
    if (regions.size == 1) {
        return regions.first().shape.toOrganicPath(viewport, seed, expand = edgeExpand)
    }
    val nodes = regions.map { region ->
        RibbonNode(
            position = region.shape.worldCenter(),
            halfWidth = when (val shape = region.shape) {
                is CollisionCircle -> shape.radius * 0.86f
                is CollisionRect -> min(shape.width, shape.height) * 0.43f
            },
        )
    }
    val samples = ArrayList<RibbonNode>((nodes.size - 1) * 7 + 1)
    for (segment in 0 until nodes.lastIndex) {
        val a = nodes[max(0, segment - 1)]
        val b = nodes[segment]
        val c = nodes[segment + 1]
        val d = nodes[min(nodes.lastIndex, segment + 2)]
        repeat(7) { step ->
            val t = step / 7f
            val sampleIndex = segment * 7 + step
            val jitter = (hash01(seed + sampleIndex * 97) - 0.5f) * 0.18f
            samples += RibbonNode(
                position = Vector2(
                    catmullRom(a.position.x, b.position.x, c.position.x, d.position.x, t),
                    catmullRom(a.position.y, b.position.y, c.position.y, d.position.y, t),
                ),
                halfWidth = max(
                    0.28f,
                    lerp(b.halfWidth, c.halfWidth, smoothStep(t)) + edgeExpand + jitter,
                ),
            )
        }
    }
    samples += nodes.last().copy(halfWidth = max(0.28f, nodes.last().halfWidth + edgeExpand))

    val left = ArrayList<Vector2>(samples.size)
    val right = ArrayList<Vector2>(samples.size)
    samples.forEachIndexed { index, sample ->
        val previous = samples[max(0, index - 1)].position
        val next = samples[min(samples.lastIndex, index + 1)].position
        val tangent = next - previous
        val length = sqrt(tangent.x * tangent.x + tangent.y * tangent.y).coerceAtLeast(0.001f)
        val normal = Vector2(-tangent.y / length, tangent.x / length)
        left += sample.position + normal * sample.halfWidth
        right += sample.position - normal * sample.halfWidth
    }
    return Path().apply {
        val first = viewport.toScreen(left.first())
        moveTo(first.x, first.y)
        left.drop(1).forEach { point ->
            val screen = viewport.toScreen(point)
            lineTo(screen.x, screen.y)
        }
        right.asReversed().forEach { point ->
            val screen = viewport.toScreen(point)
            lineTo(screen.x, screen.y)
        }
        close()
    }
}

/** Variant used by decorative roads that do not need simulation-backed [TerrainRegion] objects. */
private fun organicRibbonFromNodes(
    nodes: List<RibbonNode>,
    viewport: WorldViewport,
    edgeExpand: Float,
    seed: Int,
): Path {
    if (nodes.isEmpty()) return Path()
    if (nodes.size == 1) {
        return CollisionCircle(
            nodes.first().position,
            max(0.25f, nodes.first().halfWidth + edgeExpand),
        ).toOrganicPath(viewport, seed)
    }
    val samples = ArrayList<RibbonNode>((nodes.size - 1) * 7 + 1)
    for (segment in 0 until nodes.lastIndex) {
        val a = nodes[max(0, segment - 1)]
        val b = nodes[segment]
        val c = nodes[segment + 1]
        val d = nodes[min(nodes.lastIndex, segment + 2)]
        repeat(7) { step ->
            val t = step / 7f
            val sampleIndex = segment * 7 + step
            val jitter = (hash01(seed + sampleIndex * 97) - 0.5f) * 0.18f
            samples += RibbonNode(
                position = Vector2(
                    catmullRom(a.position.x, b.position.x, c.position.x, d.position.x, t),
                    catmullRom(a.position.y, b.position.y, c.position.y, d.position.y, t),
                ),
                halfWidth = max(
                    0.28f,
                    lerp(b.halfWidth, c.halfWidth, smoothStep(t)) + edgeExpand + jitter,
                ),
            )
        }
    }
    samples += nodes.last().copy(halfWidth = max(0.28f, nodes.last().halfWidth + edgeExpand))

    val left = ArrayList<Vector2>(samples.size)
    val right = ArrayList<Vector2>(samples.size)
    samples.forEachIndexed { index, sample ->
        val previous = samples[max(0, index - 1)].position
        val next = samples[min(samples.lastIndex, index + 1)].position
        val tangent = next - previous
        val length = tangent.length().coerceAtLeast(0.001f)
        val normal = Vector2(-tangent.y / length, tangent.x / length)
        left += sample.position + normal * sample.halfWidth
        right += sample.position - normal * sample.halfWidth
    }
    return Path().apply {
        val first = viewport.toScreen(left.first())
        moveTo(first.x, first.y)
        left.drop(1).forEach { point ->
            val screen = viewport.toScreen(point)
            lineTo(screen.x, screen.y)
        }
        right.asReversed().forEach { point ->
            val screen = viewport.toScreen(point)
            lineTo(screen.x, screen.y)
        }
        close()
    }
}

private data class RibbonNode(val position: Vector2, val halfWidth: Float)

private fun isInsideRibbon(point: Vector2, nodes: List<RibbonNode>): Boolean {
    if (nodes.isEmpty()) return false
    if (nodes.size == 1) return point.distanceTo(nodes.first().position) <= nodes.first().halfWidth
    for (index in 0 until nodes.lastIndex) {
        val start = nodes[index]
        val end = nodes[index + 1]
        val segment = end.position - start.position
        val lengthSquared = segment.lengthSquared().coerceAtLeast(0.0001f)
        val relative = point - start.position
        val t = ((relative.x * segment.x + relative.y * segment.y) / lengthSquared).coerceIn(0f, 1f)
        val nearest = start.position + segment * t
        val width = lerp(start.halfWidth, end.halfWidth, t)
        if (point.distanceTo(nearest) <= width) return true
    }
    return false
}

private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (
        2f * p1 +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        )
}

private fun boundsOfTerrain(regions: List<TerrainRegion>): CollisionRect = CollisionRect(
    left = regions.minOf { it.shape.worldBounds().left },
    top = regions.minOf { it.shape.worldBounds().top },
    right = regions.maxOf { it.shape.worldBounds().right },
    bottom = regions.maxOf { it.shape.worldBounds().bottom },
)

private fun DrawScope.drawNaturalGroundGrain(
    bounds: CollisionRect,
    seed: Int,
    terrain: TerrainType,
    viewport: WorldViewport,
) {
    val spacing = if (terrain == TerrainType.SAND) 1.2f else 1.05f
    val dotColor = if (terrain == TerrainType.SAND) Color(0x315F673A) else Color(0x304B3526)
    val firstX = floor(bounds.left / spacing).toInt()
    val lastX = ceil(bounds.right / spacing).toInt()
    val firstY = floor(bounds.top / spacing).toInt()
    val lastY = ceil(bounds.bottom / spacing).toInt()
    for (y in firstY..lastY) {
        for (x in firstX..lastX) {
            val px = x * spacing + hash01(seed + x * 71 + y * 17) * spacing
            val py = y * spacing + hash01(seed + x * 13 + y * 89) * spacing
            val screen = viewport.toScreen(Vector2(px, py))
            val radius = max(0.55f, viewport.pixelsPerUnit * (0.018f + hash01(x * 5 + y) * 0.016f))
            drawCircle(dotColor, radius, screen)
        }
    }
}

private fun DrawScope.drawWoodGrain(bounds: CollisionRect, viewport: WorldViewport) {
    val start = floor(bounds.top / 0.34f).toInt()
    val end = ceil(bounds.bottom / 0.34f).toInt()
    for (row in start..end) {
        val y = row * 0.34f
        drawLine(
            color = if (row % 2 == 0) Color(0x35522F21) else Color(0x28E5B16D),
            start = viewport.toScreen(Vector2(bounds.left, y)),
            end = viewport.toScreen(Vector2(bounds.right, y)),
            strokeWidth = max(0.6f, viewport.pixelsPerUnit * 0.035f),
        )
    }
}

/** Adjacent tilled cells overlap into one soil patch and share continuous furrows. */
private fun DrawScope.drawSoilPlots(world: FarmWorldState, viewport: WorldViewport) {
    val tilled = world.plots.filter {
        it.soil == SoilState.TILLED && it.collider.expanded(0.7f).intersects(viewport.visibleBounds)
    }
    connectedPlotGroups(tilled).forEachIndexed { groupIndex, group ->
        val soilPath = Path()
        val shadowPath = Path()
        group.forEach { plot ->
            val rect = plot.collider.expanded(0.09f)
            val topLeft = viewport.toScreen(Vector2(rect.left, rect.top))
            val bottomRight = viewport.toScreen(Vector2(rect.right, rect.bottom))
            val corner = viewport.pixelsPerUnit * 0.23f
            soilPath.addRoundRect(RoundRect(Rect(topLeft, bottomRight), CornerRadius(corner, corner)))
            val shadowOffset = viewport.pixelsPerUnit * 0.07f
            shadowPath.addRoundRect(RoundRect(Rect(topLeft + Offset(0f, shadowOffset), bottomRight + Offset(0f, shadowOffset)), CornerRadius(corner, corner)))
        }

        drawPath(shadowPath, Color(0x66512F22))
        val avgMoisture = group.map { it.moisture }.average().toFloat().coerceIn(0f, 1f)
        // Dry soil: warm dusty earth (0xFFA5734C), Wet soil: deep dark chocolate mud (0xFF381F12)
        val soilColor = lerpColor(Color(0xFFA5734C), Color(0xFF381F12), avgMoisture)
        drawPath(soilPath, soilColor)

        // Draw individual plot moisture gradient / tint if within the group some are wetter
        group.forEach { plot ->
            if (plot.moisture >= 0.45f) {
                val rect = plot.collider.expanded(0.08f)
                val topLeft = viewport.toScreen(Vector2(rect.left, rect.top))
                val bottomRight = viewport.toScreen(Vector2(rect.right, rect.bottom))
                val corner = viewport.pixelsPerUnit * 0.22f
                val wetAlpha = ((plot.moisture - 0.4f) / 0.6f).coerceIn(0f, 0.7f)
                drawRoundRect(
                    color = Color(0xFF281309).copy(alpha = wetAlpha),
                    topLeft = topLeft,
                    size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
        }

        val groupBounds = boundsOfPlots(group).expanded(0.2f)
        clipPath(soilPath) {
            val firstRow = floor(groupBounds.top / 0.33f).toInt()
            val lastRow = ceil(groupBounds.bottom / 0.33f).toInt()
            for (row in firstRow..lastRow) {
                val y = row * 0.33f
                val wave = sin(row * 1.7f + groupIndex) * 0.04f
                val furrow = Path().apply {
                    val start = viewport.toScreen(Vector2(groupBounds.left, y + wave))
                    val oneThird = viewport.toScreen(Vector2(groupBounds.left + groupBounds.width * 0.34f, y - wave))
                    val twoThirds = viewport.toScreen(Vector2(groupBounds.left + groupBounds.width * 0.68f, y + wave))
                    val end = viewport.toScreen(Vector2(groupBounds.right, y - wave))
                    moveTo(start.x, start.y)
                    cubicTo(oneThird.x, oneThird.y, twoThirds.x, twoThirds.y, end.x, end.y)
                }
                // Furrow color: lighter dusty brown when dry, deep trench black-brown when wet
                val furrowColor = lerpColor(Color(0x446B4633), Color(0x88200E06), avgMoisture)
                drawPath(
                    furrow,
                    color = furrowColor,
                    style = Stroke(
                        width = max(0.75f, viewport.pixelsPerUnit * 0.038f),
                        cap = StrokeCap.Round,
                    ),
                )
                // If wet, draw glistening specular water sheen in furrow troughs
                if (avgMoisture > 0.45f) {
                    val sheenAlpha = ((avgMoisture - 0.45f) * 0.65f).coerceIn(0f, 0.45f)
                    drawPath(
                        furrow,
                        color = Color(0xFF88D8F0).copy(alpha = sheenAlpha),
                        style = Stroke(
                            width = max(0.45f, viewport.pixelsPerUnit * 0.016f),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
        }
    }

    drawGhostPlacementPreview(world, viewport)
}

private fun DrawScope.drawGhostPlacementPreview(
    world: FarmWorldState,
    viewport: WorldViewport,
) {
    val player = world.player
    if (player.isMounted) return
    val dir = if (player.movementDirection != Vector2.ZERO) player.movementDirection.normalized() else player.facing.vector()
    val pulse = (sin(world.animation.elapsedSeconds * 4.5f) * 0.5f + 0.5f)

    when (player.selectedTool) {
        ToolType.HOE -> {
            val plotSize = 1f
            val targetPos = snapToTileCenter(player.position + dir * 1.18f, plotSize)
            val candidateRect = CollisionRect(
                targetPos.x - plotSize * 0.5f,
                targetPos.y - plotSize * 0.5f,
                targetPos.x + plotSize * 0.5f,
                targetPos.y + plotSize * 0.5f,
            )
            if (!candidateRect.intersects(viewport.visibleBounds)) return
            val existingPlot = world.plots.firstOrNull { it.soil == SoilState.TILLED && it.collider.distanceTo(targetPos) < 0.35f }
            val topLeft = viewport.toScreen(Vector2(candidateRect.left, candidateRect.top))
            val bottomRight = viewport.toScreen(Vector2(candidateRect.right, candidateRect.bottom))
            val corner = viewport.pixelsPerUnit * 0.2f
            if (existingPlot != null) {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0x66FF5722),
                    strokeColor = Color(0xDDFF7043),
                    pulse = pulse,
                )
            } else {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0xFF9B6547).copy(alpha = 0.38f + pulse * 0.15f),
                    strokeColor = Color(0xFF8AE874).copy(alpha = 0.75f + pulse * 0.25f),
                    pulse = pulse,
                )
            }
        }
        ToolType.PATH_TOOL -> {
            val raw = player.position + dir * 1.18f
            val cellX = floor(raw.x).toInt()
            val cellY = floor(raw.y).toInt()
            val candidateRect = CollisionRect(cellX.toFloat(), cellY.toFloat(), cellX + 1f, cellY + 1f)
            if (!candidateRect.intersects(viewport.visibleBounds)) return
            val existingPath = world.pathTiles.firstOrNull { it.cellX == cellX && it.cellY == cellY }
            val topLeft = viewport.toScreen(Vector2(candidateRect.left, candidateRect.top))
            val bottomRight = viewport.toScreen(Vector2(candidateRect.right, candidateRect.bottom))
            val corner = viewport.pixelsPerUnit * 0.22f
            if (existingPath != null) {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0x66FF3D00),
                    strokeColor = Color(0xFFFF6E40),
                    pulse = pulse,
                )
            } else {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0xFFB59368).copy(alpha = 0.45f + pulse * 0.15f),
                    strokeColor = Color(0xFF76D275).copy(alpha = 0.8f + pulse * 0.2f),
                    pulse = pulse,
                )
            }
        }
        ToolType.FENCE -> {
            val raw = player.position + dir * 1.18f
            val target = snapToTileCenter(raw, FENCE_TILE_SIZE)
            val halfSize = FENCE_TILE_SIZE * 0.46f
            val collider = CollisionRect(target.x - halfSize, target.y - halfSize, target.x + halfSize, target.y + halfSize)
            if (!collider.expanded(1f).intersects(viewport.visibleBounds)) return
            val existingFence = world.staticObstacles.firstOrNull {
                it.kind == ObstacleKind.FENCE && shapeCenter(it.collider).distanceSquaredTo(target) < 0.2f
            }
            val topLeft = viewport.toScreen(Vector2(collider.left, collider.top))
            val bottomRight = viewport.toScreen(Vector2(collider.right, collider.bottom))
            val corner = viewport.pixelsPerUnit * 0.12f
            if (existingFence != null) {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0x66FF3D00),
                    strokeColor = Color(0xFFFF6E40),
                    pulse = pulse,
                )
            } else {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0x889E7A5A).copy(alpha = 0.5f + pulse * 0.2f),
                    strokeColor = Color(0xFF81C784).copy(alpha = 0.85f + pulse * 0.15f),
                    pulse = pulse,
                )
            }
        }
        ToolType.SEED_BAG -> {
            val targetPos = snapToTileCenter(player.position + dir * 1.18f, 1f)
            val candidateRect = CollisionRect(targetPos.x - 0.5f, targetPos.y - 0.5f, targetPos.x + 0.5f, targetPos.y + 0.5f)
            val targetPlot = world.plots.firstOrNull { it.soil == SoilState.TILLED && it.collider.distanceTo(targetPos) < 0.35f }
            if (targetPlot != null && targetPlot.crop == null && candidateRect.intersects(viewport.visibleBounds)) {
                val topLeft = viewport.toScreen(Vector2(candidateRect.left, candidateRect.top))
                val bottomRight = viewport.toScreen(Vector2(candidateRect.right, candidateRect.bottom))
                val corner = viewport.pixelsPerUnit * 0.2f
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0x554CAF50),
                    strokeColor = Color(0xFF81C784),
                    pulse = pulse,
                )
            }
        }
        ToolType.BLUEPRINT_DAIRY,
        ToolType.BLUEPRINT_SLAUGHTERHOUSE,
        ToolType.BLUEPRINT_EGG_PACKER,
        ToolType.BLUEPRINT_VEGGIE_PACKER,
        ToolType.BLUEPRINT_BAKERY,
        ToolType.BLUEPRINT_FISH_PROCESSOR -> {
            val factoryType = when (player.selectedTool) {
                ToolType.BLUEPRINT_DAIRY -> FactoryType.DAIRY
                ToolType.BLUEPRINT_SLAUGHTERHOUSE -> FactoryType.SLAUGHTERHOUSE
                ToolType.BLUEPRINT_EGG_PACKER -> FactoryType.EGG_PACKER
                ToolType.BLUEPRINT_VEGGIE_PACKER -> FactoryType.VEGGIE_PACKER
                ToolType.BLUEPRINT_BAKERY -> FactoryType.BAKERY
                ToolType.BLUEPRINT_FISH_PROCESSOR -> FactoryType.FISH_PROCESSOR
                else -> return
            }
            val raw = player.position + dir * 2.2f
            val cellX = floor(raw.x - factoryType.defaultWidth * 0.5f).toInt()
            val cellY = floor(raw.y - factoryType.defaultHeight * 0.5f).toInt()
            val candidateRect = CollisionRect(
                cellX.toFloat(),
                cellY.toFloat(),
                (cellX + factoryType.defaultWidth).toFloat(),
                (cellY + factoryType.defaultHeight).toFloat(),
            )
            if (!candidateRect.intersects(viewport.visibleBounds)) return
            val blocked = candidateRect.left < 5f || candidateRect.right > 59f || candidateRect.top < 5f || candidateRect.bottom > 42f ||
                world.waterBodies.any { rectIntersectsShape(candidateRect, it.shape) } ||
                candidateRect.intersects(world.barn.collider) ||
                world.factories.any { candidateRect.intersects(it.collider) } ||
                world.trees.any { candidateRect.intersects(it.collider) }

            val topLeft = viewport.toScreen(Vector2(candidateRect.left, candidateRect.top))
            val bottomRight = viewport.toScreen(Vector2(candidateRect.right, candidateRect.bottom))
            val corner = viewport.pixelsPerUnit * 0.22f

            if (blocked) {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0x55F44336),
                    strokeColor = Color(0xFFE53935),
                    pulse = pulse,
                )
            } else {
                drawGhostBox(
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    corner = corner,
                    fillColor = Color(0x4400E5FF).copy(alpha = 0.35f + pulse * 0.15f),
                    strokeColor = Color(0xFF00E5FF).copy(alpha = 0.85f + pulse * 0.15f),
                    pulse = pulse,
                )
            }

            val centerScreen = viewport.toScreen(Vector2((candidateRect.left + candidateRect.right) * 0.5f, (candidateRect.top + candidateRect.bottom) * 0.5f))
            val arrowAngle = world.selectedBlueprintOrientation.angleDegrees - 90f
            val rad = arrowAngle * PI.toFloat() / 180f
            val arrowLength = viewport.pixelsPerUnit * 0.65f
            val arrowEnd = centerScreen + Offset(cos(rad) * arrowLength, sin(rad) * arrowLength)
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = centerScreen,
                end = arrowEnd,
                strokeWidth = max(2f, viewport.pixelsPerUnit * 0.06f),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = viewport.pixelsPerUnit * 0.12f,
                center = arrowEnd,
            )
        }
        else -> Unit
    }
}

private fun DrawScope.drawGhostBox(
    topLeft: Offset,
    bottomRight: Offset,
    corner: Float,
    fillColor: Color,
    strokeColor: Color,
    pulse: Float,
) {
    drawRoundRect(
        color = fillColor,
        topLeft = topLeft,
        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
        cornerRadius = CornerRadius(corner, corner),
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = topLeft,
        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(
            width = 3.2f + pulse * 1.3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), pulse * 18f),
        ),
    )
}

private fun DrawScope.drawWater(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val visibleWater = world.waterBodies.filter {
        it.shape.worldBounds().expanded(1f).intersects(viewport.visibleBounds)
    }

    // Draw every bank first and every body second. Overlapping circles therefore merge into one
    // irregular lake instead of exposing sandy rings between its component collision shapes.
    visibleWater.forEach { water ->
        val seed = 700 + water.id * 53
        val grassyLip = water.shape.toOrganicPath(viewport, seed, expand = 0.82f)
        val drySand = water.shape.toOrganicPath(viewport, seed, expand = 0.66f)
        val wetSand = water.shape.toOrganicPath(viewport, seed, expand = 0.23f)
        drawPath(grassyLip, Color(0x66566F38))
        drawPath(drySand, Color(0xFFE3C878))
        drawPath(wetSand, Color(0xFFD0A965))
        clipPath(drySand) {
            drawNaturalGroundGrain(
                water.shape.worldBounds().expanded(0.7f),
                seed,
                TerrainType.SAND,
                viewport,
            )
        }
    }

    visibleWater.forEach { water ->
        val seed = 700 + water.id * 53
        val innerPath = water.shape.toOrganicPath(viewport, seed, expand = 0.06f)
        drawPath(innerPath, Color(0xFF389BA8))

        clipPath(innerPath) {
            val cycle = (world.animation.elapsedSeconds + water.ripplePhase) * 0.42f
            val frame = positiveMod(floor(cycle).toInt(), sprites.water.size)
            val next = (frame + 1) % sprites.water.size
            val blend = smoothStep(cycle - floor(cycle))
            val bounds = water.shape.worldBounds().expanded(0.1f)
            drawStretchedTexture(sprites.water[frame], bounds, viewport, alpha = 0.45f * (1f - blend))
            drawStretchedTexture(sprites.water[next], bounds, viewport, alpha = 0.45f * blend)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0x3066EAEA), Color(0x281C5470)),
                ),
                topLeft = viewport.toScreen(Vector2(bounds.left, bounds.top)),
                size = Size(
                    bounds.width * viewport.pixelsPerUnit,
                    bounds.height * viewport.pixelsPerUnit,
                ),
            )
            drawWaterRipples(bounds, water.id, world.animation.elapsedSeconds, viewport)
        }

        // Shoreline gentle foaming lap
        val foamPulse = sin(world.animation.elapsedSeconds * 2.2f + water.id * 1.7f) * 0.04f
        val foamPath = water.shape.toOrganicPath(viewport, seed, expand = 0.11f + foamPulse)
        drawPath(
            foamPath,
            Color(0x75FFFFFF),
            style = Stroke(width = max(1.6f, viewport.pixelsPerUnit * 0.05f), cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawWaterRipples(
    bounds: CollisionRect,
    seed: Int,
    seconds: Float,
    viewport: WorldViewport,
) {
    val rippleCount = max(3, (bounds.width * bounds.height / 16f).roundToInt())
    repeat(rippleCount) { index ->
        val loop = positiveFraction(seconds * (0.07f + index % 3 * 0.015f) + hash01(seed * 97 + index))
        val x = bounds.left + bounds.width * hash01(seed * 31 + index * 47)
        val y = bounds.top + bounds.height * positiveFraction(hash01(seed + index * 83) + loop * 0.22f)
        val halfWidth = (0.3f + hash01(seed * 7 + index * 19) * 0.55f) * (0.72f + loop * 0.3f)
        val start = viewport.toScreen(Vector2(x - halfWidth, y))
        val end = viewport.toScreen(Vector2(x + halfWidth, y))
        val control = viewport.toScreen(Vector2(x, y - 0.08f))
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadraticBezierTo(control.x, control.y, end.x, end.y)
        }
        drawPath(
            path,
            color = Color.White.copy(alpha = 0.08f + (1f - loop) * 0.12f),
            style = Stroke(width = max(0.65f, viewport.pixelsPerUnit * 0.035f), cap = StrokeCap.Round),
        )
    }
}

/** Sparse continuous blades preserve the breeze without animating a grid of grass textures. */
private fun DrawScope.drawAmbientGrass(
    world: FarmWorldState,
    viewport: WorldViewport,
    overview: Boolean,
) {
    // Native-resolution world-space microdetail: random jitter hides the sampling lattice, while
    // stable coordinates prevent shimmer as the camera moves. This stays crisp at every zoom.
    val spacing = if (overview) 1.35f else 0.68f
    val bounds = viewport.visibleBounds.expanded(spacing).let {
        CollisionRect(max(it.left, world.bounds.left), max(it.top, world.bounds.top), min(it.right, world.bounds.right), min(it.bottom, world.bounds.bottom))
    }
    val firstX = floor(bounds.left / spacing).toInt()
    val lastX = ceil(bounds.right / spacing).toInt()
    val firstY = floor(bounds.top / spacing).toInt()
    val lastY = ceil(bounds.bottom / spacing).toInt()
    val waterExclusions = world.waterBodies.filter {
        it.shape.worldBounds().expanded(0.5f).intersects(bounds)
    }
    val terrainExclusions = world.terrainRegions.filter {
        it.terrain != TerrainType.GRASS && it.shape.worldBounds().expanded(0.3f).intersects(bounds)
    }
    val dirtRibbonNodes = world.terrainRegions.asSequence()
        .filter { it.terrain == TerrainType.DIRT }
        .sortedBy { it.id }
        .map { region ->
            val halfWidth = when (val shape = region.shape) {
                is CollisionCircle -> shape.radius * 0.88f
                is CollisionRect -> min(shape.width, shape.height) * 0.44f
            }
            RibbonNode(region.shape.worldCenter(), halfWidth)
        }
        .toList()
    for (cellY in firstY..lastY) for (cellX in firstX..lastX) {
        val seed = 10_007 + cellX * 1_009 + cellY * 7_919
        val position = Vector2(
            (cellX + 0.08f + hash01(seed) * 0.84f) * spacing,
            (cellY + 0.08f + hash01(seed xor 0x51F15E) * 0.84f) * spacing,
        )
        if (!viewport.visibleBounds.expanded(0.35f).contains(position)) continue
        if (waterExclusions.any { it.shape.distanceTo(position) < 0.72f }) continue
        if (isInsideRibbon(position, dirtRibbonNodes)) continue
        if (terrainExclusions.any { it.shape.contains(position) }) continue

        val height = 0.055f + hash01(seed + 41) * 0.11f
        val spread = 0.025f + hash01(seed + 73) * 0.025f
        val breeze = sin(world.animation.elapsedSeconds * 0.24f + hash01(seed + 97) * PI.toFloat() * 2f)
        val sway = breeze * (0.006f + height * 0.035f)
        val base = viewport.toScreen(position)
        val color = if (hash01(seed + 131) < 0.48f) Color(0x28175029) else Color(0x226C8D2E)
        drawCircle(Color(0x14DEF083), max(0.36f, viewport.pixelsPerUnit * 0.006f), base)
        repeat(2) { bladeIndex ->
            val side = if (bladeIndex == 0) -1f else 1f
            val tipWorld = position + Vector2(side * spread + sway, -height * (0.82f + bladeIndex * 0.18f))
            val tip = viewport.toScreen(tipWorld)
            // Straight two-pixel blades avoid allocating hundreds of Path objects per frame.
            drawLine(
                color = color,
                start = base,
                end = tip,
                strokeWidth = max(0.42f, viewport.pixelsPerUnit * 0.014f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawGroundProps(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
    overview: Boolean,
) {
    world.grassTufts.forEach { tuft ->
        if (!isNearViewport(tuft.position, 1f, viewport)) return@forEach
        val flower = tuft.id % 13 == 0
        val image = if (flower) {
            sprites.flowers
        } else {
            val frame = frameIndex(
                seconds = world.animation.elapsedSeconds + tuft.phase,
                fps = 0.62f,
                count = sprites.tufts.size,
            )
            sprites.tufts[frame]
        }
        val sway = if (flower) 0f else sin(world.animation.elapsedSeconds * 0.68f + tuft.phase) * 0.035f
        drawBottomAnchoredSprite(
            image = image,
            bottomCenter = tuft.position + Vector2(sway, 0.26f),
            worldWidth = if (flower) 0.74f else 0.66f,
            worldHeight = if (flower) 0.74f else 0.66f,
            viewport = viewport,
            alpha = if (overview) 0.9f else 1f,
        )
    }

    val fences = world.staticObstacles.filter { it.kind == ObstacleKind.FENCE }
    drawConnectedFences(fences.map { it.collider }, sprites, viewport)

    world.staticObstacles.forEach { obstacle ->
        if (obstacle.kind != ObstacleKind.ROCK) return@forEach
        val shape = obstacle.collider
        val center = shape.worldCenter()
        val width = when (shape) {
            is CollisionCircle -> shape.radius * 2.15f
            is CollisionRect -> max(shape.width, shape.height) * 1.25f
        }.coerceAtLeast(0.98f)
        drawContactShadow(
            center = center + Vector2(0f, width * 0.36f),
            worldWidth = width * 0.72f,
            worldHeight = width * 0.2f,
            viewport = viewport,
            alpha = 0.2f,
        )
        drawBottomAnchoredSprite(
            image = sprites.rock,
            bottomCenter = center + Vector2(0f, width * 0.4f),
            worldWidth = width,
            worldHeight = width,
            viewport = viewport,
            alpha = if (obstacle.hitPoints <= 1) 0.88f else 1f,
        )
    }
}

/** Minecraft-style dynamic modular connected fences with seamless joins and perimeter rails. */
private fun DrawScope.drawConnectedFences(shapes: List<CollisionShape>, sprites: WorldSprites, viewport: WorldViewport) {
    val segments = shapes.mapNotNull { it as? CollisionRect }
    val smallFences = segments.filter { max(it.width, it.height) <= 1.4f }
    val perimeterFences = segments.filter { max(it.width, it.height) > 1.4f }

    // 1. Dynamic Minecraft-style connected fences for placed tiles
    val centers = smallFences.map { it.center }
    smallFences.forEach { rect ->
        val center = rect.center
        if (!rect.expanded(1.2f).intersects(viewport.visibleBounds)) return@forEach

        // Neighbor detection in 4 cardinal directions (within ~1 block)
        val hasEast = centers.any { (it.x - center.x) in 0.7f..1.3f && abs(it.y - center.y) < 0.35f } ||
            perimeterFences.any { abs(it.left - (center.x + 0.5f)) < 0.25f && center.y in it.top..it.bottom }
        val hasWest = centers.any { (center.x - it.x) in 0.7f..1.3f && abs(it.y - center.y) < 0.35f } ||
            perimeterFences.any { abs(it.right - (center.x - 0.5f)) < 0.25f && center.y in it.top..it.bottom }
        val hasNorth = centers.any { (center.y - it.y) in 0.7f..1.3f && abs(it.x - center.x) < 0.35f } ||
            perimeterFences.any { abs(it.bottom - (center.y - 0.5f)) < 0.25f && center.x in it.left..it.right }
        val hasSouth = centers.any { (it.y - center.y) in 0.7f..1.3f && abs(it.x - center.x) < 0.35f } ||
            perimeterFences.any { abs(it.top - (center.y + 0.5f)) < 0.25f && center.x in it.left..it.right }

        val bottomCenter = center + Vector2(0f, 0.5f)
        val tileSize = 1.0f

        drawContactShadow(
            center = center + Vector2(0f, 0.42f),
            worldWidth = 0.85f,
            worldHeight = 0.35f,
            viewport = viewport,
            alpha = 0.22f,
        )

        // Draw connecting rails first
        if (hasNorth) {
            sprites.v5.fenceArmNorth?.let {
                drawBottomAnchoredSprite(it, bottomCenter, tileSize, tileSize, viewport)
            }
        }
        if (hasWest) {
            sprites.v5.fenceArmWest?.let {
                drawBottomAnchoredSprite(it, bottomCenter, tileSize, tileSize, viewport)
            }
        }
        if (hasEast) {
            sprites.v5.fenceArmEast?.let {
                drawBottomAnchoredSprite(it, bottomCenter, tileSize, tileSize, viewport)
            }
        }
        if (hasSouth) {
            sprites.v5.fenceArmSouth?.let {
                drawBottomAnchoredSprite(it, bottomCenter, tileSize, tileSize, viewport)
            }
        }

        // Center post drawn on top
        val postSprite = sprites.v5.fenceCenterPost ?: sprites.v5.fenceHorizontal
        if (postSprite != null) {
            drawBottomAnchoredSprite(postSprite, bottomCenter, tileSize, tileSize, viewport)
        }
    }

    // 2. Perimeter property boundary fences (continuous long segments)
    perimeterFences.forEach { rect ->
        val horizontal = rect.width >= rect.height
        val start = if (horizontal) Vector2(rect.left, rect.center.y) else Vector2(rect.center.x, rect.top)
        val end = if (horizontal) Vector2(rect.right, rect.center.y) else Vector2(rect.center.x, rect.bottom)
        val shadowOffset = if (horizontal) Vector2(0f, 0.13f) else Vector2(0.1f, 0.08f)
        val width = max(1f, viewport.pixelsPerUnit * 0.16f)
        drawLine(
            color = Color(0xA84A3022),
            start = viewport.toScreen(start + shadowOffset),
            end = viewport.toScreen(end + shadowOffset),
            strokeWidth = width * 1.55f,
            cap = StrokeCap.Square,
        )
        drawLine(
            color = Color(0xFF98603A),
            start = viewport.toScreen(start),
            end = viewport.toScreen(end),
            strokeWidth = width,
            cap = StrokeCap.Square,
        )
        drawLine(
            color = Color(0xFFD59A58),
            start = viewport.toScreen(start + if (horizontal) Vector2(0f, -0.055f) else Vector2(-0.055f, 0f)),
            end = viewport.toScreen(end + if (horizontal) Vector2(0f, -0.055f) else Vector2(-0.055f, 0f)),
            strokeWidth = max(0.7f, width * 0.26f),
            cap = StrokeCap.Square,
        )

        val length = if (horizontal) rect.width else rect.height
        val pieces = max(1, ceil(length / 2.35f).toInt())
        repeat(pieces + 1) { index ->
            val t = index.toFloat() / pieces
            drawFencePost(lerp(start, end, t), viewport)
        }
    }
}

private fun DrawScope.drawFencePost(position: Vector2, viewport: WorldViewport) {
    val center = viewport.toScreen(position)
    val width = max(2f, viewport.pixelsPerUnit * 0.32f)
    val height = max(3f, viewport.pixelsPerUnit * 0.72f)
    drawRect(
        Color(0x88422A20),
        topLeft = Offset(center.x - width * 0.37f + width * 0.17f, center.y - height * 0.68f + height * 0.16f),
        size = Size(width * 0.74f, height * 0.92f),
    )
    drawRect(
        Color(0xFF8C5536),
        topLeft = Offset(center.x - width * 0.5f, center.y - height * 0.75f),
        size = Size(width, height),
    )
    drawRect(
        Color(0xFFD49A59),
        topLeft = Offset(center.x - width * 0.5f, center.y - height * 0.75f),
        size = Size(width * 0.25f, height * 0.84f),
    )
    val cap = Path().apply {
        moveTo(center.x - width * 0.5f, center.y - height * 0.75f)
        lineTo(center.x, center.y - height)
        lineTo(center.x + width * 0.5f, center.y - height * 0.75f)
        close()
    }
    drawPath(cap, Color(0xFFE4AF6B))
}

private fun DrawScope.drawFishingRig(world: FarmWorldState, viewport: WorldViewport) {
    val fishing = world.fishing
    if (fishing.phase == FishingPhase.NONE) return

    val hand = world.player.position + Vector2(0.25f, -1.25f)
    val castProgress = world.player.actionAnimation
        ?.takeIf { it.tool == ToolType.FISHING_ROD }
        ?.progress
        ?: 1f
    val bobber = if (fishing.phase == FishingPhase.CASTING) {
        val arc = sin(castProgress * PI.toFloat()) * 1.15f
        val linear = lerp(hand, fishing.bobberPosition, castProgress)
        linear + Vector2(0f, -arc)
    } else {
        fishing.bobberPosition
    }

    val start = viewport.toScreen(hand)
    val end = viewport.toScreen(bobber)
    val control = Offset(
        (start.x + end.x) * 0.5f,
        min(start.y, end.y) - viewport.pixelsPerUnit * (0.55f + castProgress * 0.25f),
    )
    val line = Path().apply {
        moveTo(start.x, start.y)
        quadraticBezierTo(control.x, control.y, end.x, end.y)
    }
    drawPath(
        path = line,
        color = Color(0xE8F4F1D5),
        style = Stroke(
            width = max(0.75f, viewport.pixelsPerUnit * 0.035f),
            cap = StrokeCap.Round,
        ),
    )
    val bobberRadius = max(2f, viewport.pixelsPerUnit * 0.13f)
    drawCircle(Color(0xFFFBF0D0), bobberRadius, end)
    drawArc(
        color = Color(0xFFD34C3E),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(end.x - bobberRadius, end.y - bobberRadius),
        size = Size(bobberRadius * 2f, bobberRadius * 2f),
    )
    if (fishing.phase != FishingPhase.CASTING) {
        val pulse = (sin(world.animation.elapsedSeconds * 2.2f) + 1f) * 0.5f
        drawOval(
            color = Color.White.copy(alpha = 0.2f * (1f - pulse)),
            topLeft = Offset(end.x - bobberRadius * (1.4f + pulse), end.y - bobberRadius * 0.35f),
            size = Size(bobberRadius * (2.8f + pulse * 2f), bobberRadius * 0.7f),
            style = Stroke(max(0.7f, viewport.pixelsPerUnit * 0.025f)),
        )
    }
}

private fun DrawScope.drawBarn(world: FarmWorldState, sprites: WorldSprites, viewport: WorldViewport) {
    val barn = world.barn
    if (!barn.collider.expanded(2f).intersects(viewport.visibleBounds)) return
    val bottom = Vector2(barn.collider.center.x, barn.collider.bottom + 0.28f)
    drawContactShadow(
        center = bottom,
        worldWidth = barn.collider.width * 0.82f,
        worldHeight = 1.05f,
        viewport = viewport,
        alpha = 0.27f,
    )
    drawBottomAnchoredSprite(
        image = sprites.barn,
        bottomCenter = Vector2(barn.collider.center.x, barn.collider.bottom + 0.35f),
        worldWidth = barn.collider.width + 1.7f,
        worldHeight = barn.collider.height + 1.7f,
        viewport = viewport,
    )
}

private fun DrawScope.drawFactory(
    factory: FactoryBuildingState,
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    if (!factory.collider.expanded(2f).intersects(viewport.visibleBounds)) return

    val col = factory.collider
    val topLeftScreen = viewport.toScreen(Vector2(col.left, col.top))
    val bottomRightScreen = viewport.toScreen(Vector2(col.right, col.bottom))
    val widthPx = bottomRightScreen.x - topLeftScreen.x
    val heightPx = bottomRightScreen.y - topLeftScreen.y
    val bottomWorld = Vector2((col.left + col.right) * 0.5f, col.bottom + 0.28f)

    drawContactShadow(
        center = bottomWorld,
        worldWidth = col.width * 0.88f,
        worldHeight = 1.1f,
        viewport = viewport,
        alpha = 0.32f,
    )

    if (factory.phase == ConstructionPhase.SCAFFOLD) {
        val foundationColor = Color(0xFF6D4C41)
        val scaffoldWood = Color(0xFF8D6E63)
        val scaffoldDarkWood = Color(0xFF5D4037)
        val beamThick = max(2.5f, viewport.pixelsPerUnit * 0.09f)

        drawRoundRect(
            color = foundationColor,
            topLeft = Offset(topLeftScreen.x + widthPx * 0.05f, topLeftScreen.y + heightPx * 0.4f),
            size = Size(widthPx * 0.9f, heightPx * 0.58f),
            cornerRadius = CornerRadius(beamThick, beamThick),
        )

        val uprightCount = factory.widthCells + 1
        for (i in 0 until uprightCount) {
            val ux = topLeftScreen.x + widthPx * (i.toFloat() / factory.widthCells)
            drawLine(
                color = scaffoldDarkWood,
                start = Offset(ux, topLeftScreen.y + heightPx * 0.05f),
                end = Offset(ux, topLeftScreen.y + heightPx * 0.98f),
                strokeWidth = beamThick,
                cap = StrokeCap.Round,
            )
        }

        val horizontalLevels = 3
        for (j in 0..horizontalLevels) {
            val uy = topLeftScreen.y + heightPx * (0.15f + j * 0.26f)
            drawLine(
                color = scaffoldWood,
                start = Offset(topLeftScreen.x, uy),
                end = Offset(bottomRightScreen.x, uy),
                strokeWidth = beamThick * 0.85f,
                cap = StrokeCap.Round,
            )
        }

        for (i in 0 until factory.widthCells) {
            val x1 = topLeftScreen.x + widthPx * (i.toFloat() / factory.widthCells)
            val x2 = topLeftScreen.x + widthPx * ((i + 1).toFloat() / factory.widthCells)
            val y1 = topLeftScreen.y + heightPx * 0.15f
            val y2 = topLeftScreen.y + heightPx * 0.67f
            drawLine(scaffoldDarkWood.copy(alpha = 0.75f), Offset(x1, y1), Offset(x2, y2), strokeWidth = beamThick * 0.6f)
            drawLine(scaffoldDarkWood.copy(alpha = 0.75f), Offset(x2, y1), Offset(x1, y2), strokeWidth = beamThick * 0.6f)
        }

        val bubbleCenter = Vector2((col.left + col.right) * 0.5f, col.top - 0.5f)
        val bob = sin(world.animation.elapsedSeconds * 2.8f) * 0.08f
        val bubbleScreen = viewport.toScreen(bubbleCenter + Vector2(0f, bob))
        val bubbleW = viewport.pixelsPerUnit * 2.2f
        val bubbleH = viewport.pixelsPerUnit * 0.75f
        val bubbleTL = Offset(bubbleScreen.x - bubbleW * 0.5f, bubbleScreen.y - bubbleH * 0.5f)

        drawRoundRect(
            color = Color(0xF2FFFFFF),
            topLeft = bubbleTL,
            size = Size(bubbleW, bubbleH),
            cornerRadius = CornerRadius(bubbleH * 0.35f, bubbleH * 0.35f),
        )
        drawRoundRect(
            color = Color(0xFF4E342E),
            topLeft = bubbleTL,
            size = Size(bubbleW, bubbleH),
            cornerRadius = CornerRadius(bubbleH * 0.35f, bubbleH * 0.35f),
            style = Stroke(width = max(1.5f, viewport.pixelsPerUnit * 0.035f)),
        )

        val woodIconPos = Vector2(bubbleCenter.x - 0.7f, bubbleCenter.y + bob)
        drawBottomAnchoredSprite(sprites.wood, woodIconPos, 0.45f, 0.45f, viewport)
        val woodRatio = (factory.woodContributed.toFloat() / factory.woodRequired.toFloat()).coerceIn(0f, 1f)
        val woodBarLeft = bubbleTL.x + bubbleW * 0.22f
        val woodBarTop = bubbleTL.y + bubbleH * 0.42f
        val barW = bubbleW * 0.25f
        val barH = bubbleH * 0.28f
        drawRoundRect(Color(0xFFBCAAA4), Offset(woodBarLeft, woodBarTop), Size(barW, barH), CornerRadius(barH * 0.5f, barH * 0.5f))
        drawRoundRect(Color(0xFF8D6E63), Offset(woodBarLeft, woodBarTop), Size(barW * woodRatio, barH), CornerRadius(barH * 0.5f, barH * 0.5f))

        val rockIconPos = Vector2(bubbleCenter.x + 0.15f, bubbleCenter.y + bob)
        drawBottomAnchoredSprite(sprites.rock, rockIconPos, 0.42f, 0.42f, viewport)
        val stoneRatio = (factory.stoneContributed.toFloat() / factory.stoneRequired.toFloat()).coerceIn(0f, 1f)
        val stoneBarLeft = bubbleTL.x + bubbleW * 0.65f
        val stoneBarTop = bubbleTL.y + bubbleH * 0.42f
        drawRoundRect(Color(0xFFB0BEC5), Offset(stoneBarLeft, stoneBarTop), Size(barW, barH), CornerRadius(barH * 0.5f, barH * 0.5f))
        drawRoundRect(Color(0xFF546E7A), Offset(stoneBarLeft, stoneBarTop), Size(barW * stoneRatio, barH), CornerRadius(barH * 0.5f, barH * 0.5f))

    } else {
        val buildingSprite = when (factory.type) {
            FactoryType.DAIRY -> sprites.v5.factoryDairy
            FactoryType.SLAUGHTERHOUSE -> sprites.v5.factorySlaughterhouse
            FactoryType.EGG_PACKER -> sprites.v5.factoryEggPacker
            FactoryType.VEGGIE_PACKER -> sprites.v5.factoryVeggiePacker
            FactoryType.BAKERY -> sprites.v5.factoryBakery
            FactoryType.FISH_PROCESSOR -> sprites.v5.factoryFishProcessor
        }

        if (buildingSprite != null) {
            drawBottomAnchoredSprite(
                image = buildingSprite,
                bottomCenter = Vector2(col.center.x, col.bottom + 0.35f),
                worldWidth = col.width + 1.25f,
                worldHeight = col.height + 1.25f,
                viewport = viewport,
            )
        }

        // Bakery chimney smoke particles
        if (factory.type == FactoryType.BAKERY) {
            val chimPos = Vector2(col.right - 0.75f, col.top - 0.35f)
            val chimScreen = viewport.toScreen(chimPos)
            val smokeTime = world.animation.elapsedSeconds * 2.2f
            for (s in 0..2) {
                val p = ((smokeTime + s * 0.8f) % 2.5f) / 2.5f
                val puffRadius = viewport.pixelsPerUnit * (0.12f + p * 0.22f)
                val puffX = chimScreen.x + sin(p * 5f) * viewport.pixelsPerUnit * 0.15f
                val puffY = chimScreen.y - p * viewport.pixelsPerUnit * 1.1f
                drawCircle(Color.White.copy(alpha = (1f - p) * 0.45f), puffRadius, Offset(puffX, puffY))
            }
        }

        // Slaughterhouse animal chamber indicator
        if (factory.type == FactoryType.SLAUGHTERHOUSE && (factory.hasPigInChamber || factory.hasCowInChamber)) {
            val chamberCenter = Vector2(col.center.x, col.bottom - 0.3f)
            val chamberSprite = if (factory.hasCowInChamber) sprites.v5.cow.downIdle else sprites.v5.pig.downIdle
            chamberSprite?.let {
                drawBottomAnchoredSprite(it, chamberCenter, 0.75f, 0.75f, viewport)
            }
        }

        // Dairy cheese ready indicator
        if (factory.type == FactoryType.DAIRY && factory.storedOutputs > 0) {
            val cheesePos = Vector2(col.center.x, col.top - 0.45f)
            val cheeseSprite = sprites.v5.itemCheese
            if (cheeseSprite != null) {
                drawBottomAnchoredSprite(cheeseSprite, cheesePos, 0.85f, 0.85f, viewport)
            } else {
                val cheeseScreen = viewport.toScreen(cheesePos)
                drawCircle(Color(0xFFFFD54F), viewport.pixelsPerUnit * 0.26f, cheeseScreen)
                drawCircle(Color(0xFFFFA000), viewport.pixelsPerUnit * 0.26f, cheeseScreen, style = Stroke(2f))
            }
        }
    }

    val playerDist = world.player.position.distanceTo(factory.doorPosition)
    if (playerDist < 1.8f) {
        val bubblePos = factory.doorPosition + Vector2(0f, -1.35f)
        val bubbleScreen = viewport.toScreen(bubblePos)
        val pulse = sin(world.animation.elapsedSeconds * 4f) * 0.5f + 0.5f
        drawCircle(
            color = Color(0xFF76FF03).copy(alpha = 0.35f + pulse * 0.3f),
            radius = viewport.pixelsPerUnit * (0.28f + pulse * 0.08f),
            center = bubbleScreen,
        )
        drawCircle(
            color = Color.White,
            radius = viewport.pixelsPerUnit * 0.22f,
            center = bubbleScreen,
        )
        drawCircle(
            color = Color(0xFF2E7D32),
            radius = viewport.pixelsPerUnit * 0.16f,
            center = bubbleScreen,
        )
    }
}

private fun DrawScope.drawArchitectGrid(world: FarmWorldState, viewport: WorldViewport) {
    val bounds = CollisionRect(4.5f, 4.5f, 59.5f, 42.5f)
    if (!bounds.intersects(viewport.visibleBounds)) return

    val minX = max(5, floor(viewport.visibleBounds.left).toInt())
    val maxX = min(59, ceil(viewport.visibleBounds.right).toInt())
    val minY = max(5, floor(viewport.visibleBounds.top).toInt())
    val maxY = min(42, ceil(viewport.visibleBounds.bottom).toInt())

    for (x in minX..maxX) {
        val p1 = viewport.toScreen(Vector2(x.toFloat(), minY.toFloat()))
        val p2 = viewport.toScreen(Vector2(x.toFloat(), maxY.toFloat()))
        val isMajor = x % 4 == 0
        drawLine(
            color = if (isMajor) Color(0x7700E5FF) else Color(0x3300E5FF),
            start = p1,
            end = p2,
            strokeWidth = if (isMajor) 1.8f else 1f,
        )
    }
    for (y in minY..maxY) {
        val p1 = viewport.toScreen(Vector2(minX.toFloat(), y.toFloat()))
        val p2 = viewport.toScreen(Vector2(maxX.toFloat(), y.toFloat()))
        val isMajor = y % 4 == 0
        drawLine(
            color = if (isMajor) Color(0x7700E5FF) else Color(0x3300E5FF),
            start = p1,
            end = p2,
            strokeWidth = if (isMajor) 1.8f else 1f,
        )
    }

    val barnBox = world.barn.collider
    val barnTL = viewport.toScreen(Vector2(barnBox.left, barnBox.top))
    val barnBR = viewport.toScreen(Vector2(barnBox.right, barnBox.bottom))
    val isBarnSelected = world.selectedMoveStructureId == -1
    drawRoundRect(
        color = if (isBarnSelected) Color(0x6600E5FF) else Color(0x2200E5FF),
        topLeft = barnTL,
        size = Size(barnBR.x - barnTL.x, barnBR.y - barnTL.y),
        cornerRadius = CornerRadius(viewport.pixelsPerUnit * 0.15f, viewport.pixelsPerUnit * 0.15f),
    )
    drawRoundRect(
        color = if (isBarnSelected) Color(0xFF00E5FF) else Color(0xAA00E5FF),
        topLeft = barnTL,
        size = Size(barnBR.x - barnTL.x, barnBR.y - barnTL.y),
        cornerRadius = CornerRadius(viewport.pixelsPerUnit * 0.15f, viewport.pixelsPerUnit * 0.15f),
        style = Stroke(
            width = if (isBarnSelected) 3.5f else 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
        ),
    )

    world.factories.forEach { factory ->
        val fBox = factory.collider
        val fTL = viewport.toScreen(Vector2(fBox.left, fBox.top))
        val fBR = viewport.toScreen(Vector2(fBox.right, fBox.bottom))
        val isSelected = world.selectedMoveStructureId == factory.id
        drawRoundRect(
            color = if (isSelected) Color(0x6600E5FF) else Color(0x2200E5FF),
            topLeft = fTL,
            size = Size(fBR.x - fTL.x, fBR.y - fTL.y),
            cornerRadius = CornerRadius(viewport.pixelsPerUnit * 0.15f, viewport.pixelsPerUnit * 0.15f),
        )
        drawRoundRect(
            color = if (isSelected) Color(0xFF00E5FF) else Color(0xAA00E5FF),
            topLeft = fTL,
            size = Size(fBR.x - fTL.x, fBR.y - fTL.y),
            cornerRadius = CornerRadius(viewport.pixelsPerUnit * 0.15f, viewport.pixelsPerUnit * 0.15f),
            style = Stroke(
                width = if (isSelected) 3.5f else 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
            ),
        )
    }
}

private fun DrawScope.drawTree(
    tree: TreeState,
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    if (!isNearViewport(tree.position, 5f, viewport)) return
    if (tree.lifeState == TreeLifeState.STUMP) {
        drawContactShadow(
            center = tree.position + Vector2(0f, 0.4f),
            worldWidth = 1.25f,
            worldHeight = 0.34f,
            viewport = viewport,
            alpha = 0.23f,
        )
        drawBottomAnchoredSprite(
            image = sprites.treeStump,
            bottomCenter = tree.position + Vector2(0f, 0.45f),
            worldWidth = 1.85f,
            worldHeight = 1.45f,
            viewport = viewport,
        )
        return
    }

    val image = when {
        tree.lifeState == TreeLifeState.FALLING && tree.fallProgress > 0.58f -> sprites.treeFall
        tree.lifeState == TreeLifeState.FALLING -> sprites.treeLean
        tree.shakeTimer > 0f -> sprites.treeShake
        tree.health <= 1 -> sprites.treeDamage
        else -> when (frameIndex(world.animation.elapsedSeconds + tree.windPhase, 0.48f, 8)) {
            2 -> sprites.treeBreezeLeft
            6 -> sprites.treeBreezeRight
            else -> sprites.treeIdle
        }
    }
    val isFalling = tree.lifeState == TreeLifeState.FALLING
    val sway = if (tree.shakeTimer > 0f) sin(tree.shakeTimer * 46f) * 0.13f else 0f
    val bottom = tree.position + Vector2(sway, 0.5f)
    val width = if (isFalling) 6.1f else 5.25f
    val height = if (isFalling) 5.35f else 6.25f
    val fallAngle = if (isFalling) {
        atan2(tree.fallDirection.y, tree.fallDirection.x) * 180f / PI.toFloat()
    } else {
        0f
    }
    drawContactShadow(
        center = tree.position + Vector2(0f, 0.45f),
        worldWidth = if (isFalling) 4.8f else 3.35f,
        worldHeight = if (isFalling) 0.7f else 0.82f,
        viewport = viewport,
        alpha = if (isFalling) 0.22f else 0.26f,
        rotationDegrees = fallAngle,
    )
    drawBottomAnchoredSprite(
        image = image,
        bottomCenter = bottom,
        worldWidth = width,
        worldHeight = height,
        viewport = viewport,
        flipHorizontal = isFalling && tree.fallDirection.x < 0f,
    )
}

private fun DrawScope.drawPlayer(
    player: PlayerState,
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
    isHost: Boolean = true,
) {
    val action = player.actionAnimation
    val actionFrames = when (action?.tool) {
        ToolType.AXE -> sprites.axeAction
        ToolType.PICKAXE -> sprites.pickaxeAction
        ToolType.MACHETE -> sprites.v5.macheteAction ?: sprites.axeAction
        ToolType.HOE,
        ToolType.PATH_TOOL -> sprites.hoeAction
        ToolType.WATERING_CAN -> sprites.waterAction
        ToolType.SEED_BAG -> sprites.seedAction
        ToolType.FISHING_ROD -> sprites.fishingAction
        ToolType.PET_BALL,
        ToolType.CARROT_BAIT,
        ToolType.FENCE,
        ToolType.GATE,
        ToolType.HANDS,
        ToolType.MILK_PAIL,
        ToolType.ARCHITECT_PENCIL,
        ToolType.BLUEPRINT_DAIRY,
        ToolType.BLUEPRINT_SLAUGHTERHOUSE,
        ToolType.BLUEPRINT_EGG_PACKER,
        ToolType.BLUEPRINT_VEGGIE_PACKER,
        ToolType.BLUEPRINT_BAKERY,
        ToolType.BLUEPRINT_FISH_PROCESSOR,
        null,
        -> null
    }

    if (player.faintTimer > 0f) {
        drawContactShadow(player.position + Vector2(0f, 0.35f), 1.0f, 0.25f, viewport, 0.24f)
        val pivot = viewport.toScreen(player.position)
        rotate(82f, pivot = pivot) {
            drawBottomAnchoredSprite(
                sprites.farmer.down.idle,
                player.position + Vector2(0f, 0.38f),
                1.55f,
                1.55f,
                viewport,
            )
        }
        return
    }

    drawContactShadow(
        center = player.position + Vector2(0f, 0.34f),
        worldWidth = if (player.isMounted) 1.5f else if (action != null) 0.85f else 0.75f,
        worldHeight = if (player.isMounted) 0.32f else 0.22f,
        viewport = viewport,
        alpha = 0.25f,
    )

    if (action != null && actionFrames != null) {
        val frame = (action.progress * actionFrames.size)
            .toInt()
            .coerceIn(actionFrames.indices)
        val flip = action.targetPosition.x < player.position.x - 0.08f
        drawBottomAnchoredSprite(
            image = actionFrames[frame],
            bottomCenter = player.position + Vector2(0f, 0.38f),
            worldWidth = if (action.tool in setOf(ToolType.WATERING_CAN, ToolType.SEED_BAG)) 2.2f else 2.05f,
            worldHeight = if (action.tool in setOf(ToolType.WATERING_CAN, ToolType.SEED_BAG)) 2.2f else 2.05f,
            viewport = viewport,
            flipHorizontal = flip,
        )
    } else if (player.isMounted) {
        val alternate = frameIndex(world.animation.elapsedSeconds, 4.8f, 2) == 1
        val bikeFacing = if (player.isMoving && player.movementDirection.lengthSquared() > 0.01f) {
            cardinalFacing(player.movementDirection)
        } else {
            player.facing
        }
        val bikeSprite = sprites.v5.bicycleV7.frame(bikeFacing, player.isMoving, alternate)
        if (bikeSprite != null) {
            drawBottomAnchoredSprite(
                bikeSprite,
                player.position + Vector2(0f, 0.44f),
                2.05f,
                2.05f,
                viewport,
            )
        }
    } else if (player.carriedAnimal != null) {
        val facing = if (player.isMoving && player.movementDirection.lengthSquared() > 0.01f) {
            cardinalFacing(player.movementDirection)
        } else {
            player.facing
        }
        val alternate = frameIndex(world.animation.elapsedSeconds, 3.8f, 2) == 1
        val carrySprite = when (facing) {
            Facing.UP -> sprites.v5.carryUp
            Facing.DOWN -> if (player.isMoving && alternate) sprites.v5.carryDownWalk ?: sprites.v5.carryDown else sprites.v5.carryDown
            Facing.LEFT, Facing.RIGHT -> sprites.v5.carryLeft
        } ?: sprites.farmer.down.idle
        val stepBob = if (player.isMoving) kotlin.math.abs(sin(world.animation.elapsedSeconds * 10f)) * 0.04f else 0f
        val walkTilt = if (player.isMoving) sin(world.animation.elapsedSeconds * 10f) * 4f else 0f
        val pivot = viewport.toScreen(player.position + Vector2(0f, 0.35f))
        rotate(walkTilt, pivot = pivot) {
            drawBottomAnchoredSprite(
                image = carrySprite,
                bottomCenter = player.position + Vector2(0f, 0.38f - stepBob),
                worldWidth = 1.55f,
                worldHeight = 1.55f,
                viewport = viewport,
                flipHorizontal = facing == Facing.RIGHT,
            )
        }
    } else {
        val direction = farmerDirection(player.movementDirection, player.facing)
        // The extracted UP_LEFT bitmap itself faces up-right. Mirroring the clean up-right source
        // fixes both idle and walk consistently, without a one-frame direction reversal.
        val directionSprites = when (direction) {
            FarmerDirection.DOWN -> sprites.farmer.down
            FarmerDirection.DOWN_LEFT -> sprites.farmer.downLeft
            FarmerDirection.LEFT -> sprites.farmer.left
            FarmerDirection.UP_LEFT -> sprites.farmer.upRight
            FarmerDirection.UP -> sprites.farmer.up
            FarmerDirection.UP_RIGHT -> sprites.farmer.upRight
            FarmerDirection.RIGHT -> sprites.farmer.right
            FarmerDirection.DOWN_RIGHT -> sprites.farmer.downRight
        }
        val walkFrame = frameIndex(world.animation.elapsedSeconds, 3.1f, 2)
        val image = if (player.isMoving && walkFrame == 1) directionSprites.walk else directionSprites.idle
        val stepBob = if (player.isMoving) sin(world.animation.elapsedSeconds * 3.1f * PI.toFloat()) * 0.025f else 0f
        drawBottomAnchoredSprite(
            image = image,
            bottomCenter = player.position + Vector2(0f, 0.38f - stepBob),
            worldWidth = 1.55f,
            worldHeight = 1.55f,
            viewport = viewport,
            flipHorizontal = direction == FarmerDirection.UP_LEFT,
        )
    }

    player.carriedAnimal?.let { reference ->
        when (reference.kind) {
            AnimalKind.CHICKEN -> world.chickens.firstOrNull { it.id == reference.id }?.let {
                drawCarriedChicken(it, player.position, sprites, viewport)
            }
            AnimalKind.COW,
            AnimalKind.PIG,
            AnimalKind.DOG,
            AnimalKind.CAT,
            -> world.animals.firstOrNull { it.id == reference.id }?.let {
                drawDomesticAnimal(it, sprites, viewport, carriedAt = player.position)
            }
        }
    }

    player.activeEmote?.let { emote ->
        val image = sprites.emotes[emote.type] ?: return@let
        val fadeIn = smoothStep((emote.progress / 0.12f).coerceIn(0f, 1f))
        val fadeOut = smoothStep(((1f - emote.progress) / 0.2f).coerceIn(0f, 1f))
        val bob = sin(emote.progress * PI.toFloat()) * 0.28f
        drawBottomAnchoredSprite(
            image = image,
            bottomCenter = player.position + Vector2(0.65f, -1.42f - bob),
            worldWidth = 1.25f,
            worldHeight = 1.25f,
            viewport = viewport,
            alpha = fadeIn * fadeOut,
        )
    }

    if (player.activeItemId != null && player.carriedAnimal == null && !player.isMounted) {
        val item = player.activeItemId
        val itemImage = when (item) {
            ItemType.EGG -> sprites.egg
            ItemType.APPLE -> sprites.apple
            ItemType.WOOD -> sprites.wood
            ItemType.STONE -> sprites.rock
            ItemType.FISH -> sprites.fish
            ItemType.MILK -> sprites.v5.milk
            ItemType.CARROT -> sprites.carrot.last()
            ItemType.SEED, ItemType.WHEAT_SEED -> null
            ItemType.WHEAT -> sprites.v5.itemWheat ?: sprites.carrot.last()
            ItemType.FENCE -> sprites.v5.fenceCenterPost ?: sprites.wood
            ItemType.BREAD -> sprites.v5.itemBread
            ItemType.CHEESE -> sprites.v5.itemCheese
            ItemType.MEAT -> sprites.v5.itemMeat
            ItemType.EGG_CARTON -> sprites.v5.itemEggCarton
            ItemType.VEGGIE_BOX -> sprites.v5.itemVeggieBox
            ItemType.MILK_PAIL -> sprites.v5.toolMilkPail
            ItemType.FLOUR -> sprites.v5.itemFlour ?: sprites.v5.itemWheatSeed ?: sprites.wood
            ItemType.DOUGH -> sprites.v5.itemDough ?: sprites.v5.itemBread ?: sprites.egg
            ItemType.GATE -> sprites.v5.fenceGateClosed ?: sprites.wood
            ItemType.FISH_FILLET -> sprites.v5.itemFishFillet ?: sprites.fish
            else -> null
        }
        if (itemImage != null) {
            val facing = player.facing
            val offset = when (facing) {
                Facing.UP -> Vector2(0f, -0.4f)
                Facing.DOWN -> Vector2(0f, 0.08f)
                Facing.LEFT -> Vector2(-0.25f, 0.04f)
                Facing.RIGHT -> Vector2(0.25f, 0.04f)
                else -> Vector2(0f, 0.08f)
            }
            val bob = if (player.isMoving) kotlin.math.abs(sin(world.animation.elapsedSeconds * 6f)) * 0.035f else 0f
            drawBottomAnchoredSprite(
                image = itemImage,
                bottomCenter = player.position + offset + Vector2(0f, -bob),
                worldWidth = 0.52f,
                worldHeight = 0.52f,
                viewport = viewport,
            )
        }
    }

    // Role indicator when multiplayer is active
    if (world.guestPlayer != null) {
        val indicatorPos = viewport.toScreen(player.position + Vector2(0f, -1.05f))
        val badgeColor = if (isHost) Color(0xFFF3B83E) else Color(0xFF67B76B)
        drawCircle(color = Color(0xFF1E140A), radius = 6f, center = indicatorPos)
        drawCircle(color = badgeColor, radius = 4.5f, center = indicatorPos)
    }
}

private fun DrawScope.drawHeldTool(
    tool: ToolType,
    position: Vector2,
    facing: Facing,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val image = when (tool) {
        ToolType.AXE -> sprites.axe
        ToolType.PICKAXE -> sprites.pickaxe
        ToolType.HOE -> sprites.hoe
        ToolType.WATERING_CAN -> sprites.wateringCan
        ToolType.FISHING_ROD -> sprites.fishingRod
        ToolType.MACHETE -> sprites.v5.machete ?: sprites.axe
        ToolType.PET_BALL -> sprites.v5.ball
        ToolType.FENCE -> sprites.v5.fenceHorizontal
        ToolType.PATH_TOOL -> sprites.dirt
        ToolType.SEED_BAG, ToolType.HANDS, ToolType.CARROT_BAIT,
        ToolType.GATE, ToolType.BLUEPRINT_FISH_PROCESSOR,
        ToolType.MILK_PAIL, ToolType.ARCHITECT_PENCIL,
        ToolType.BLUEPRINT_DAIRY, ToolType.BLUEPRINT_SLAUGHTERHOUSE,
        ToolType.BLUEPRINT_EGG_PACKER, ToolType.BLUEPRINT_VEGGIE_PACKER,
        ToolType.BLUEPRINT_BAKERY -> null
    } ?: return
    val offset = when (facing) {
        Facing.DOWN -> Vector2(0.48f, 0.12f)
        Facing.UP -> Vector2(-0.42f, -0.2f)
        Facing.RIGHT -> Vector2(0.5f, -0.04f)
        Facing.LEFT -> Vector2(-0.5f, -0.04f)
    }
    val size = if (tool == ToolType.FISHING_ROD) 1.05f else 0.78f
    drawBottomAnchoredSprite(
        image = image,
        bottomCenter = position + offset,
        worldWidth = size,
        worldHeight = size,
        viewport = viewport,
        flipHorizontal = facing == Facing.LEFT,
    )
}

private enum class FarmerDirection {
    DOWN, DOWN_LEFT, LEFT, UP_LEFT, UP, UP_RIGHT, RIGHT, DOWN_RIGHT,
}

private fun farmerDirection(direction: Vector2, facing: Facing): FarmerDirection {
    if (direction.lengthSquared() <= 0.0001f) {
        return when (facing) {
            Facing.UP -> FarmerDirection.UP
            Facing.DOWN -> FarmerDirection.DOWN
            Facing.LEFT -> FarmerDirection.LEFT
            Facing.RIGHT -> FarmerDirection.RIGHT
        }
    }
    val angle = atan2(direction.y, direction.x) * 180f / PI.toFloat()
    return when {
        angle >= 157.5f || angle < -157.5f -> FarmerDirection.LEFT
        angle >= 112.5f -> FarmerDirection.DOWN_LEFT
        angle >= 67.5f -> FarmerDirection.DOWN
        angle >= 22.5f -> FarmerDirection.DOWN_RIGHT
        angle >= -22.5f -> FarmerDirection.RIGHT
        angle >= -67.5f -> FarmerDirection.UP_RIGHT
        angle >= -112.5f -> FarmerDirection.UP
        else -> FarmerDirection.UP_LEFT
    }
}

private fun DrawScope.drawChicken(chicken: ChickenState, sprites: WorldSprites, viewport: WorldViewport) {
    if (chicken.handling == AnimalHandlingState.CARRIED) return
    if (!isNearViewport(chicken.position, 1.2f, viewport)) return
    val animal = if (chicken.lifeStage == ChickenLifeStage.CHICK) sprites.chick else sprites.chicken
    val image = when (chicken.behavior) {
        ChickenBehavior.PECK -> if (
            frameIndex(chicken.animationTime + chicken.animationPhase, 1.15f, 2) == 0
        ) animal.idle else animal.peck
        ChickenBehavior.FLEE,
        ChickenBehavior.WANDER,
        ChickenBehavior.FOLLOW_BAIT,
        -> if (
            frameIndex(chicken.animationTime + chicken.animationPhase, 1.55f, 2) == 0
        ) animal.idle else animal.walk
        ChickenBehavior.IDLE -> animal.idle
    }
    val scale = if (chicken.lifeStage == ChickenLifeStage.CHICK) 0.52f else 0.86f
    val hop = if (chicken.behavior in setOf(ChickenBehavior.WANDER, ChickenBehavior.FLEE)) {
        abs(sin(chicken.animationTime * PI.toFloat() * 1.85f)) * 0.035f
    } else 0f
    drawContactShadow(
        center = chicken.position + Vector2(0f, scale * 0.38f),
        worldWidth = scale * 0.7f,
        worldHeight = scale * 0.2f,
        viewport = viewport,
        alpha = if (chicken.lifeStage == ChickenLifeStage.CHICK) 0.16f else 0.21f,
    )
    drawBottomAnchoredSprite(
        image = image,
        bottomCenter = chicken.position + Vector2(0f, scale * 0.42f - hop),
        worldWidth = scale,
        worldHeight = scale,
        viewport = viewport,
        flipHorizontal = chicken.velocity.x < -0.02f ||
            (abs(chicken.velocity.x) <= 0.02f && chicken.facing == Facing.LEFT),
    )
}

private fun DrawScope.drawCarriedChicken(
    chicken: ChickenState,
    playerPosition: Vector2,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val animal = if (chicken.lifeStage == ChickenLifeStage.CHICK) sprites.chick else sprites.chicken
    val size = if (chicken.lifeStage == ChickenLifeStage.CHICK) 0.52f else 0.86f
    drawBottomAnchoredSprite(
        image = animal.idle,
        bottomCenter = playerPosition + Vector2(0f, -0.92f),
        worldWidth = size,
        worldHeight = size,
        viewport = viewport,
        flipHorizontal = chicken.facing == Facing.LEFT,
    )
}

/**
 * Asset-independent fallback for newly introduced animals. It is intentionally compact and uses
 * the same palette/outline language as the sprites; optional v5 art can replace this directly.
 */
private fun DrawScope.drawDomesticAnimal(
    animal: DomesticAnimalState,
    sprites: WorldSprites,
    viewport: WorldViewport,
    carriedAt: Vector2? = null,
) {
    if (animal.handling == AnimalHandlingState.CARRIED && carriedAt == null) return
    if (carriedAt == null && !isNearViewport(animal.position, 2f, viewport)) return

    val carried = carriedAt != null
    val baseWidth = when (animal.type) {
        DomesticAnimalType.COW -> 1.72f
        DomesticAnimalType.PIG -> 1.35f
        DomesticAnimalType.DOG -> 1.08f
        DomesticAnimalType.CAT -> 0.9f
    }
    val baseHeight = when (animal.type) {
        DomesticAnimalType.COW -> 1.28f
        DomesticAnimalType.PIG -> 0.98f
        DomesticAnimalType.DOG -> 1.02f
        DomesticAnimalType.CAT -> 0.86f
    }
    val scale = 1f
    val movementBehavior = animal.behavior in setOf(
        DomesticAnimalBehavior.WANDER,
        DomesticAnimalBehavior.FOLLOW_PLAYER,
        DomesticAnimalBehavior.FOLLOW_BAIT,
        DomesticAnimalBehavior.FETCH_BALL,
        DomesticAnimalBehavior.RETURN_BALL,
        DomesticAnimalBehavior.THROWN,
        DomesticAnimalBehavior.CHASE_FOX,
        DomesticAnimalBehavior.PLAY_WITH_PET,
        DomesticAnimalBehavior.CHASE_CREATURE,
    )
    // FOLLOW_PLAYER can remain selected while the owner is standing still. Base the visual walk
    // cycle on real velocity as well, so a stopped pet immediately uses its seated idle frame.
    val moving = movementBehavior &&
        (animal.velocity.lengthSquared() > 0.008f || animal.handling == AnimalHandlingState.THROWN)
    val step = if (moving) abs(sin(animal.animationTime * PI.toFloat() * 1.55f)) * 0.055f else 0f
    val throwLift = if (animal.handling == AnimalHandlingState.THROWN) {
        abs(sin(animal.thrownTimer * PI.toFloat() * 2.2f)) * 0.32f
    } else {
        0f
    }
    val pounceHop = if (animal.behavior == DomesticAnimalBehavior.CHASE_CREATURE) {
        abs(sin(animal.animationTime * PI.toFloat() * 3.4f)) * 0.24f
    } else if (animal.behavior == DomesticAnimalBehavior.PLAY_WITH_PET) {
        abs(sin(animal.animationTime * PI.toFloat() * 2.6f)) * 0.14f
    } else {
        0f
    }
    val totalLift = throwLift + pounceHop
    val bottomWorld = carriedAt?.plus(Vector2(0f, -0.92f))
        ?: animal.position + Vector2(0f, animal.collisionRadius - step - totalLift)
    val widthWorld = baseWidth * scale
    val heightWorld = baseHeight * scale
    if (!carried) {
        drawContactShadow(
            center = animal.position + Vector2(0f, animal.collisionRadius * 0.8f),
            worldWidth = widthWorld * (0.72f - totalLift * 0.35f).coerceAtLeast(0.2f),
            worldHeight = heightWorld * (0.2f - totalLift * 0.08f).coerceAtLeast(0.05f),
            viewport = viewport,
            alpha = (0.23f * (1f - totalLift * 0.45f)).coerceAtLeast(0.05f),
        )
    }

    val animalSprites = when (animal.type) {
        DomesticAnimalType.COW -> sprites.v5.cow
        DomesticAnimalType.PIG -> sprites.v5.pig
        DomesticAnimalType.DOG -> sprites.v5.dog
        DomesticAnimalType.CAT -> sprites.v5.cat
    }
    val direction = if (animal.velocity.lengthSquared() > 0.004f) animal.velocity.normalized() else animal.facing.vector()
    val alternate = frameIndex(animal.animationTime + animal.animationPhase, 2.4f, 2) == 1
    val directionalV7 = when {
        animal.type == DomesticAnimalType.DOG -> sprites.v5.puppyV7[animal.breedIndex]
        animal.type == DomesticAnimalType.CAT -> sprites.v5.kittenV7[animal.breedIndex]
        animal.type == DomesticAnimalType.COW -> sprites.v5.calfV7
        animal.type == DomesticAnimalType.PIG -> sprites.v5.pigletV7
        else -> null
    }
    val directionalSprite = directionalV7?.frame(animal.facing, moving, alternate)
    val specialSprite = directionalSprite ?: when {
        animal.lifeStage == AnimalLifeStage.ADULT && animal.type == DomesticAnimalType.DOG -> sprites.v5.dogBreeds.getOrNull(animal.breedIndex)
        animal.lifeStage == AnimalLifeStage.ADULT && animal.type == DomesticAnimalType.CAT -> sprites.v5.catBreeds.getOrNull(animal.breedIndex)
        else -> null
    }
    val sprite = specialSprite ?: if (direction.y < -0.22f) {
        if (moving) animalSprites.upLeftWalk ?: animalSprites.upIdle else animalSprites.upIdle
    } else {
        if (moving) animalSprites.downRightWalk ?: animalSprites.downIdle else animalSprites.downIdle
    }
    if (sprite != null) {
        val flip = directionalSprite == null && direction.x < -0.04f
        val animalScale = when {
            animal.lifeStage == AnimalLifeStage.BABY -> 0.72f
            animal.type == DomesticAnimalType.COW -> 1.32f
            animal.type == DomesticAnimalType.PIG -> 1.18f
            else -> 1f
        }
        val petLift = when {
            animal.behavior == DomesticAnimalBehavior.FISHING -> abs(sin(animal.animationTime * PI.toFloat() * 2.6f)) * 0.55f
            animal.behavior == DomesticAnimalBehavior.PLAY_WITH_PET -> abs(sin(animal.animationTime * PI.toFloat() * 3.4f)) * 0.28f
            animal.behavior == DomesticAnimalBehavior.CHASE_CREATURE && animal.behaviorTimer > 0f -> abs(sin(animal.behaviorTimer * PI.toFloat() * 2.8f)) * 0.36f
            else -> 0f
        }
        val drawSprite = {
            drawBottomAnchoredSprite(
                image = sprite,
                bottomCenter = bottomWorld + Vector2(0f, -petLift),
                worldWidth = widthWorld * animalScale,
                worldHeight = heightWorld * animalScale,
                viewport = viewport,
                flipHorizontal = flip,
            )
        }
        drawSprite()
        return
    }

    val bottom = viewport.toScreen(bottomWorld)
    val width = max(5f, widthWorld * viewport.pixelsPerUnit)
    val height = max(5f, heightWorld * viewport.pixelsPerUnit)
    val horizontal = abs(direction.x) > abs(direction.y) * 0.65f
    val headSide = if (horizontal) if (direction.x < 0f) -1f else 1f else 0f
    val bodyCenter = Offset(bottom.x, bottom.y - height * 0.48f)
    val headCenter = if (horizontal) {
        bodyCenter + Offset(width * 0.34f * headSide, height * 0.05f)
    } else {
        bodyCenter + Offset(0f, height * 0.26f * if (direction.y < 0f) -1f else 1f)
    }
    val outline = Color(0xFF51382C)
    val bodyColor = when (animal.type) {
        DomesticAnimalType.COW -> Color(0xFFF2E7D0)
        DomesticAnimalType.PIG -> Color(0xFFEFA59A)
        DomesticAnimalType.DOG -> Color(0xFFB97843)
        DomesticAnimalType.CAT -> Color(0xFF82929A)
    }
    val accent = when (animal.type) {
        DomesticAnimalType.COW -> Color(0xFF5B4A3D)
        DomesticAnimalType.PIG -> Color(0xFFD97E79)
        DomesticAnimalType.DOG -> Color(0xFF75462E)
        DomesticAnimalType.CAT -> Color(0xFF56656D)
    }

    fun oval(center: Offset, w: Float, h: Float, color: Color) {
        drawOval(color, center - Offset(w * 0.5f, h * 0.5f), Size(w, h))
    }

    oval(bodyCenter, width * 0.78f, height * 0.64f, outline)
    oval(bodyCenter, width * 0.72f, height * 0.57f, bodyColor)
    val legY = bottom.y - height * 0.11f
    listOf(-0.24f, 0.24f).forEach { x ->
        oval(Offset(bottom.x + width * x, legY), width * 0.17f, height * 0.28f, outline)
        oval(Offset(bottom.x + width * x, legY - height * 0.015f), width * 0.12f, height * 0.22f, accent)
    }

    if (animal.type == DomesticAnimalType.COW) {
        oval(bodyCenter + Offset(-width * 0.18f, -height * 0.08f), width * 0.2f, height * 0.18f, accent)
        oval(bodyCenter + Offset(width * 0.14f, height * 0.11f), width * 0.16f, height * 0.13f, accent)
    }
    if (animal.type in setOf(DomesticAnimalType.DOG, DomesticAnimalType.CAT)) {
        val tailStart = bodyCenter - Offset(width * 0.33f * (if (headSide == 0f) 1f else headSide), 0f)
        drawLine(
            color = outline,
            start = tailStart,
            end = tailStart + Offset(-width * 0.25f * (if (headSide == 0f) 1f else headSide), -height * 0.22f),
            strokeWidth = max(2f, width * 0.12f),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = tailStart,
            end = tailStart + Offset(-width * 0.23f * (if (headSide == 0f) 1f else headSide), -height * 0.2f),
            strokeWidth = max(1f, width * 0.065f),
            cap = StrokeCap.Round,
        )
    }

    oval(headCenter, width * 0.43f, height * 0.48f, outline)
    oval(headCenter, width * 0.37f, height * 0.41f, bodyColor)
    val earY = headCenter.y - height * 0.2f
    listOf(-1f, 1f).forEach { side ->
        val ear = Path().apply {
            moveTo(headCenter.x + side * width * 0.1f, earY + height * 0.07f)
            lineTo(headCenter.x + side * width * 0.21f, earY - height * 0.11f)
            lineTo(headCenter.x + side * width * 0.24f, earY + height * 0.11f)
            close()
        }
        drawPath(ear, outline)
    }
    val eyeY = headCenter.y - height * 0.035f
    val eyeSpread = width * 0.09f
    drawCircle(Color(0xFF33271F), max(1f, width * 0.027f), Offset(headCenter.x - eyeSpread, eyeY))
    drawCircle(Color(0xFF33271F), max(1f, width * 0.027f), Offset(headCenter.x + eyeSpread, eyeY))
    if (animal.type == DomesticAnimalType.PIG) {
        oval(headCenter + Offset(0f, height * 0.11f), width * 0.2f, height * 0.13f, accent)
        drawCircle(outline, max(0.7f, width * 0.016f), headCenter + Offset(-width * 0.04f, height * 0.11f))
        drawCircle(outline, max(0.7f, width * 0.016f), headCenter + Offset(width * 0.04f, height * 0.11f))
    }
}

private fun DrawScope.drawPetBall(world: FarmWorldState, sprites: WorldSprites, viewport: WorldViewport) {
    val ball = world.ball
    if (ball.phase == BallPhase.WITH_PLAYER || !isNearViewport(ball.position, 1f, viewport)) return
    val lift = if (ball.phase == BallPhase.FLYING) {
        abs(sin(ball.animationTime * PI.toFloat() * 2.4f)) * 0.35f
    } else {
        0f
    }
    drawContactShadow(
        center = ball.position + Vector2(0f, 0.1f),
        worldWidth = 0.34f - lift * 0.08f,
        worldHeight = 0.11f,
        viewport = viewport,
        alpha = 0.18f * (1f - lift * 0.5f),
    )
    val center = viewport.toScreen(ball.position + Vector2(0f, -lift))
    val radius = max(2.5f, viewport.pixelsPerUnit * 0.16f)
    sprites.v5.ball?.let {
        drawBottomAnchoredSprite(it, ball.position + Vector2(0f, 0.18f - lift), 0.42f, 0.42f, viewport)
        return
    }
    drawCircle(Color(0xFF57352E), radius * 1.12f, center)
    drawCircle(Color(0xFFE95D55), radius, center)
    drawArc(
        color = Color(0xFFFFC96B),
        startAngle = 208f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = center - Offset(radius * 0.72f, radius * 0.72f),
        size = Size(radius * 1.44f, radius * 1.44f),
        style = Stroke(max(1f, radius * 0.24f), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawBicycle(position: Vector2, sprites: WorldSprites, viewport: WorldViewport) {
    if (!isNearViewport(position, 2f, viewport)) return
    drawContactShadow(
        center = position + Vector2(0f, 0.42f),
        worldWidth = 1.72f,
        worldHeight = 0.34f,
        viewport = viewport,
        alpha = 0.22f,
    )
    sprites.v5.bicycle?.let {
        drawBottomAnchoredSprite(it, position + Vector2(0f, 0.58f), 2.25f, 1.65f, viewport)
        return
    }
    val center = viewport.toScreen(position)
    val unit = viewport.pixelsPerUnit
    val wheelRadius = max(3f, unit * 0.31f)
    val leftWheel = center + Offset(-unit * 0.62f, unit * 0.28f)
    val rightWheel = center + Offset(unit * 0.62f, unit * 0.28f)
    listOf(leftWheel, rightWheel).forEach { wheel ->
        drawCircle(Color(0xFF3E3B39), wheelRadius, wheel, style = Stroke(max(1.5f, unit * 0.075f)))
        drawCircle(Color(0xFFD8CAB0), max(1f, unit * 0.045f), wheel)
    }
    val crank = center + Offset(-unit * 0.05f, unit * 0.12f)
    val seat = center + Offset(-unit * 0.27f, -unit * 0.28f)
    val handle = center + Offset(unit * 0.4f, -unit * 0.35f)
    val frameStroke = max(1.5f, unit * 0.09f)
    listOf(
        leftWheel to crank,
        crank to rightWheel,
        leftWheel to seat,
        seat to crank,
        crank to handle,
        handle to rightWheel,
    ).forEach { (start, end) ->
        drawLine(Color(0xFF327C82), start, end, frameStroke, StrokeCap.Round)
    }
    drawLine(Color(0xFF4A352B), seat - Offset(unit * 0.12f, 0f), seat + Offset(unit * 0.12f, 0f), frameStroke, StrokeCap.Round)
    drawLine(Color(0xFFD7B36B), handle, handle + Offset(unit * 0.18f, 0f), max(1f, unit * 0.055f), StrokeCap.Round)
}

private fun DrawScope.drawAmbientCreature(
    creature: AmbientCreatureState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    if (!isNearViewport(creature.position, 1.2f, viewport)) return
    val moving = creature.velocity.lengthSquared() > 0.003f
    val alternate = frameIndex(creature.animationTime + creature.id * 0.071f, 4.2f, 2) == 1
    val facingLeft = if (moving) creature.velocity.x < 0f else creature.facing == Facing.LEFT
    when (creature.type) {
        AmbientCreatureType.BIRD -> {
            val image = if (moving) {
                sprites.birdFly[if (alternate) 1 else 0]
                    ?: sprites.birdFly.firstOrNull { it != null }
                    ?: sprites.birdIdle
            } else {
                sprites.birdIdle ?: sprites.birdFly.firstOrNull { it != null }
            }
            val hop = if (moving) abs(sin(creature.animationTime * PI.toFloat() * 2.2f)) * 0.08f else 0f
            drawContactShadow(
                center = creature.position + Vector2(0f, 0.22f),
                worldWidth = 0.52f,
                worldHeight = 0.12f,
                viewport = viewport,
                alpha = 0.13f,
            )
            if (image != null) {
                drawBottomAnchoredSprite(
                    image = image,
                    bottomCenter = creature.position + Vector2(0f, 0.25f - hop),
                    worldWidth = 0.78f,
                    worldHeight = 0.78f,
                    viewport = viewport,
                    flipHorizontal = facingLeft,
                )
            } else {
                val center = viewport.toScreen(creature.position + Vector2(0f, -hop))
                val unit = viewport.pixelsPerUnit
                drawOval(Color(0xFF5D7E9B), center - Offset(unit * 0.2f, unit * 0.12f), Size(unit * 0.4f, unit * 0.24f))
                drawCircle(Color(0xFF40576F), max(1.2f, unit * 0.1f), center + Offset(if (facingLeft) -unit * 0.18f else unit * 0.18f, -unit * 0.08f))
            }
        }
        AmbientCreatureType.BUTTERFLY -> {
            val image = sprites.butterflyFly[if (alternate) 1 else 0]
                ?: sprites.butterflyFly.firstOrNull { it != null }
            val float = sin(creature.animationTime * 2.6f + creature.id) * 0.13f
            if (image != null) {
                drawBottomAnchoredSprite(
                    image = image,
                    bottomCenter = creature.position + Vector2(0f, -0.08f - float),
                    worldWidth = 0.54f,
                    worldHeight = 0.54f,
                    viewport = viewport,
                    flipHorizontal = facingLeft,
                )
            } else {
                val center = viewport.toScreen(creature.position + Vector2(0f, -0.18f - float))
                val unit = viewport.pixelsPerUnit
                val wing = max(1.4f, unit * if (alternate) 0.12f else 0.18f)
                drawOval(Color(0xFFD985C5), center - Offset(wing * 1.1f, wing * 0.65f), Size(wing, wing * 1.25f))
                drawOval(Color(0xFFF1B75A), center + Offset(wing * 0.1f, -wing * 0.65f), Size(wing, wing * 1.25f))
                drawLine(Color(0xFF4D3B42), center - Offset(0f, wing * 0.45f), center + Offset(0f, wing * 0.5f), max(0.7f, unit * 0.025f))
            }
        }
    }
}

private fun DrawScope.drawFox(world: FarmWorldState, sprites: WorldSprites, viewport: WorldViewport) {
    val fox = world.fox
    if (fox.phase !in setOf(FoxPhase.HUNTING, FoxPhase.FLEEING, FoxPhase.DEAD) || !isNearViewport(fox.position, 2f, viewport)) return
    val running = fox.velocity.lengthSquared() > 0.01f
    val isAttacking = fox.biteCooldown > 0.15f
    val hop = if (running) abs(sin(fox.animationTime * PI.toFloat() * 2.1f)) * 0.07f else 0f
    val biteLunge = if (isAttacking) abs(sin(fox.animationTime * PI.toFloat() * 3.5f)) * 0.18f else 0f
    drawContactShadow(
        center = fox.position + Vector2(0f, 0.35f),
        worldWidth = 1.1f,
        worldHeight = 0.25f,
        viewport = viewport,
        alpha = 0.23f,
    )
    val direction = if (fox.velocity.lengthSquared() > 0.004f) fox.velocity.normalized() else fox.facing.vector()
    val foxSprite = when {
        fox.phase == FoxPhase.DEAD -> sprites.v5.foxDefeated ?: sprites.v5.foxHurt
        fox.phase == FoxPhase.FLEEING -> sprites.v5.foxHurt ?: if (direction.y < -0.22f) sprites.v5.foxUpLeftWalk else sprites.v5.foxDownRightWalk
        isAttacking -> sprites.v5.foxBite ?: sprites.v5.foxAttack ?: if (direction.y < -0.22f) sprites.v5.foxUpIdle else sprites.v5.foxDownIdle
        direction.y < -0.22f -> if (running) sprites.v5.foxUpLeftWalk ?: sprites.v5.foxUpIdle else sprites.v5.foxUpIdle
        else -> if (running) sprites.v5.foxDownRightWalk ?: sprites.v5.foxDownIdle else sprites.v5.foxDownIdle
    }
    if (foxSprite != null) {
        val lungeOffset = direction * biteLunge
        drawBottomAnchoredSprite(
            foxSprite,
            fox.position + Vector2(0f, 0.48f - hop) + lungeOffset,
            1.35f,
            1.35f,
            viewport,
            flipHorizontal = direction.x < 0f,
        )
        return
    }
    val center = viewport.toScreen(fox.position + Vector2(0f, -hop))
    val unit = viewport.pixelsPerUnit
    val sign = if (direction.x < 0f) -1f else 1f
    val body = center + Offset(-sign * unit * 0.08f, 0f)
    val head = center + Offset(sign * unit * 0.42f, -unit * 0.16f)
    val tail = center - Offset(sign * unit * 0.56f, unit * 0.05f)
    drawOval(Color(0xFF56362B), tail - Offset(unit * 0.42f, unit * 0.22f), Size(unit * 0.84f, unit * 0.44f))
    drawOval(Color(0xFFC96A36), tail - Offset(unit * 0.36f, unit * 0.18f), Size(unit * 0.72f, unit * 0.36f))
    drawOval(Color(0xFF55352B), body - Offset(unit * 0.52f, unit * 0.34f), Size(unit * 1.04f, unit * 0.68f))
    drawOval(Color(0xFFE87B3C), body - Offset(unit * 0.47f, unit * 0.29f), Size(unit * 0.94f, unit * 0.58f))
    drawCircle(Color(0xFF55352B), max(3f, unit * 0.34f), head)
    drawCircle(Color(0xFFE87B3C), max(2f, unit * 0.29f), head)
    listOf(-1f, 1f).forEach { earSide ->
        val ear = Path().apply {
            moveTo(head.x + earSide * unit * 0.08f, head.y - unit * 0.18f)
            lineTo(head.x + earSide * unit * 0.22f, head.y - unit * 0.52f)
            lineTo(head.x + earSide * unit * 0.28f, head.y - unit * 0.1f)
            close()
        }
        drawPath(ear, Color(0xFF633A2B))
    }
    val muzzle = head + Offset(sign * unit * 0.22f, unit * 0.12f)
    drawCircle(Color(0xFFF5D8B0), max(2f, unit * 0.15f), muzzle)
    drawCircle(Color(0xFF342622), max(1f, unit * 0.045f), muzzle + Offset(sign * unit * 0.1f, 0f))
}

private fun DrawScope.drawMilkBottle(position: Vector2, sprites: WorldSprites, viewport: WorldViewport) {
    sprites.v5.milk?.let {
        drawBottomAnchoredSprite(it, position + Vector2(0f, 0.34f), 0.68f, 0.68f, viewport)
        return
    }
    val center = viewport.toScreen(position + Vector2(0f, 0.03f))
    val unit = viewport.pixelsPerUnit
    val width = max(4f, unit * 0.36f)
    val height = max(6f, unit * 0.62f)
    drawRect(Color(0xFF554A3F), center - Offset(width * 0.52f, height * 0.5f), Size(width * 1.04f, height))
    drawRect(Color(0xFFF4EEDA), center - Offset(width * 0.43f, height * 0.42f), Size(width * 0.86f, height * 0.84f))
    drawRect(Color(0xFF6CB7C5), center + Offset(-width * 0.43f, height * 0.05f), Size(width * 0.86f, height * 0.19f))
    drawRect(Color(0xFFDAA65F), center - Offset(width * 0.27f, height * 0.62f), Size(width * 0.54f, height * 0.17f))
}

private fun DrawScope.drawGroundItem(
    item: GroundItemState,
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val image = when (item.item) {
        ItemType.EGG -> sprites.egg
        ItemType.APPLE -> sprites.apple
        ItemType.WOOD -> sprites.wood
        ItemType.STONE -> sprites.rock
        ItemType.FISH -> sprites.fish
        ItemType.MILK -> sprites.v5.milk
        ItemType.CARROT -> sprites.carrot.last()
        ItemType.SEED -> sprites.seedBag
        ItemType.WHEAT_SEED -> sprites.v5.itemWheatSeed ?: sprites.seedBag
        ItemType.WHEAT -> sprites.v5.itemWheat ?: sprites.carrot.last()
        ItemType.FENCE -> sprites.v5.fenceCenterPost ?: sprites.v5.fenceHorizontal ?: sprites.wood
        ItemType.BREAD -> sprites.v5.itemBread
        ItemType.CHEESE -> sprites.v5.itemCheese
        ItemType.MEAT -> sprites.v5.itemMeat
        ItemType.EGG_CARTON -> sprites.v5.itemEggCarton
        ItemType.VEGGIE_BOX -> sprites.v5.itemVeggieBox
        ItemType.MILK_PAIL -> sprites.v5.toolMilkPail
        ItemType.FLOUR -> sprites.v5.itemFlour ?: sprites.v5.itemWheatSeed ?: sprites.wood
        ItemType.DOUGH -> sprites.v5.itemDough ?: sprites.v5.itemBread ?: sprites.egg
        ItemType.GATE -> sprites.v5.fenceGateClosed ?: sprites.wood
        ItemType.FISH_FILLET -> sprites.v5.itemFishFillet ?: sprites.fish
    }
    val baseSize = when (item.item) {
        ItemType.STONE -> 0.66f
        ItemType.WOOD -> 0.82f
        ItemType.FISH, ItemType.FISH_FILLET -> 0.72f
        ItemType.GATE -> 0.85f
        ItemType.MILK -> 0.7f
        ItemType.EGG_CARTON, ItemType.VEGGIE_BOX -> 0.78f
        else -> 0.58f
    }
    val bob = sin(world.animation.elapsedSeconds * 1.45f + item.animationPhase) * 0.045f
    drawContactShadow(
        center = item.position + Vector2(0f, baseSize * 0.4f),
        worldWidth = baseSize * 0.62f,
        worldHeight = baseSize * 0.17f,
        viewport = viewport,
        alpha = 0.18f,
    )
    if (image != null) {
        drawBottomAnchoredSprite(
            image = image,
            bottomCenter = item.position + Vector2(0f, baseSize * 0.45f - bob),
            worldWidth = baseSize,
            worldHeight = baseSize,
            viewport = viewport,
        )
    } else {
        val centerScreen = viewport.toScreen(item.position + Vector2(0f, -bob))
        val rad = viewport.pixelsPerUnit * (baseSize * 0.45f)
        when (item.item) {
            ItemType.MILK -> drawMilkBottle(item.position + Vector2(0f, -bob), sprites, viewport)
            ItemType.CHEESE -> {
                drawCircle(Color(0xFFFFD54F), rad, centerScreen)
                drawCircle(Color(0xFFFFA000), rad, centerScreen, style = Stroke(2f))
                drawCircle(Color(0xFFFFA000), rad * 0.25f, centerScreen + Offset(-rad * 0.25f, -rad * 0.2f))
                drawCircle(Color(0xFFFFA000), rad * 0.18f, centerScreen + Offset(rad * 0.3f, rad * 0.1f))
            }
            ItemType.BREAD -> {
                val box = Rect(centerScreen.x - rad * 1.1f, centerScreen.y - rad * 0.65f, centerScreen.x + rad * 1.1f, centerScreen.y + rad * 0.65f)
                drawRoundRect(Color(0xFFD87D38), box.topLeft, box.size, CornerRadius(rad * 0.5f, rad * 0.5f))
                drawRoundRect(Color(0xFF8D4925), box.topLeft, box.size, CornerRadius(rad * 0.5f, rad * 0.5f), style = Stroke(1.5f))
            }
            ItemType.MEAT -> {
                drawCircle(Color(0xFFE53935), rad * 0.9f, centerScreen)
                drawCircle(Color(0xFFB71C1C), rad * 0.9f, centerScreen, style = Stroke(2f))
                drawCircle(Color.White, rad * 0.22f, centerScreen)
            }
            ItemType.EGG_CARTON -> {
                val box = Rect(centerScreen.x - rad * 1.2f, centerScreen.y - rad * 0.8f, centerScreen.x + rad * 1.2f, centerScreen.y + rad * 0.8f)
                drawRoundRect(Color(0xFFBCAAA4), box.topLeft, box.size, CornerRadius(rad * 0.2f, rad * 0.2f))
                drawRoundRect(Color(0xFF8D6E63), box.topLeft, box.size, CornerRadius(rad * 0.2f, rad * 0.2f), style = Stroke(1.5f))
            }
            ItemType.VEGGIE_BOX -> {
                val box = Rect(centerScreen.x - rad * 1.2f, centerScreen.y - rad * 0.8f, centerScreen.x + rad * 1.2f, centerScreen.y + rad * 0.8f)
                drawRoundRect(Color(0xFF8D6E63), box.topLeft, box.size, CornerRadius(rad * 0.15f, rad * 0.15f))
                drawRoundRect(Color(0xFF5D4037), box.topLeft, box.size, CornerRadius(rad * 0.15f, rad * 0.15f), style = Stroke(2f))
            }
            else -> {
                drawCircle(Color(0xFFEEEEEE), rad * 0.7f, centerScreen)
            }
        }
    }
}

private fun DrawScope.drawGate(gate: FenceGateState, sprites: WorldSprites, viewport: WorldViewport) {
    val sprite = if (gate.isOpen) sprites.v5.fenceGateOpen ?: sprites.v5.fenceGateClosed else sprites.v5.fenceGateClosed
    if (sprite != null) {
        drawContactShadow(gate.position + Vector2(0f, 0.15f), 0.95f, 0.28f, viewport, 0.2f)
        drawBottomAnchoredSprite(
            image = sprite,
            bottomCenter = gate.position + Vector2(0f, 0.28f),
            worldWidth = 1.0f,
            worldHeight = 1.0f,
            viewport = viewport,
        )
    }
}

private fun DrawScope.drawWheelbarrow(wb: WheelbarrowState, sprites: WorldSprites, viewport: WorldViewport) {
    val sprite = if (wb.totalCargo > 0) (sprites.v5.wheelbarrowLoaded ?: sprites.v5.wheelbarrowEmpty) else sprites.v5.wheelbarrowEmpty
    if (sprite != null) {
        drawContactShadow(wb.position + Vector2(0f, 0.35f), 1.9f, 0.5f, viewport, 0.28f)
        drawBottomAnchoredSprite(
            image = sprite,
            bottomCenter = wb.position + Vector2(0f, 0.45f),
            worldWidth = 2.1f,
            worldHeight = 2.1f,
            viewport = viewport,
        )
    }
}

private fun DrawScope.drawProjectile(proj: ThrownProjectileState, sprites: WorldSprites, viewport: WorldViewport) {
    val sprite = if (proj.item == ItemType.EGG) sprites.egg else sprites.rock
    val size = if (proj.item == ItemType.EGG) 0.42f else 0.38f
    val arc = proj.height
    drawContactShadow(proj.position, size * 0.8f, size * 0.25f, viewport, 0.18f)
    drawBottomAnchoredSprite(
        image = sprite,
        bottomCenter = proj.position + Vector2(0f, -arc),
        worldWidth = size,
        worldHeight = size,
        viewport = viewport,
    )
}

private fun DrawScope.drawRipple(ripple: WaterRippleState, viewport: WorldViewport) {
    val screenCenter = viewport.toScreen(ripple.position)
    val radiusPx = ripple.radius * viewport.pixelsPerUnit
    val alpha = (1f - ripple.progress).coerceIn(0f, 1f) * 0.65f
    drawCircle(
        color = Color(0xFFE0F7FA).copy(alpha = alpha),
        radius = radiusPx,
        center = screenCenter,
        style = Stroke(width = max(1.5f, viewport.pixelsPerUnit * 0.04f)),
    )
    drawCircle(
        color = Color(0xFF80DEEA).copy(alpha = alpha * 0.6f),
        radius = max(2f, radiusPx * 0.65f),
        center = screenCenter,
        style = Stroke(width = max(1f, viewport.pixelsPerUnit * 0.025f)),
    )
}

private fun DrawScope.drawTruck(world: FarmWorldState, sprites: WorldSprites, viewport: WorldViewport) {
    val truck = world.truck
    if (!isNearViewport(truck.position, 4f, viewport)) return
    val image = when (truck.phase) {
        TruckPhase.PARKED -> if (truck.saleCompletedThisVisit) sprites.truckOpen else sprites.truckParked
        TruckPhase.ARRIVING, TruckPhase.LEAVING -> sprites.truckDrive[
            frameIndex(world.animation.elapsedSeconds, 3f, sprites.truckDrive.size)
        ]
        TruckPhase.ABSENT -> return
    }
    drawContactShadow(
        center = truck.position + Vector2(0f, 0.98f),
        worldWidth = 4.05f,
        worldHeight = 0.72f,
        viewport = viewport,
        alpha = 0.28f,
    )
    drawBottomAnchoredSprite(
        image = image,
        bottomCenter = truck.position + Vector2(0f, 1.05f),
        worldWidth = 4.8f,
        worldHeight = 3.25f,
        viewport = viewport,
        flipHorizontal = truck.phase == TruckPhase.LEAVING,
    )
}

private fun DrawScope.drawCourier(
    world: FarmWorldState,
    orderId: Int,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val order = world.deliveries.firstOrNull { it.id == orderId } ?: return
    if (order.courierPhase == CourierPhase.WAITING) return
    val position = order.courierPosition
    if (!isNearViewport(position, 2.2f, viewport)) return
    val alternate = frameIndex(order.courierAnimationTime, 3.2f, 2) == 1
    val courier = sprites.v5.courierV7.frame(order.courierFacing, moving = true, alternate = alternate) ?: return
    drawContactShadow(position + Vector2(0f, 0.42f), 0.95f, 0.25f, viewport, 0.22f)
    drawBottomAnchoredSprite(courier, position + Vector2(0f, 0.48f), 2.1f, 2.1f, viewport)
    if (!order.delivered && order.courierPhase == CourierPhase.TO_BARN) {
        val item = when (order.kind) {
            DeliveryKind.SEEDS -> sprites.seedBag
            DeliveryKind.FENCES -> sprites.v5.fenceHorizontal ?: sprites.wood
            DeliveryKind.BICYCLE -> sprites.v5.bicycle
            DeliveryKind.CHICKEN -> sprites.chicken.idle
            DeliveryKind.TOOL -> when (order.toolType) {
                ToolType.AXE -> sprites.axe
                ToolType.PICKAXE -> sprites.pickaxe
                ToolType.HOE -> sprites.hoe
                ToolType.WATERING_CAN -> sprites.wateringCan
                ToolType.FISHING_ROD -> sprites.fishingRod
                ToolType.MACHETE -> sprites.axe
                else -> sprites.hoe
            }
            DeliveryKind.BLUEPRINT -> sprites.hoe
            DeliveryKind.ANIMAL -> when (order.animalType) {
                DomesticAnimalType.COW -> sprites.v5.calfV7.frame(Facing.DOWN, false, false)
                DomesticAnimalType.PIG -> sprites.v5.pigletV7.frame(Facing.DOWN, false, false)
                DomesticAnimalType.DOG -> sprites.v5.puppyV7[order.breedIndex]?.frame(Facing.DOWN, false, false)
                DomesticAnimalType.CAT -> sprites.v5.kittenV7[order.breedIndex]?.frame(Facing.DOWN, false, false)
                null -> null
            }
        }
        item?.let {
            val itemSize = if (order.kind == DeliveryKind.BICYCLE) 1.15f else 0.72f
            drawBottomAnchoredSprite(it, position + Vector2(0f, -0.38f), itemSize, itemSize, viewport)
        }
    }
}

private fun DrawScope.drawHelper(
    world: FarmWorldState,
    sprites: WorldSprites,
    viewport: WorldViewport,
) {
    val helper = world.helper ?: return
    if (!isNearViewport(helper.position, 3f, viewport)) return
    val isMoving = helper.velocity.lengthSquared() > 0.05f
    val alternate = frameIndex(helper.animationTime, 4f, 2) == 1
    val frame = sprites.v5.helperV9.frame(helper.facing, moving = isMoving, alternate = alternate)
        ?: sprites.farmer.down.idle

    drawContactShadow(
        center = helper.position + Vector2(0f, 0.43f),
        worldWidth = 0.98f,
        worldHeight = 0.3f,
        viewport = viewport,
        alpha = 0.25f,
    )
    drawBottomAnchoredSprite(
        image = frame,
        bottomCenter = helper.position + Vector2(0f, 0.48f),
        worldWidth = 2.15f,
        worldHeight = 2.15f,
        viewport = viewport,
    )
    if (helper.carriedEggs > 0) {
        val eggOffset = when (helper.facing) {
            Facing.DOWN -> Vector2(0f, 0.22f)
            Facing.UP -> Vector2(0f, -0.2f)
            Facing.LEFT -> Vector2(-0.25f, 0.05f)
            Facing.RIGHT -> Vector2(0.25f, 0.05f)
        }
        drawBottomAnchoredSprite(
            image = sprites.egg,
            bottomCenter = helper.position + Vector2(0f, 0.35f) + eggOffset,
            worldWidth = 0.48f,
            worldHeight = 0.48f,
            viewport = viewport,
        )
    }
}

private fun DrawScope.drawOverviewMarker(world: FarmWorldState, viewport: WorldViewport) {
    val center = viewport.toScreen(world.player.position)
    val pulse = (sin(world.animation.elapsedSeconds * 3f) + 1f) * 0.5f
    val radius = max(4f, viewport.pixelsPerUnit * (0.52f + pulse * 0.08f))
    drawCircle(Color(0x55FFF3B0), radius * 1.8f, center)
    drawCircle(Color(0xFFF9E28A), radius, center)
    drawCircle(Color(0xFF513822), radius, center, style = Stroke(max(1.5f, viewport.pixelsPerUnit * 0.1f)))
}

/**
 * A small three-pass ellipse gives sprites contact with the ground without allocating a blur
 * effect per entity. It scales in world coordinates and therefore stays stable while panning.
 */
private fun DrawScope.drawContactShadow(
    center: Vector2,
    worldWidth: Float,
    worldHeight: Float,
    viewport: WorldViewport,
    alpha: Float,
    rotationDegrees: Float = 0f,
) {
    val screenCenter = viewport.toScreen(center)
    val width = max(1f, worldWidth * viewport.pixelsPerUnit)
    val height = max(1f, worldHeight * viewport.pixelsPerUnit)
    rotate(rotationDegrees, pivot = screenCenter) {
        fun shadowLayer(widthScale: Float, heightScale: Float, opacityScale: Float) {
            val layerWidth = width * widthScale
            val layerHeight = height * heightScale
            drawOval(
                color = Color(0xFF17231C).copy(alpha = alpha * opacityScale),
                topLeft = Offset(
                    screenCenter.x - layerWidth * 0.5f,
                    screenCenter.y - layerHeight * 0.5f,
                ),
                size = Size(layerWidth, layerHeight),
            )
        }
        shadowLayer(1f, 1f, 0.22f)
        shadowLayer(0.79f, 0.7f, 0.34f)
        shadowLayer(0.56f, 0.43f, 0.44f)
    }
}

private fun DrawScope.drawBottomAnchoredSprite(
    image: ImageBitmap,
    bottomCenter: Vector2,
    worldWidth: Float,
    worldHeight: Float,
    viewport: WorldViewport,
    alpha: Float = 1f,
    flipHorizontal: Boolean = false,
) {
    val bottom = viewport.toScreen(bottomCenter)
    val width = max(1, (worldWidth * viewport.pixelsPerUnit).roundToInt())
    val height = max(1, (worldHeight * viewport.pixelsPerUnit).roundToInt())
    val left = (bottom.x - width * 0.5f).roundToInt()
    val top = (bottom.y - height).roundToInt()
    val drawBlock: DrawScope.() -> Unit = {
        drawImage(
            image = image,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(width, height),
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.None,
        )
    }
    if (flipHorizontal) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(bottom.x, bottom.y - height * 0.5f)) {
            drawBlock()
        }
    } else {
        drawBlock()
    }
}

private fun DrawScope.drawStretchedTexture(
    image: ImageBitmap,
    bounds: CollisionRect,
    viewport: WorldViewport,
    alpha: Float,
) {
    val topLeft = viewport.toScreen(Vector2(bounds.left, bounds.top))
    drawImage(
        image = image,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(
            max(1, (bounds.width * viewport.pixelsPerUnit).roundToInt()),
            max(1, (bounds.height * viewport.pixelsPerUnit).roundToInt()),
        ),
        alpha = alpha.coerceIn(0f, 1f),
        filterQuality = FilterQuality.Low,
    )
}

/** Tiles a texture in world space so large roads do not expose a blurred, stretched bitmap. */
private fun DrawScope.drawTiledWorldTexture(
    image: ImageBitmap,
    bounds: CollisionRect,
    viewport: WorldViewport,
    tileWorld: Float,
    alpha: Float,
) {
    val visibleLeft = max(bounds.left, viewport.visibleBounds.left - tileWorld)
    val visibleTop = max(bounds.top, viewport.visibleBounds.top - tileWorld)
    val visibleRight = min(bounds.right, viewport.visibleBounds.right + tileWorld)
    val visibleBottom = min(bounds.bottom, viewport.visibleBounds.bottom + tileWorld)
    if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) return
    val visible = CollisionRect(visibleLeft, visibleTop, visibleRight, visibleBottom)
    val firstColumn = floor(visible.left / tileWorld).toInt()
    val lastColumn = ceil(visible.right / tileWorld).toInt()
    val firstRow = floor(visible.top / tileWorld).toInt()
    val lastRow = ceil(visible.bottom / tileWorld).toInt()
    val tilePixels = max(1, (tileWorld * viewport.pixelsPerUnit).roundToInt() + 1)
    for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
        val topLeft = viewport.toScreen(Vector2(column * tileWorld, row * tileWorld))
        drawImage(
            image = image,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(tilePixels, tilePixels),
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium,
        )
    }
}

private fun CollisionShape.toPath(viewport: WorldViewport): Path = when (this) {
    is CollisionRect -> Path().apply {
        val topLeft = viewport.toScreen(Vector2(left, top))
        val bottomRight = viewport.toScreen(Vector2(right, bottom))
        addRect(Rect(topLeft, bottomRight))
    }
    is CollisionCircle -> Path().apply {
        val centerPx = viewport.toScreen(center)
        val radiusPx = radius * viewport.pixelsPerUnit
        addOval(Rect(centerPx - Offset(radiusPx, radiusPx), centerPx + Offset(radiusPx, radiusPx)))
    }
}

private fun CollisionShape.toOrganicPath(
    viewport: WorldViewport,
    seed: Int,
    expand: Float = 0f,
): Path {
    val points = when (this) {
        is CollisionCircle -> {
            val count = 28
            List(count) { index ->
                val angle = index * (PI.toFloat() * 2f / count)
                val jitter = 0.94f + hash01(seed + index * 97) * 0.12f
                val r = max(0.02f, radius + expand) * jitter
                center + Vector2(cos(angle) * r, sin(angle) * r)
            }
        }
        is CollisionRect -> {
            val left = left - expand
            val top = top - expand
            val right = right + expand
            val bottom = bottom + expand
            val countPerEdge = 7
            buildList {
                repeat(countPerEdge) { i ->
                    val t = i.toFloat() / countPerEdge
                    add(Vector2(lerp(left, right, t), top + (hash01(seed + i) - 0.5f) * 0.12f))
                }
                repeat(countPerEdge) { i ->
                    val t = i.toFloat() / countPerEdge
                    add(Vector2(right + (hash01(seed + 100 + i) - 0.5f) * 0.12f, lerp(top, bottom, t)))
                }
                repeat(countPerEdge) { i ->
                    val t = i.toFloat() / countPerEdge
                    add(Vector2(lerp(right, left, t), bottom + (hash01(seed + 200 + i) - 0.5f) * 0.12f))
                }
                repeat(countPerEdge) { i ->
                    val t = i.toFloat() / countPerEdge
                    add(Vector2(left + (hash01(seed + 300 + i) - 0.5f) * 0.12f, lerp(bottom, top, t)))
                }
            }
        }
    }
    return Path().apply {
        if (points.isEmpty()) return@apply
        val first = viewport.toScreen(points.first())
        moveTo(first.x, first.y)
        points.drop(1).forEach { point ->
            val screen = viewport.toScreen(point)
            lineTo(screen.x, screen.y)
        }
        close()
    }
}

private fun CollisionShape.worldBounds(): CollisionRect = when (this) {
    is CollisionCircle -> CollisionRect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    is CollisionRect -> this
}

private fun CollisionShape.worldCenter(): Vector2 = when (this) {
    is CollisionCircle -> center
    is CollisionRect -> center
}

private fun connectedPlotGroups(plots: List<SoilPlotState>): List<List<SoilPlotState>> {
    val remaining = plots.toMutableList()
    val groups = mutableListOf<List<SoilPlotState>>()
    while (remaining.isNotEmpty()) {
        val group = mutableListOf(remaining.removeAt(0))
        var index = 0
        while (index < group.size) {
            val current = group[index++] 
            val connected = remaining.filter { other ->
                current.position.distanceTo(other.position) <= (current.size + other.size) * 0.82f
            }
            group += connected
            remaining.removeAll(connected.toSet())
        }
        groups += group
    }
    return groups
}

private fun boundsOfPlots(plots: List<SoilPlotState>): CollisionRect {
    val left = plots.minOf { it.collider.left }
    val top = plots.minOf { it.collider.top }
    val right = plots.maxOf { it.collider.right }
    val bottom = plots.maxOf { it.collider.bottom }
    return CollisionRect(left, top, right, bottom)
}

private fun lerpColor(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = lerp(from.red, to.red, t),
        green = lerp(from.green, to.green, t),
        blue = lerp(from.blue, to.blue, t),
        alpha = lerp(from.alpha, to.alpha, t),
    )
}

private fun isNearViewport(position: Vector2, margin: Float, viewport: WorldViewport): Boolean =
    viewport.visibleBounds.expanded(margin).contains(position)

private fun Facing.vector(): Vector2 = when (this) {
    Facing.UP -> Vector2.UP
    Facing.DOWN -> Vector2.DOWN
    Facing.LEFT -> Vector2.LEFT
    Facing.RIGHT -> Vector2.RIGHT
}

private fun cardinalFacing(direction: Vector2): Facing = when {
    abs(direction.x) > abs(direction.y) && direction.x < 0f -> Facing.LEFT
    abs(direction.x) > abs(direction.y) -> Facing.RIGHT
    direction.y < 0f -> Facing.UP
    else -> Facing.DOWN
}

private fun frameIndex(seconds: Float, fps: Float, count: Int): Int =
    if (count <= 1) 0 else positiveMod(floor(seconds * fps).toInt(), count)

private fun hash01(value: Int): Float {
    var x = value
    x = (x xor (x ushr 16)) * 0x45d9f3b
    x = (x xor (x ushr 16)) * 0x45d9f3b
    x = x xor (x ushr 16)
    return (x and 0x7fffffff) / Int.MAX_VALUE.toFloat()
}

private fun positiveMod(value: Int, divisor: Int): Int = ((value % divisor) + divisor) % divisor
private fun positiveFraction(value: Float): Float = value - floor(value)
private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount
private fun lerp(start: Vector2, end: Vector2, amount: Float): Vector2 =
    start + (end - start) * amount.coerceIn(0f, 1f)
