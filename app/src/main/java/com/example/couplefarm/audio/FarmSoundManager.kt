package com.example.couplefarm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.annotation.RawRes
import com.example.couplefarm.R
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/** Short, low-latency sound effects used by the farm simulation. */
enum class FarmSoundEffect(
    @param:RawRes internal val resourceId: Int,
    internal val baseVolume: Float = 1f,
    internal val priority: Int = 1,
) {
    STEP_GRASS_1(R.raw.sfx_step_grass_1, baseVolume = 0.34f),
    STEP_GRASS_2(R.raw.sfx_step_grass_2, baseVolume = 0.34f),
    STEP_DIRT_1(R.raw.sfx_step_dirt_1, baseVolume = 0.38f),
    STEP_DIRT_2(R.raw.sfx_step_dirt_2, baseVolume = 0.38f),
    PICKUP_EGG(R.raw.sfx_pickup_egg, baseVolume = 0.68f, priority = 2),
    PICKUP_APPLE(R.raw.sfx_pickup_apple, baseVolume = 0.68f, priority = 2),
    CHICKEN_CLUCK_1(R.raw.sfx_chicken_cluck_1, baseVolume = 0.48f),
    CHICKEN_CLUCK_2(R.raw.sfx_chicken_cluck_2, baseVolume = 0.48f),
    CHICK_PEEP(R.raw.sfx_chick_peep, baseVolume = 0.46f),
    EGG_HATCH(R.raw.sfx_egg_hatch, baseVolume = 0.62f, priority = 2),
    TREE_SHAKE(R.raw.sfx_tree_shake, baseVolume = 0.58f),
    AXE_SWING(R.raw.sfx_axe_swing, baseVolume = 0.52f),
    AXE_HIT(R.raw.sfx_axe_hit, baseVolume = 0.72f, priority = 2),
    PICKAXE_SWING(R.raw.sfx_pickaxe_swing, baseVolume = 0.48f),
    PICKAXE_HIT(R.raw.sfx_pickaxe_hit, baseVolume = 0.66f, priority = 2),
    ROCK_BREAK(R.raw.sfx_rock_break, baseVolume = 0.7f, priority = 2),
    HOE_DIG(R.raw.sfx_hoe_dig, baseVolume = 0.52f),
    WATERING(R.raw.sfx_watering, baseVolume = 0.46f),
    SEED_PLANT(R.raw.sfx_seed_plant, baseVolume = 0.46f),
    TREE_FALL(R.raw.sfx_tree_fall, baseVolume = 0.76f, priority = 3),
    WATER_SPLASH(R.raw.sfx_water_splash, baseVolume = 0.58f),
    FISHING_CAST(R.raw.sfx_fishing_cast, baseVolume = 0.62f),
    FISHING_BITE(R.raw.sfx_fishing_bite, baseVolume = 0.72f, priority = 2),
    FISHING_REEL(R.raw.sfx_fishing_reel, baseVolume = 0.58f),
    HARVEST(R.raw.sfx_harvest, baseVolume = 0.64f, priority = 2),
    STORE_ITEM(R.raw.sfx_store_item, baseVolume = 0.62f),
    SALE(R.raw.sfx_sale, baseVolume = 0.74f, priority = 3),
    TOOL_SELECT(R.raw.sfx_tool_select, baseVolume = 0.48f),
    TRUCK_ARRIVE(R.raw.sfx_truck_arrive, baseVolume = 0.54f, priority = 2),
    TRUCK_DEPART(R.raw.sfx_truck_depart, baseVolume = 0.54f, priority = 2),
    DOG_BARK(R.raw.sfx_dog_bark, baseVolume = 0.62f, priority = 2),
    CAT_MEOW(R.raw.sfx_cat_meow, baseVolume = 0.58f, priority = 2),
    PIG_OINK(R.raw.sfx_pig_oink, baseVolume = 0.58f, priority = 2),
    COW_MOO(R.raw.sfx_cow_moo, baseVolume = 0.62f, priority = 2),
    BICYCLE_BELL(R.raw.sfx_bicycle_bell, baseVolume = 0.56f, priority = 2),
    PICKUP_CHICKEN(R.raw.sfx_chicken_cluck_1, baseVolume = 0.52f, priority = 2),
    BIRD_CHIRP(R.raw.sfx_bird_chirp, baseVolume = 0.38f),
    BUTTERFLY_FLUTTER(R.raw.sfx_butterfly_flutter, baseVolume = 0.24f),
}

