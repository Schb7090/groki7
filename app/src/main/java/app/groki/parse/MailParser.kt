package app.groki.parse

object MailParser {
    private val automationRe = Regex("""Automation\s*·\s*([^͏\n]+?)(?:\s{2,}|͏|$)""")

    /** A Grok értesítők törzse így kezdődik: "Automation · Task Extractor ͏ ͏ ...". */
    fun automationName(snippet: String, subject: String): String {
        automationRe.find(snippet)?.let { return it.groupValues[1].trim() }
        val s = subject.lowercase()
        return when {
            "pénzvadász" in s -> "Pénzvadász"
            "promo" in s || "promó" in s -> "Promó-figyelő"
            "action" in s -> "Task Extractor"
            "login" in s || "bejelentkez" in s -> "Fiók"
            else -> "Grok"
        }
    }

    fun cleanSnippet(snippet: String): String {
        val body = if ('\u034f' in snippet) snippet.substringAfterLast('\u034f') else snippet
        return body.replace(Regex("""\s+"""), " ").trim()
    }
}
