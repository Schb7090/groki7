package app.groki.parse

import app.groki.model.Category
import app.groki.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Szintetikus minta a Grok Task Extractor kimenetének szerkezetével (valódi adat nélkül). */
class SummaryParserTest {
    private val markdown = """
        # Action Items and Next Steps Summary

        **Generated:** 2026-11-23 16:35 CET (Europe/Budapest)
        **Owner Context:** Primary owner is Example User.

        ## Executive Summary

        Latest review confirms no new high-priority correspondence. Example\_Service domain renewal pending.

        ## Extracted Action Items

        ### High Priority (due soon or time-sensitive)

        1.  **Verify Example Git sign-in from new location**

          - Owner: Example User
          - Suggested priority: High (security)
          - Due: ASAP
          - Details: Notification of sign-in from new location.
          - Next step: Confirm whether the login was authorized.

        1.  **CLAIM NOW: ExampleCompute 30M tokens/month free (tight window)**

          - Owner: Example User
          - Due: 2026-11-24 09:00 CET
          - Details: Free tier with email confirmation. Official: <https://example.com/free/> .
          - Next step: Claim if desired before cutoff; do not add payment.

        1.  **Resolve ExampleRide outstanding payment or cancel subscription**

          - Owner: Example User
          - Due: 2026-11-30
          - Details: Renewal payment failed.

        ### Medium Priority

        1.  **Decide on ExampleIDE Pro trial (first month free)**

          - Details: Requires card and switches to paid after.
          - Next step: Provide explicit yes/no.

        ### Low Priority / Monitoring

        1.  **Review/clean task tracker pages**

          - Details: Mostly sample tasks.

        ## Next Steps Summary

          - Immediate: verify security logins.
          - Time-sensitive decisions: ExampleCompute by Nov 24 09:00.

        **End of summary.**
    """.trimIndent()

    @Test fun parsesSectionsAndItems() {
        val s = SummaryParser.parse(markdown, "doc1", "Action_Items_Summary", 0)
        assertEquals("2026-11-23 16:35 CET (Europe/Budapest)", s.generatedAt)
        assertTrue(s.executiveSummary!!.contains("Example_Service"))
        assertEquals(5, s.items.size)
        assertEquals(3, s.items.count { it.priority == Priority.HIGH })
        assertEquals(1, s.items.count { it.priority == Priority.MEDIUM })
        assertEquals(1, s.items.count { it.priority == Priority.LOW })
        assertEquals(2, s.nextSteps.size)
    }

    @Test fun categorizesItems() {
        val items = SummaryParser.parse(markdown, "d", "t", 0).items.associateBy { it.title }
        assertEquals(Category.SECURITY, items["Verify Example Git sign-in from new location"]!!.category)
        assertEquals(Category.PROMO, items["CLAIM NOW: ExampleCompute 30M tokens/month free (tight window)"]!!.category)
        assertEquals(Category.MONEY, items["Resolve ExampleRide outstanding payment or cancel subscription"]!!.category)
        assertEquals(Category.PROMO, items["Decide on ExampleIDE Pro trial (first month free)"]!!.category)
        assertEquals(Category.TASK, items["Review/clean task tracker pages"]!!.category)
    }

    @Test fun extractsFieldsDueAndLinks() {
        val promo = SummaryParser.parse(markdown, "d", "t", 0).items.first { it.title.startsWith("CLAIM") }
        assertEquals("2026-11-24 09:00 CET", promo.due)
        // 2026-11-24 09:00 Budapest = 08:00 UTC
        assertEquals(java.time.Instant.parse("2026-11-24T08:00:00Z").toEpochMilli(), promo.dueEpochMs)
        assertEquals(listOf("https://example.com/free/"), promo.links)
        assertEquals("Claim if desired before cutoff; do not add payment.", promo.nextStep)
    }

    @Test fun asapHasNoDueTimestamp() {
        val sec = SummaryParser.parse(markdown, "d", "t", 0).items.first { it.category == Category.SECURITY }
        assertEquals("ASAP", sec.due)
        assertNull(sec.dueEpochMs)
    }

    @Test fun stableIdSurvivesRewording() {
        assertEquals(
            SummaryParser.stableId("CLAIM NOW: ExampleCompute 30M tokens/month free (tight window)"),
            SummaryParser.stableId("ExampleCompute 30M tokens/month free (closes tomorrow)"),
        )
    }

    @Test fun plainTextExportAlsoWorks() {
        val plain = markdown.replace("**", "").replace(Regex("(?m)^#+ "), "")
        val s = SummaryParser.parse(plain, "d", "t", 0)
        assertEquals(5, s.items.size)
        assertNotNull(s.executiveSummary)
    }

    @Test fun mailAutomationName() {
        val snippet = "Automation · Task Extractor ͏ ͏ ͏ Task Extractor is ready New action items summary ready"
        assertEquals("Task Extractor", MailParser.automationName(snippet, "x"))
        assertEquals("Task Extractor is ready New action items summary ready", MailParser.cleanSnippet(snippet))
        assertEquals("Pénzvadász", MailParser.automationName("", "Pénzvadász Notion updates"))
    }
}
