package app.groki.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.groki.parse.DocConverters
import java.io.IOException

/**
 * Olvasás a felhasználó által kiválasztott mappából (Storage Access Framework).
 * Nem kell OAuth: a fájlokat a telefonon lévő Google Drive app adja át.
 * A Grok minden futáshoz külön almappát hoz létre, ezért a legutóbbi néhány almappába is belenézünk.
 */
class FolderSource(private val context: Context, private val treeUri: Uri) {
    private val resolver = context.contentResolver
    private val nameRe = Regex("""(?i)action[ _]+(items|summary)""")

    data class Doc(val documentId: String, val name: String, val mime: String, val modifiedMs: Long, val isDir: Boolean)

    private fun children(parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED)
        val out = mutableListOf<Doc>()
        resolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(2).orEmpty()
                out += Doc(c.getString(0), c.getString(1).orEmpty(), mime,
                    if (c.isNull(3)) 0L else c.getLong(3), mime == Document.MIME_TYPE_DIR)
            }
        } ?: throw IOException("A kiválasztott mappa nem olvasható. Válaszd ki újra.")
        return out
    }

    private fun isSummary(d: Doc) = !d.isDir && nameRe.containsMatchIn(d.name) &&
        d.name.contains("summary", true) && !d.mime.contains("json")

    /** A legutóbb módosított összefoglaló dokumentum, legfeljebb 3 mappaszint mélyen. */
    fun latestSummary(): Doc? {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val found = mutableListOf<Doc>()
        fun walk(id: String, depth: Int) {
            val kids = children(id)
            found += kids.filter(::isSummary)
            if (depth >= 3) return
            kids.filter { it.isDir }
                .sortedWith(compareByDescending<Doc> { it.modifiedMs }.thenByDescending { it.name })
                .take(6)
                .forEach { walk(it.documentId, depth + 1) }
        }
        walk(rootId, 0)
        return found.maxWithOrNull(compareBy<Doc> { it.modifiedMs }.thenBy { it.name })
    }

    /** A dokumentum szövege; Google Docs esetén a Drive által kínált konvertált formátumból. */
    fun readText(doc: Doc): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.documentId)
        if (doc.mime.startsWith("text/")) {
            return resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IOException("Nem sikerült megnyitni: ${doc.name}")
        }
        val types = resolver.getStreamTypes(uri, "*/*").orEmpty().toList()
        val docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val pick = listOf("text/markdown", "text/x-markdown", "text/plain", "text/html", docx)
            .firstOrNull { it in types }
            ?: if (types.isEmpty()) null
            else throw IOException("A Drive ezt a fájlt csak ${types.joinToString()} formában adja ki, amit az app nem tud olvasni.")
        if (pick == null) {
            // nem virtuális fájl: közvetlenül olvasható
            return resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IOException("Nem sikerült megnyitni: ${doc.name}")
        }
        val afd = resolver.openTypedAssetFileDescriptor(uri, pick, null)
            ?: throw IOException("Nem sikerült konvertálni: ${doc.name}")
        return afd.createInputStream().use { s ->
            when (pick) {
                docx -> DocConverters.docxToMarkdown(s)
                "text/html" -> DocConverters.htmlToMarkdown(s.readBytes().toString(Charsets.UTF_8))
                else -> s.readBytes().toString(Charsets.UTF_8)
            }
        }
    }
}
