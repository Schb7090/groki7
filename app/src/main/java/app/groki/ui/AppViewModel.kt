package app.groki.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.groki.data.GoogleAuth
import app.groki.data.Graph
import app.groki.data.Repository
import app.groki.model.ActionItem
import app.groki.model.Decision
import app.groki.model.Snapshot
import app.groki.model.SourceKind
import app.groki.model.Status
import app.groki.sync.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val snapshot: Snapshot = Snapshot(),
    val syncing: Boolean = false,
    val error: String? = null,
    val pendingConsent: PendingIntent? = null,
    val needsSignIn: Boolean = false,
)

/** Visszavonható művelet a snackbarhoz. */
data class UndoEvent(val message: String, val undo: suspend () -> Unit)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Graph.repo
    private val transient = MutableStateFlow(UiState())
    val undo = MutableStateFlow<UndoEvent?>(null)

    val state: StateFlow<UiState> = combine(repo.snapshot, transient) { snap, t ->
        t.copy(snapshot = snap, needsSignIn = snap.lastSyncMs == 0L)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(snapshot = repo.snapshot.value))

    init {
        if (repo.snapshot.value.lastSyncMs != 0L) refresh(silent = true)
    }

    fun refresh(silent: Boolean = false) {
        if (transient.value.syncing) return
        viewModelScope.launch {
            transient.value = transient.value.copy(syncing = true, error = null)
            handle(repo.sync(), silent)
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            transient.value = transient.value.copy(pendingConsent = null, syncing = true)
            when (val r = GoogleAuth.tokenFromConsent(getApplication(), data)) {
                is GoogleAuth.Result.Token -> handle(repo.syncWith(r.accessToken), silent = false)
                is GoogleAuth.Result.Failed -> transient.value =
                    transient.value.copy(syncing = false, error = "Bejelentkezés megszakítva: ${r.message}")
                is GoogleAuth.Result.NeedsConsent -> transient.value =
                    transient.value.copy(syncing = false, pendingConsent = r.intent)
            }
        }
    }

    private suspend fun handle(r: Repository.SyncResult, silent: Boolean) {
        transient.value = when (r) {
            is Repository.SyncResult.Ok -> {
                Notifier.notifyImportant(getApplication(), repo)
                transient.value.copy(syncing = false, error = null, needsSignIn = false)
            }
            is Repository.SyncResult.NeedsConsent ->
                // csendes frissítésnél nem dobunk fel fiókválasztót, csak jelezzük
                transient.value.copy(syncing = false, pendingConsent = if (silent) null else r.auth.intent,
                    error = if (silent) "A Google hozzáférés megújítása szükséges." else null)
            is Repository.SyncResult.Error -> transient.value.copy(syncing = false, error = r.message)
        }
    }

    fun consentLaunched() { transient.value = transient.value.copy(pendingConsent = null) }
    fun dismissError() { transient.value = transient.value.copy(error = null) }

    private fun change(item: ActionItem, msg: String, block: suspend () -> Unit) {
        val prev = repo.snapshot.value.stateOf(item.id)
        viewModelScope.launch {
            block()
            undo.value = UndoEvent(msg) { repo.restore(item.id, prev) }
        }
    }

    fun done(item: ActionItem) = change(item, "Kész: ${item.title.take(40)}") {
        repo.setStatus(item.id, Status.DONE)
    }

    fun snooze(item: ActionItem, hours: Long) = change(item, "Elhalasztva: ${item.title.take(40)}") {
        repo.setStatus(item.id, Status.SNOOZED, System.currentTimeMillis() + hours * 3_600_000)
    }

    fun dismiss(item: ActionItem) = change(item, "Elvetve: ${item.title.take(40)}") {
        repo.setStatus(item.id, Status.DISMISSED)
    }

    fun reopen(item: ActionItem) = viewModelScope.launch { repo.setStatus(item.id, Status.OPEN) }

    fun decide(item: ActionItem, d: Decision) = change(
        item, if (d == Decision.WANT) "Megjelölve: kérem" else "Nem kérem: ${item.title.take(40)}",
    ) { repo.decide(item.id, d) }

    fun runUndo(e: UndoEvent) = viewModelScope.launch { e.undo() }

    /** Drive-mappa a rendszer fájlválasztójából; az olvasási jogot tartósan megtartjuk. */
    fun useFolder(uri: Uri) = viewModelScope.launch {
        runCatching {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        repo.setSource(SourceKind.FOLDER, treeUri = uri.toString())
        refresh()
    }

    fun useBridge(url: String) = viewModelScope.launch {
        val clean = url.trim()
        if (!clean.startsWith("https://script.google.com/")) {
            transient.value = transient.value.copy(
                error = "Ez nem Apps Script webapp URL. A https://script.google.com/macros/s/…/exec formát várom.")
            return@launch
        }
        repo.setSource(SourceKind.BRIDGE, bridgeUrl = clean)
        refresh()
    }

    fun useGoogle() = viewModelScope.launch {
        repo.setSource(SourceKind.GOOGLE)
        refresh()
    }

    /** Vissza a forrásválasztóhoz; a teendők állapota megmarad. */
    fun changeSource() = viewModelScope.launch {
        releaseFolder()
        repo.setSource(null)
        transient.value = UiState(needsSignIn = true)
    }

    private fun releaseFolder() {
        val tree = repo.snapshot.value.treeUri ?: return
        runCatching {
            getApplication<Application>().contentResolver
                .releasePersistableUriPermission(Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun signOut() = viewModelScope.launch {
        releaseFolder()
        repo.signOut()
        transient.value = UiState(needsSignIn = true)
    }
}
