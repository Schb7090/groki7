package app.groki.model

import kotlinx.serialization.Serializable

@Serializable
enum class Priority { HIGH, MEDIUM, LOW }

@Serializable
enum class Category { SECURITY, PROMO, MONEY, TASK }

@Serializable
enum class Status { OPEN, DONE, SNOOZED, DISMISSED }

/** Egy teendő a Grok Task Extractor összefoglalójából. */
@Serializable
data class ActionItem(
    val id: String,
    val title: String,
    val priority: Priority,
    val category: Category,
    val owner: String? = null,
    val due: String? = null,
    val dueEpochMs: Long? = null,
    val details: String? = null,
    val nextStep: String? = null,
    val links: List<String> = emptyList(),
)

@Serializable
data class Summary(
    val docId: String,
    val title: String,
    val modifiedMs: Long,
    val generatedAt: String? = null,
    val executiveSummary: String? = null,
    val nextSteps: List<String> = emptyList(),
    val items: List<ActionItem> = emptyList(),
)

/** Egy noreply@x.ai értesítő levél (Grok automatizmus futás). */
@Serializable
data class GrokMail(
    val id: String,
    val threadId: String,
    val subject: String,
    val automation: String,
    val snippet: String,
    val dateMs: Long,
    val unread: Boolean,
)

@Serializable
data class ItemState(
    val status: Status = Status.OPEN,
    val snoozeUntilMs: Long? = null,
    val decision: Decision? = null,
    val updatedMs: Long = 0,
)

@Serializable
enum class Decision { WANT, SKIP }

/** Honnan jön az adat. */
@Serializable
enum class SourceKind { FOLDER, BRIDGE, GOOGLE }

@Serializable
data class Snapshot(
    val source: SourceKind? = null,
    val treeUri: String? = null,
    val bridgeUrl: String? = null,
    val summary: Summary? = null,
    val mails: List<GrokMail> = emptyList(),
    val states: Map<String, ItemState> = emptyMap(),
    val notifiedIds: Set<String> = emptySet(),
    val lastSyncMs: Long = 0,
    val accountHint: String? = null,
) {
    fun stateOf(id: String): ItemState = states[id] ?: ItemState()

    fun isVisible(item: ActionItem, now: Long): Boolean {
        val s = stateOf(item.id)
        return when (s.status) {
            Status.OPEN -> true
            Status.SNOOZED -> (s.snoozeUntilMs ?: 0) <= now
            Status.DONE, Status.DISMISSED -> false
        }
    }

    fun openItems(now: Long = System.currentTimeMillis()): List<ActionItem> =
        summary?.items.orEmpty().filter { isVisible(it, now) }.sortedWith(itemOrder)

    fun closedItems(): List<ActionItem> = summary?.items.orEmpty().filter {
        val st = stateOf(it.id).status
        st == Status.DONE || st == Status.DISMISSED
    }

    companion object {
        val itemOrder: Comparator<ActionItem> = compareBy<ActionItem>(
            { if (it.category == Category.SECURITY) 0 else 1 },
            { it.priority.ordinal },
            { it.dueEpochMs ?: Long.MAX_VALUE },
        )
    }
}
