package app.groki.parse

import app.groki.model.ActionItem
import app.groki.model.Category
import app.groki.model.Priority
import app.groki.model.Summary
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A Grok Task Extractor "Action Items and Next Steps Summary" dokumentumának
 * feldolgozása. Markdown- és sima szöveges exportot is kezel, mert a Drive
 * exportformátuma nem garantáltan azonos.
 */
object SummaryParser {
    val zone: ZoneId = ZoneId.of("Europe/Budapest")

    private val heading = Regex("""^\s*#{1,6}\s+(.*)$""")
    private val fieldLine = Regex(
        """^\s*[-*•]?\s*(?:\*\*)?(owner|suggested priority|priority|due|details|next step|felelős|prioritás|határidő|részletek|következő lépés)(?:\*\*)?\s*:\s*(?:\*\*)?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val numberedTitle = Regex("""^\s*\d+[.)]\s+(.+)$""")
    private val boldBulletTitle = Regex("""^\s*[-*]\s+\*\*(.+?)\*\*\s*$""")
    private val bullet = Regex("""^\s*[-*•]\s+(.+)$""")
    private val generated = Regex("""(?i)^\s*\*{0,2}generated:?\*{0,2}:?\s*(.+)$""")
    private val unescape = Regex("""\\([\\_~*#.\-()\[\]!+`>])""")
    private val dateRe = Regex("""(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{1,2}):(\d{2}))?""")
    private val urlRe = Regex("""https?://[^\s<>()\]\[]+""")

    private enum class Section { NONE, EXEC, HIGH, MEDIUM, LOW, NEXT }

    fun parse(text: String, docId: String, title: String, modifiedMs: Long): Summary {
        val lines = text.lines().map { unescape.replace(it, "$1").trimEnd() }
        var section = Section.NONE
        var generatedAt: String? = null
        val exec = StringBuilder()
        val nextSteps = mutableListOf<String>()
        val items = mutableListOf<ActionItem>()
        var cur: Draft? = null

        fun flush() {
            cur?.let { d -> d.toItem()?.let { items += it } }
            cur = null
        }

        for (raw in lines) {
            val line = raw.replace(" ", " ")
            if (line.isBlank()) continue

            if (generatedAt == null) generated.find(line)?.let { generatedAt = clean(it.groupValues[1]) }

            val sectionHit = sectionOf(line)
            if (sectionHit != null) {
                flush(); section = sectionHit; continue
            }
            if (heading.matches(line)) { flush(); section = Section.NONE; continue }

            when (section) {
                Section.EXEC -> {
                    if (exec.isNotEmpty()) exec.append('\n')
                    exec.append(clean(line))
                }
                // csak a felsorolás számít; a záró mondatok ("End of summary.") nem lépések
                Section.NEXT -> bullet.find(line)?.let { nextSteps += clean(it.groupValues[1]) }
                Section.HIGH, Section.MEDIUM, Section.LOW -> {
                    val f = fieldLine.find(line)
                    val t = if (f == null) (boldBulletTitle.find(line) ?: numberedTitle.find(line)) else null
                    when {
                        f != null && cur != null -> cur!!.set(f.groupValues[1], clean(f.groupValues[2]))
                        t != null -> {
                            flush()
                            cur = Draft(clean(t.groupValues[1]), section.toPriority())
                        }
                        cur != null -> cur!!.appendDetail(clean(line))
                    }
                }
                Section.NONE -> Unit
            }
        }
        flush()

        return Summary(
            docId = docId,
            title = title,
            modifiedMs = modifiedMs,
            generatedAt = generatedAt,
            executiveSummary = exec.toString().ifBlank { null },
            nextSteps = nextSteps.filter { it.isNotBlank() },
            items = items.distinctBy { it.id },
        )
    }

    private fun sectionOf(line: String): Section? {
        val h = heading.find(line)?.groupValues?.get(1)
        // sima szöveges exportnál a címsor elveszíti a #-et, ezért a teljes sort is nézzük
        val candidate = clean(h ?: line).lowercase()
        if (h == null && candidate.length > 60) return null
        return when {
            candidate.startsWith("executive summary") || candidate.startsWith("vezetői összefoglaló") -> Section.EXEC
            candidate.startsWith("next steps") || candidate.startsWith("következő lépések") -> Section.NEXT
            candidate.startsWith("high priority") || candidate.startsWith("magas prioritás") -> Section.HIGH
            candidate.startsWith("medium priority") || candidate.startsWith("közepes prioritás") -> Section.MEDIUM
            candidate.startsWith("low priority") || candidate.startsWith("alacsony prioritás") -> Section.LOW
            else -> null
        }
    }

    private fun Section.toPriority() = when (this) {
        Section.HIGH -> Priority.HIGH
        Section.MEDIUM -> Priority.MEDIUM
        else -> Priority.LOW
    }

    fun clean(s: String): String = s
        .replace("**", "")
        .replace(Regex("""\[([^\]]*)]\((https?://[^)]+)\)"""), "$1 $2")
        .replace(Regex("""<(https?://[^>]+)>"""), "$1")
        .replace(Regex("""<([^@\s>]+@[^>\s]+)>"""), "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun parseDue(due: String?): Long? {
        if (due == null) return null
        val m = dateRe.find(due) ?: return null
        val (y, mo, d) = m.destructured
        val date = runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull() ?: return null
        val hh = m.groupValues[4]
        val time = if (hh.isNotEmpty()) LocalTime.of(hh.toInt().coerceIn(0, 23), m.groupValues[5].toInt().coerceIn(0, 59))
        else LocalTime.of(23, 59)
        return ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
    }

    /** Stabil azonosító: a cím zárójeles részei és írásjelei nélkül, mert a Grok futásonként kicsit átfogalmazhat. */
    fun stableId(title: String): String {
        val norm = title.lowercase()
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""^(claim now|claim)\s*:?\s*"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .take(80)
        return "i" + Integer.toHexString(norm.hashCode())
    }

    fun categorize(title: String, details: String?): Category {
        val t = (title + " " + (details ?: "")).lowercase()
        val ttl = title.lowercase()
        fun any(vararg k: String) = k.any { t.contains(it) }
        return when {
            any("sign-in", "sign in", "signed in", "login", "log in", "bejelentkez", "password", "jelszó",
                "security", "2fa", "new device", "new location", "biztonság") && !ttl.startsWith("claim") -> Category.SECURITY
            any("pénzvadász", "payment", "fizetés", "invoice", "számla", "nav ", "nav)", "tax", "adó",
                "hostinger", "domain", "subscription", "előfizetés", "refund", "visszatérítés") &&
                !ttl.startsWith("claim") -> Category.MONEY
            any("claim", "free", "ingyen", "promo", "promó", "trial", "credits", "offer", "premium",
                "win-back", "discount", "kupon", "student pack", "akció") -> Category.PROMO
            else -> Category.TASK
        }
    }

    private class Draft(val title: String, val priority: Priority) {
        var owner: String? = null
        var due: String? = null
        var details: String? = null
        var next: String? = null

        fun set(key: String, value: String) {
            when (key.lowercase()) {
                "owner", "felelős" -> owner = value
                "due", "határidő" -> due = value
                "details", "részletek" -> details = value
                "next step", "következő lépés" -> next = value
            }
        }

        fun appendDetail(v: String) {
            details = if (details == null) v else "$details $v"
        }

        fun toItem(): ActionItem? {
            if (title.isBlank()) return null
            val all = listOfNotNull(title, details, next, due).joinToString(" ")
            val links = urlRe.findAll(all).map { it.value.trimEnd('.', ',', ';') }.distinct().toList()
            return ActionItem(
                id = stableId(title),
                title = title,
                priority = priority,
                category = categorize(title, details),
                owner = owner,
                due = due,
                dueEpochMs = parseDue(due),
                details = details,
                nextStep = next,
                links = links,
            )
        }
    }
}
