package app.groki.data

import app.groki.model.GrokMail
import app.groki.parse.MailParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class HttpException(val code: Int, message: String) : IOException(message)

data class DriveFile(val id: String, val name: String, val modifiedMs: Long)

/** Vékony REST kliens a Gmail és Drive API-hoz (külső HTTP könyvtár nélkül). */
class GoogleApi(private val token: String) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                throw HttpException(code, "HTTP $code: ${err.take(300)}")
            }
            c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun obj(s: String): JsonObject = json.parseToJsonElement(s).jsonObject

    /** A Grok értesítők az elmúlt [days] napból. A már ismert levelek részleteit nem kéri le újra. */
    suspend fun grokMails(known: Map<String, GrokMail>, days: Int = 14, max: Int = 60): List<GrokMail> {
        val q = enc("from:noreply@x.ai newer_than:${days}d")
        val list = obj(get("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=$q&maxResults=$max"))
        val ids = list["messages"]?.jsonArray?.map { it.jsonObject["id"]!!.jsonPrimitive.content }.orEmpty()
        val gate = Semaphore(6)
        return coroutineScope {
            ids.map { id ->
                async {
                    gate.withPermit {
                        val m = obj(
                            get("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id" +
                                "?format=metadata&metadataHeaders=Subject")
                        )
                        val labels = m["labelIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                        val prev = known[id]
                        if (prev != null) return@withPermit prev.copy(unread = "UNREAD" in labels)
                        val headers = m["payload"]?.jsonObject?.get("headers")?.jsonArray.orEmpty()
                        val subject = headers.firstOrNull {
                            it.jsonObject["name"]?.jsonPrimitive?.content.equals("Subject", true)
                        }?.jsonObject?.get("value")?.jsonPrimitive?.content ?: "(tárgy nélkül)"
                        val snippet = m["snippet"]?.jsonPrimitive?.content.orEmpty()
                        GrokMail(
                            id = id,
                            threadId = m["threadId"]?.jsonPrimitive?.content ?: id,
                            subject = subject,
                            automation = MailParser.automationName(snippet, subject),
                            snippet = MailParser.cleanSnippet(snippet),
                            dateMs = m["internalDate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                            unread = "UNREAD" in labels,
                        )
                    }
                }
            }.awaitAll().sortedByDescending { it.dateMs }
        }
    }

    /** A legutóbb módosított "Action Items ... Summary" Google Doc a Drive-on. */
    suspend fun latestSummaryDoc(): DriveFile? {
        val q = enc("name contains 'Action' and mimeType = 'application/vnd.google-apps.document' and trashed = false")
        val res = obj(
            get("https://www.googleapis.com/drive/v3/files?q=$q&orderBy=${enc("modifiedTime desc")}" +
                "&pageSize=25&fields=${enc("files(id,name,modifiedTime)")}")
        )
        val nameRe = Regex("""(?i)action[ _]+(items|summary)""")
        return res["files"]?.jsonArray?.asSequence()?.map { it.jsonObject }?.map {
            DriveFile(
                id = it["id"]!!.jsonPrimitive.content,
                name = it["name"]!!.jsonPrimitive.content,
                modifiedMs = Instant.parse(it["modifiedTime"]!!.jsonPrimitive.content).toEpochMilli(),
            )
        }?.firstOrNull { nameRe.containsMatchIn(it.name) && it.name.contains("summary", ignoreCase = true) }
    }

    suspend fun exportDoc(id: String): String = try {
        get("https://www.googleapis.com/drive/v3/files/$id/export?mimeType=${enc("text/markdown")}")
    } catch (e: HttpException) {
        if (e.code == 401) throw e
        get("https://www.googleapis.com/drive/v3/files/$id/export?mimeType=${enc("text/plain")}")
    }
}