enum class FarmSurface { GRASS, DIRT }

enum class FarmPickup { EGG, APPLE }

/**
 * Owns the game's [SoundPool]. Construct once for a farm screen/session and call
 * [close] when that owner leaves composition or is destroyed.
 *
 * All files are preloaded during construction. Calls made before an individual
 * sample finishes loading are safely ignored instead of blocking the game loop.
 */
class FarmSoundManager(context: Context) : Closeable {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val released = AtomicBoolean(false)
    private val loadedSampleIds = ConcurrentHashMap.newKeySet<Int>()
    private val musicTracks: List<Int> = discoverMusicTracks()
    private var musicPlayer: MediaPlayer? = null
    private var nextMusicTrack = 0
    private var pausedByLifecycle = false

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val sampleIds: Map<FarmSoundEffect, Int>

    @Volatile
    var enabled: Boolean = preferences.getBoolean(PREFERENCE_SFX, true)
        set(value) {
            field = value
            preferences.edit().putBoolean(PREFERENCE_SFX, value).apply()
        }

    @Volatile
    var musicEnabled: Boolean = preferences.getBoolean(PREFERENCE_MUSIC, true)
        set(value) {
            field = value
            preferences.edit().putBoolean(PREFERENCE_MUSIC, value).apply()
            if (value) startOrResumeMusic() else pauseMusic()
        }

