package app.groki.data

import android.content.Context
import app.groki.model.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/** Offline gyorsítótár: egyetlen JSON fájl az app privát tárhelyén. */
class LocalStore(context: Context) {
    private val file = File(context.filesDir, "snapshot.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Snapshot> = _state

    private fun load(): Snapshot = runCatching {
        if (file.exists()) json.decodeFromString(Snapshot.serializer(), file.readText()) else Snapshot()
    }.getOrElse { Snapshot() }

    suspend fun update(f: (Snapshot) -> Snapshot): Snapshot = lock.withLock {
        val next = f(_state.value)
        _state.value = next
        val tmp = File(file.parentFile, "snapshot.json.tmp")
        tmp.writeText(json.encodeToString(Snapshot.serializer(), next))
        tmp.renameTo(file)
        next
    }

    suspend fun clear() = update { Snapshot() }
}
