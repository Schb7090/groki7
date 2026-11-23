package app.groki.parse

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * A Drive app a Google Docs fájlokat csak konvertálva adja ki (rendszerint docx, html vagy pdf).
 * Ezek a konverterek a [SummaryParser] által értett egyszerű markdownra alakítanak:
 * címsor -> "#", számozott lista 0. szint -> "1. ", alsóbb szint / felsorolás -> "- ".
 */
object DocConverters {

    fun docxToMarkdown(input: InputStream): String {
        val xml = ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "word/document.xml" }
                ?: return ""
            zip.readBytes().toString(Charsets.UTF_8)
        }
        return docxXmlToMarkdown(xml)
    }

    private val paragraph = Regex("""<w:p[ >].*?</w:p>""", RegexOption.DOT_MATCHES_ALL)
    private val styleRe = Regex("""<w:pStyle w:val="([^"]+)"""")
    private val ilvlRe = Regex("""<w:ilvl w:val="(\d+)"""")
    private val textRe = Regex("""<w:t(?:\s[^>]*)?>(.*?)</w:t>""", RegexOption.DOT_MATCHES_ALL)

    fun docxXmlToMarkdown(xml: String): String = paragraph.findAll(xml).mapNotNull { m ->
        val p = m.value
        val text = textRe.findAll(p).joinToString("") { xmlUnescape(it.groupValues[1]) }.trim()
        if (text.isEmpty()) return@mapNotNull null
        val style = styleRe.find(p)?.groupValues?.get(1).orEmpty()
        val heading = Regex("""(?i)heading\s*(\d)""").find(style)?.groupValues?.get(1)?.toIntOrNull()
        when {
            style.equals("Title", true) -> "# $text"
            heading != null -> "#".repeat(heading.coerceIn(1, 6)) + " " + text
            p.contains("<w:numPr>") -> {
                val lvl = ilvlRe.find(p)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (lvl == 0) "1. $text" else "  - $text"
            }
            else -> text
        }
    }.joinToString("\n")

    private val tagRe = Regex("""<(/?)([a-zA-Z0-9]+)[^>]*>""")

    fun htmlToMarkdown(html: String): String {
        val body = html.replace(Regex("""(?is)<(script|style|head)[^>]*>.*?</\1>"""), "")
        val out = StringBuilder()
        val lists = ArrayDeque<String>()
        var last = 0
        for (m in tagRe.findAll(body)) {
            out.append(htmlUnescape(body.substring(last, m.range.first)))
            last = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            when (val tag = m.groupValues[2].lowercase()) {
                "ol", "ul" -> if (closing) lists.removeLastOrNull() else lists.addLast(tag)
                "li" -> if (!closing) {
                    out.append('\n')
                    val nested = lists.size > 1
                    out.append(if (lists.lastOrNull() == "ol" && !nested) "1. " else "  - ")
                }
                "h1", "h2", "h3", "h4", "h5", "h6" ->
                    if (!closing) out.append("\n" + "#".repeat(tag[1].digitToInt()) + " ") else out.append('\n')
                "p", "div", "br", "tr" -> out.append('\n')
                "b", "strong" -> out.append("**")
            }
        }
        out.append(htmlUnescape(body.substring(last)))
        return out.toString().lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    private fun xmlUnescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun htmlUnescape(s: String) = xmlUnescape(s).replace("&nbsp;", " ").replace("&#39;", "'")
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toInt().toChar().toString() }
}
