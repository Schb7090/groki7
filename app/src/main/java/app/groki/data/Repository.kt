package app.groki.data

import android.content.Context
import app.groki.model.Decision
import app.groki.model.ItemState
import app.groki.model.Snapshot
import app.groki.model.Status
import app.groki.parse.SummaryParser
import app.groki.widget.TopItemsWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.StateFlow

class Repository(private val context: Context) {
    private val store = LocalStore(context)
    val snapshot: StateFlow<Snapshot> = store.state

    sealed interface SyncResult {
        data class Ok(val newSummary: Boolean, val newMails: Int) : SyncResult
        data class NeedsConsent(val auth: GoogleAuth.Result.NeedsConsent) : SyncResult
        data class Error(val message: String) : SyncResult
    }

    suspend fun sync(): SyncResult {
        return when (val auth = GoogleAuth.authorize(context)) {
            is GoogleAuth.Result.NeedsConsent -> SyncResult.NeedsConsent(auth)
            is GoogleAuth.Result.Failed -> SyncResult.Error(auth.message)
            is GoogleAuth.Result.Token -> syncWith(auth.accessToken)
        }
    }

    suspend fun syncWith(token: String): SyncResult = try {
        val api = GoogleApi(token)
        val before = store.state.value
        val mails = api.grokMails(before.mails.associateBy { it.id })
        val doc = api.latestSummaryDoc()
        val old = before.summary
        val summary = when {
            doc == null -> old
            old != null && old.docId == doc.id && old.modifiedMs == doc.modifiedMs -> old
            else -> SummaryParser.parse(api.exportDoc(doc.id), doc.id, doc.name, doc.modifiedMs)
        }
        val newMails = mails.count { m -> before.mails.none { it.id == m.id } }
        store.update { s ->
            val liveIds = summary?.items.orEmpty().map { it.id }.toSet()
            s.copy(
                summary = summary,
                mails = mails,
                lastSyncMs = System.currentTimeMillis(),
                // a már nem szereplő elemek állapotát 30 nap után eldobjuk
                states = s.states.filter { (id, st) ->
                    id in liveIds || System.currentTimeMillis() - st.updatedMs < 30L * 86_400_000
                },
            )
        }
        refreshWidget()
        SyncResult.Ok(newSummary = summary?.docId != old?.docId, newMails = newMails)
    } catch (e: HttpException) {
        SyncResult.Error(
            when (e.code) {
                401 -> "A Google hozzáférés lejárt, jelentkezz be újra."
                403 -> "A Google megtagadta a hozzáférést (Gmail/Drive API engedélyezve van?)."
                else -> e.message ?: "Hálózati hiba"
            }
        )
    } catch (e: Exception) {
        SyncResult.Error(e.message ?: "Ismeretlen hiba")
    }

    suspend fun setStatus(id: String, status: Status, snoozeUntilMs: Long? = null) {
        store.update { s ->
            val prev = s.stateOf(id)
            s.copy(states = s.states + (id to prev.copy(
                status = status,
                snoozeUntilMs = snoozeUntilMs,
                updatedMs = System.currentTimeMillis(),
            )))
        }
        refreshWidget()
    }

    suspend fun restore(id: String, state: ItemState) {
        store.update { s -> s.copy(states = s.states + (id to state)) }
        refreshWidget()
    }

    suspend fun decide(id: String, decision: Decision) {
        store.update { s ->
            val prev = s.stateOf(id)
            val status = if (decision == Decision.SKIP) Status.DISMISSED else prev.status
            s.copy(states = s.states + (id to prev.copy(
                decision = decision, status = status, updatedMs = System.currentTimeMillis(),
            )))
        }
        refreshWidget()
    }

    suspend fun markNotified(ids: Collection<String>) {
        store.update { it.copy(notifiedIds = (it.notifiedIds + ids).toList().takeLast(500).toSet()) }
    }

    suspend fun signOut() {
        store.clear()
        refreshWidget()
    }

    private suspend fun refreshWidget() {
        runCatching { TopItemsWidget().updateAll(context) }
    }
}

object Graph {
    lateinit var repo: Repository
        private set

    fun init(context: Context) {
        if (!::repo.isInitialized) repo = Repository(context.applicationContext)
    }
}
