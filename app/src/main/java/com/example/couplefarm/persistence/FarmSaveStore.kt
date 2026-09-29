package com.example.couplefarm.persistence

import android.content.Context
import android.util.AtomicFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.couplefarm.game.FarmWorldState
import com.example.couplefarm.game.createDefaultFarmWorld
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class FarmRestoreStatus { LOADED, NOT_FOUND, INVALID }

data class FarmRestoreResult(
    val state: FarmWorldState,
    val status: FarmRestoreStatus,
    val savedAtEpochMillis: Long? = null,
    val schemaVersion: Int? = null,
    val error: Throwable? = null,
)

data class FarmSaveResult(
    val success: Boolean,
    val savedAtEpochMillis: Long? = null,
    val bytesWritten: Int = 0,
    val error: Throwable? = null,
)

/**
 * Crash-safe local persistence for a complete [FarmWorldState].
 *
 * Call [track] whenever the immutable world snapshot changes, then [attach] once
 * to the screen/activity lifecycle. The latest tracked state is written on pause,
 * stop and close. While attached, a debounced periodic autosave also protects long
 * play sessions. File I/O never mutates the supplied snapshots.
 */
class FarmSaveStore(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
    private val autosaveIntervalMillis: Long = DEFAULT_AUTOSAVE_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : DefaultLifecycleObserver, Closeable {
    private val atomicFile: AtomicFile
    private val latestState = AtomicReference<FarmWorldState?>(null)
    private val revision = AtomicLong(0L)
    private val savedRevision = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "couple-farm-autosave").apply { isDaemon = true }
    }

    @Volatile
    private var autosaveTask: ScheduledFuture<*>? = null

    @Volatile
    private var attachedLifecycle: Lifecycle? = null

    @Volatile
    var lastError: Throwable? = null
        private set

    @Volatile
    var lastSuccessfulSaveEpochMillis: Long? = null
        private set

    init {
        require(SAFE_FILE_NAME.matches(fileName)) { "Unsafe save filename: $fileName" }
        require(autosaveIntervalMillis >= MIN_AUTOSAVE_INTERVAL_MILLIS) {
            "Autosave interval must be at least $MIN_AUTOSAVE_INTERVAL_MILLIS ms"
        }
        val saveDirectory = File(context.applicationContext.filesDir, SAVE_DIRECTORY)
        atomicFile = AtomicFile(File(saveDirectory, fileName))
    }

    /**
     * Loads the last valid snapshot. Missing/corrupt/unsupported data returns
     * [fallback] and is reported through [FarmRestoreResult.status].
     */
    fun restoreDetailed(fallback: FarmWorldState = createDefaultFarmWorld()): FarmRestoreResult {
        check(!closed.get()) { "FarmSaveStore is closed" }
        val decoded = try {
            val bytes = atomicFile.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= MAX_SAVE_BYTES) { "Farm save exceeds $MAX_SAVE_BYTES bytes" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            FarmWorldJsonCodec.decode(bytes, fallback)
        } catch (_: FileNotFoundException) {
            null
        } catch (error: Throwable) {
            lastError = error
            installSnapshot(fallback, alreadyPersisted = false)
            return FarmRestoreResult(
                state = fallback,
                status = FarmRestoreStatus.INVALID,
                error = error,
            )
        }

        if (decoded == null) {
            installSnapshot(fallback, alreadyPersisted = false)
            return FarmRestoreResult(fallback, FarmRestoreStatus.NOT_FOUND)
        }

        lastError = null
        lastSuccessfulSaveEpochMillis = decoded.savedAtEpochMillis
        installSnapshot(decoded.state, alreadyPersisted = true)
        return FarmRestoreResult(
            state = decoded.state,
            status = FarmRestoreStatus.LOADED,
            savedAtEpochMillis = decoded.savedAtEpochMillis,
            schemaVersion = decoded.sourceVersion,
        )
    }

    fun restore(fallback: FarmWorldState = createDefaultFarmWorld()): FarmWorldState =
        restoreDetailed(fallback).state

    /** Updates the immutable snapshot used by pause/close and periodic autosaves. */
    fun track(state: FarmWorldState) {
        if (closed.get()) return
        val previous = latestState.getAndSet(state)
        if (previous !== state) revision.incrementAndGet()
    }

    /** Immediately and atomically persists [state]. Safe to call from lifecycle callbacks. */
    fun saveNow(state: FarmWorldState): FarmSaveResult {
        if (closed.get()) return closedResult()
        track(state)
        return persistLatest(force = true)
    }

    /** Immediately persists the latest value supplied through [track]. */
    fun flush(): FarmSaveResult {
        if (closed.get()) return closedResult()
        return persistLatest(force = true)
    }

    /**
     * Observes lifecycle pause/stop/destroy and starts periodic autosave. Calling
     * this again safely moves the store to the new lifecycle.
     */
    @Synchronized
    fun attach(lifecycle: Lifecycle): FarmSaveStore {
        check(!closed.get()) { "FarmSaveStore is closed" }
        if (attachedLifecycle !== lifecycle) {
            attachedLifecycle?.removeObserver(this)
            attachedLifecycle = lifecycle
            lifecycle.addObserver(this)
        }
        startAutosave()
        return this
    }

    @Synchronized
    fun detach() {
        attachedLifecycle?.removeObserver(this)
        attachedLifecycle = null
        stopAutosave()
    }

    @Synchronized
    fun startAutosave() {
        if (closed.get() || autosaveTask?.isCancelled == false) return
        autosaveTask = scheduler.scheduleWithFixedDelay(
            { runCatching { persistLatest(force = false) }.onFailure { lastError = it } },
            autosaveIntervalMillis,
            autosaveIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized
    fun stopAutosave() {
        autosaveTask?.cancel(false)
        autosaveTask = null
    }

    override fun onPause(owner: LifecycleOwner) {
        persistLatest(force = false)
    }

    override fun onStop(owner: LifecycleOwner) {
        persistLatest(force = false)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stopAutosave()
        // Persistence is still allowed internally after the closed flag flips.
        persistLatest(force = false, allowClosed = true)
        attachedLifecycle?.removeObserver(this)
        attachedLifecycle = null
        scheduler.shutdown()
    }

    private fun installSnapshot(state: FarmWorldState, alreadyPersisted: Boolean) {
        latestState.set(state)
        val newRevision = revision.incrementAndGet()
        savedRevision.set(if (alreadyPersisted) newRevision else newRevision - 1L)
    }

    private fun persistLatest(force: Boolean, allowClosed: Boolean = false): FarmSaveResult {
        if (closed.get() && !allowClosed) return closedResult()
        val snapshot = latestState.get() ?: return FarmSaveResult(success = true)
        val snapshotRevision = revision.get()
        if (!force && snapshotRevision <= savedRevision.get()) {
            return FarmSaveResult(
                success = true,
                savedAtEpochMillis = lastSuccessfulSaveEpochMillis,
            )
        }

        synchronized(writeLock) {
            if (!force && snapshotRevision <= savedRevision.get()) {
                return FarmSaveResult(
                    success = true,
                    savedAtEpochMillis = lastSuccessfulSaveEpochMillis,
                )
            }
            val savedAt = clock()
            var output: java.io.FileOutputStream? = null
            return try {
                val bytes = FarmWorldJsonCodec.encode(snapshot, savedAt)
                check(bytes.size <= MAX_SAVE_BYTES) { "Farm save exceeds $MAX_SAVE_BYTES bytes" }
                atomicFile.baseFile.parentFile?.let { directory ->
                    check(directory.exists() || directory.mkdirs()) { "Unable to create save directory" }
                }
                val stream = atomicFile.startWrite()
                output = stream
                stream.write(bytes)
                atomicFile.finishWrite(stream)
                savedRevision.updateAndGet { old -> maxOf(old, snapshotRevision) }
                lastSuccessfulSaveEpochMillis = savedAt
                lastError = null
                FarmSaveResult(true, savedAt, bytes.size)
            } catch (error: Throwable) {
                output?.let { runCatching { atomicFile.failWrite(it) } }
                lastError = error
                FarmSaveResult(false, error = error)
            }
        }
    }

    private fun closedResult(): FarmSaveResult {
        val error = IllegalStateException("FarmSaveStore is closed")
        lastError = error
        return FarmSaveResult(false, error = error)
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "farm_world.json"
        const val SAVE_DIRECTORY = "saves"
        const val DEFAULT_AUTOSAVE_INTERVAL_MILLIS = 20_000L
        const val MIN_AUTOSAVE_INTERVAL_MILLIS = 1_000L
        const val MAX_SAVE_BYTES = 4 * 1024 * 1024
        val SAFE_FILE_NAME = Regex("[A-Za-z0-9._-]+")
    }
}
