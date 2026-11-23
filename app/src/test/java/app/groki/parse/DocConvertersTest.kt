package app.groki.parse

import app.groki.model.Category
import app.groki.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A Drive app által adott docx/html export -> parser láncot ellenőrzi szintetikus adatokkal. */
class DocConvertersTest {
    private fun p(text: String, style: String? = null, list: Int? = null): String {
        val ppr = buildString {
            if (style != null || list != null) {
                append("<w:pPr>")
                if (style != null) append("""<w:pStyle w:val="$style"/>""")
                if (list != null) append("""<w:numPr><w:ilvl w:val="$list"/><w:numId w:val="1"/></w:numPr>""")
                append("</w:pPr>")
            }
        }
        return """<w:p>$ppr<w:r><w:t xml:space="preserve">$text</w:t></w:r></w:p>"""
    }

    private val docXml = "<w:document><w:body>" +
        p("Action Items and Next Steps Summary", "Title") +
        p("Generated: 2026-11-23 16:35 CET") +
        p("Executive Summary", "Heading2") +
        p("Nothing new &amp; urgent.") +
        p("High Priority (due soon)", "Heading3") +
        p("Verify Example sign-in from new location", list = 0) +
        p("Owner: Example User", list = 1) +
        p("Due: ASAP", list = 1) +
        p("CLAIM NOW: Example 10,000 free credits", list = 0) +
        p("Due: 2026-11-30", list = 1) +
        p("Next step: Create account and claim.", list = 1) +
        p("Medium Priority", "Heading3") +
        p("Decide on Example Pro trial", list = 0) +
        p("Next Steps Summary", "Heading2") +
        p("Immediate: verify logins.", list = 0) +
        "</w:body></w:document>"

    private fun docxBytes(xml: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write("<x/>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("word/document.xml")); z.write(xml.toByteArray()); z.closeEntry()
        }
        return bos.toByteArray()
    }

    @Test fun docxRoundTrip() {
        val md = DocConverters.docxToMarkdown(ByteArrayInputStream(docxBytes(docXml)))
        val s = SummaryParser.parse(md, "d", "t", 0)
        assertEquals("Nothing new & urgent.", s.executiveSummary)
        assertEquals(3, s.items.size)
        val sec = s.items.first()
        assertEquals(Category.SECURITY, sec.category)
        assertEquals(Priority.HIGH, sec.priority)
        assertEquals("Example User", sec.owner)
        val promo = s.items[1]
        assertEquals(Category.PROMO, promo.category)
        assertEquals("Create account and claim.", promo.nextStep)
        assertTrue(promo.dueEpochMs != null)
        assertEquals(Priority.MEDIUM, s.items[2].priority)
        assertEquals(listOf("Immediate: verify logins."), s.nextSteps)
    }

    @Test fun htmlRoundTrip() {
        val html = """
            <html><head><style>p{}</style></head><body>
            <h2>Executive Summary</h2><p>All quiet.</p>
            <h3>High Priority</h3>
            <ol><li><b>Resolve Example payment</b><ul><li>Due: 2026-12-08</li><li>Details: Invoice unpaid.</li></ul></li>
            <li><b>Verify Example password reset</b></li></ol>
            <h3>Low Priority</h3><ol><li>Review tracker pages</li></ol>
            </body></html>
        """.trimIndent()
        val s = SummaryParser.parse(DocConverters.htmlToMarkdown(html), "d", "t", 0)
        assertEquals("All quiet.", s.executiveSummary)
        assertEquals(3, s.items.size)
        assertEquals(Category.MONEY, s.items[0].category)
        assertEquals("Invoice unpaid.", s.items[0].details)
        assertEquals(Category.SECURITY, s.items[1].category)
        assertEquals(Priority.LOW, s.items[2].priority)
    }
}