    @Volatile
    var masterVolume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    val isReady: Boolean
        get() = loadedSampleIds.size == sampleIds.size

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == LOAD_OK && !released.get()) {
                loadedSampleIds.add(sampleId)
            }
        }

        sampleIds = FarmSoundEffect.entries.associateWith { effect ->
            soundPool.load(applicationContext, effect.resourceId, LOAD_PRIORITY)
        }
        startOrResumeMusic()
    }

    /** Returns the SoundPool stream id, or `0` if muted, released, or still loading. */
    fun play(
        effect: FarmSoundEffect,
        volume: Float = 1f,
        rate: Float = 1f,
        loop: Int = 0,
    ): Int {
        if (!enabled || released.get() || effect in SILENT_EFFECTS) return NO_STREAM
        val sampleId = sampleIds[effect] ?: return NO_STREAM
        if (sampleId !in loadedSampleIds) return NO_STREAM

        val finalVolume = (effect.baseVolume * masterVolume * volume).coerceIn(0f, 1f)
        return soundPool.play(
            sampleId,
            finalVolume,
            finalVolume,
            effect.priority,
            loop,
            rate.coerceIn(MIN_RATE, MAX_RATE),
        )
    }

    /** Alternates samples and varies pitch slightly so footsteps do not sound mechanical. */
    fun playFootstep(surface: FarmSurface): Int {
        val effect = when (surface) {
            FarmSurface.GRASS -> randomOf(FarmSoundEffect.STEP_GRASS_1, FarmSoundEffect.STEP_GRASS_2)
            FarmSurface.DIRT -> randomOf(FarmSoundEffect.STEP_DIRT_1, FarmSoundEffect.STEP_DIRT_2)
        }
        return play(effect, rate = randomRate(0.94f, 1.06f))
    }

    fun playPickup(pickup: FarmPickup): Int = play(
        when (pickup) {
            FarmPickup.EGG -> FarmSoundEffect.PICKUP_EGG
            FarmPickup.APPLE -> FarmSoundEffect.PICKUP_APPLE
        },
    )

    /** Gallinas, puesta y nacimiento son silenciosos; solo su recogida produce sonido. */
    fun playChicken(): Int = NO_STREAM
    fun playChickPeep(): Int = NO_STREAM
    fun playEggHatch(): Int = NO_STREAM
    fun playTreeShake(): Int = play(FarmSoundEffect.TREE_SHAKE)
    fun playAxeSwing(): Int = play(FarmSoundEffect.AXE_SWING, rate = randomRate(0.96f, 1.04f))
    fun playAxeHit(): Int = play(FarmSoundEffect.AXE_HIT, rate = randomRate(0.94f, 1.05f))
    fun playPickaxeSwing(): Int = play(FarmSoundEffect.PICKAXE_SWING, rate = randomRate(0.97f, 1.04f))
    fun playPickaxeHit(): Int = play(FarmSoundEffect.PICKAXE_HIT, rate = randomRate(0.96f, 1.04f))
    fun playRockBreak(): Int = play(FarmSoundEffect.ROCK_BREAK, rate = randomRate(0.97f, 1.03f))
    fun playHoe(): Int = play(FarmSoundEffect.HOE_DIG, rate = randomRate(0.97f, 1.04f))
    fun playWatering(): Int = play(FarmSoundEffect.WATERING, rate = randomRate(0.98f, 1.03f))
    fun playPlantSeed(): Int = play(FarmSoundEffect.SEED_PLANT, rate = randomRate(0.98f, 1.04f))
    fun playTreeFall(): Int = play(FarmSoundEffect.TREE_FALL)
    fun playWaterSplash(): Int = play(FarmSoundEffect.WATER_SPLASH, rate = randomRate(0.96f, 1.05f))
    fun playFishingCast(): Int = play(FarmSoundEffect.FISHING_CAST)
    fun playFishingBite(): Int = play(FarmSoundEffect.FISHING_BITE)
    fun playFishingReel(): Int = play(FarmSoundEffect.FISHING_REEL)
    fun playHarvest(): Int = play(FarmSoundEffect.HARVEST, rate = randomRate(0.96f, 1.04f))
    fun playStoreItem(): Int = play(FarmSoundEffect.STORE_ITEM)
    fun playSale(): Int = play(FarmSoundEffect.SALE)
    fun playToolSelect(): Int = play(FarmSoundEffect.TOOL_SELECT)
    fun playTruckArrive(): Int = play(FarmSoundEffect.TRUCK_ARRIVE)
    fun playTruckDepart(): Int = play(FarmSoundEffect.TRUCK_DEPART)
    fun playDog(): Int = play(FarmSoundEffect.DOG_BARK, rate = randomRate(.96f, 1.04f))
    fun playCat(): Int = play(FarmSoundEffect.CAT_MEOW, rate = randomRate(.96f, 1.05f))
    fun playPig(): Int = play(FarmSoundEffect.PIG_OINK, rate = randomRate(.95f, 1.04f))
    fun playCow(): Int = play(FarmSoundEffect.COW_MOO, rate = randomRate(.96f, 1.03f))
    fun playChickenPickup(): Int = play(FarmSoundEffect.PICKUP_CHICKEN)
    fun playBicycleBell(): Int = play(FarmSoundEffect.BICYCLE_BELL)
    fun playBirdChirp(volume: Float = 1f): Int =
        play(FarmSoundEffect.BIRD_CHIRP, volume = volume, rate = randomRate(.96f, 1.06f))

    fun playButterflyFlutter(volume: Float = 1f): Int =
        play(FarmSoundEffect.BUTTERFLY_FLUTTER, volume = volume, rate = randomRate(.94f, 1.08f))
    fun playBicycleRoll(surface: FarmSurface): Int {
        val effect = when (surface) {
            FarmSurface.GRASS -> randomOf(FarmSoundEffect.STEP_GRASS_1, FarmSoundEffect.STEP_GRASS_2)
            FarmSurface.DIRT -> randomOf(FarmSoundEffect.STEP_DIRT_1, FarmSoundEffect.STEP_DIRT_2)
        }
        return play(effect, volume = .34f, rate = randomRate(.72f, .82f))
    }

    fun stop(streamId: Int) {
        if (!released.get() && streamId != NO_STREAM) soundPool.stop(streamId)
    }

    fun pauseAll() {
        if (!released.get()) {
            pausedByLifecycle = true
            soundPool.autoPause()
            pauseMusic()
        }
    }

    fun resumeAll() {
        if (!released.get()) {
            pausedByLifecycle = false
            soundPool.autoResume()
            startOrResumeMusic()
        }
    }

    override fun close() {
        if (released.compareAndSet(false, true)) {
            loadedSampleIds.clear()
            musicPlayer?.setOnCompletionListener(null)
            musicPlayer?.release()
            musicPlayer = null
            soundPool.release()
        }
    }

    private fun startOrResumeMusic() {
        if (!musicEnabled || pausedByLifecycle || released.get() || musicTracks.isEmpty()) return
        musicPlayer?.let { player ->
            if (!player.isPlaying) player.start()
            return
        }
        repeat(musicTracks.size) {
            val trackId = musicTracks[nextMusicTrack % musicTracks.size]
            nextMusicTrack = (nextMusicTrack + 1) % musicTracks.size
            val player = runCatching { MediaPlayer.create(applicationContext, trackId) }.getOrNull()
                ?: return@repeat
            musicPlayer = player.apply {
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                isLooping = musicTracks.size == 1
                setOnCompletionListener { finished ->
                    finished.setOnCompletionListener(null)
                    finished.release()
                    musicPlayer = null
                    startOrResumeMusic()
                }
                start()
            }
            return
        }
    }

    private fun pauseMusic() {
        musicPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun playBakeryMusic() {
        if (!musicEnabled || released.get()) return
        musicPlayer?.setOnCompletionListener(null)
        musicPlayer?.release()
        musicPlayer = null
        val bakeryTrack = runCatching {
            val field = R.raw::class.java.getField("music_bakery_cozy")
            field.getInt(null)
        }.getOrNull()
        if (bakeryTrack != null) {
            val player = runCatching { MediaPlayer.create(applicationContext, bakeryTrack) }.getOrNull()
            if (player != null) {
                player.isLooping = true
                player.setVolume(MUSIC_VOLUME * 1.15f, MUSIC_VOLUME * 1.15f)
                player.start()
                musicPlayer = player
            }
        }
    }

    fun resumeMeadowMusic() {
        if (!musicEnabled || released.get()) return
        musicPlayer?.setOnCompletionListener(null)
        musicPlayer?.release()
        musicPlayer = null
        startOrResumeMusic()
    }

    /**
     * Android no permite carpetas dentro de res/raw. Cualquier audio cuyo recurso
     * empiece con `music_` entra automáticamente en esta lista al recompilar.
     */
    private fun discoverMusicTracks(): List<Int> = R.raw::class.java.fields
        .asSequence()
        .filter { it.name.startsWith(MUSIC_RESOURCE_PREFIX) && it.name != "music_bakery_cozy" }
        .sortedBy { it.name }
        .mapNotNull { field -> runCatching { field.getInt(null) }.getOrNull() }
        .toList()

    private fun randomOf(first: FarmSoundEffect, second: FarmSoundEffect): FarmSoundEffect =
        if (Random.nextBoolean()) first else second

    private fun randomRate(from: Float, until: Float): Float =
        from + Random.nextFloat() * (until - from)

    private companion object {
        val SILENT_EFFECTS = setOf(
            FarmSoundEffect.CHICKEN_CLUCK_1,
            FarmSoundEffect.CHICKEN_CLUCK_2,
            FarmSoundEffect.CHICK_PEEP,
            FarmSoundEffect.EGG_HATCH,
        )
        const val MAX_STREAMS = 8
        const val LOAD_PRIORITY = 1
        const val LOAD_OK = 0
        const val NO_STREAM = 0
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
        const val MUSIC_VOLUME = 0.24f
        const val MUSIC_RESOURCE_PREFIX = "music_"
        const val PREFERENCES_NAME = "couple_farm_audio"
        const val PREFERENCE_SFX = "sound_effects_enabled"
        const val PREFERENCE_MUSIC = "music_enabled"
    }
}
