package app.groki.data

import app.groki.model.GrokMail
import app.groki.parse.MailParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Apps Script híd: a felhasználó saját Google-fiókjában futó webapp (assets/bridge.gs),
 * amely a legfrissebb összefoglaló szövegét és a Grok-leveleket JSON-ként adja vissza.
 */
class BridgeSource(private val url: String) {
    @Serializable
    data class Payload(val summary: BridgeSummary? = null, val mails: List<BridgeMail> = emptyList(), val error: String? = null)

    @Serializable
    data class BridgeSummary(val id: String, val name: String, val modifiedMs: Long, val text: String)

    @Serializable
    data class BridgeMail(
        val id: String, val threadId: String, val subject: String,
        val snippet: String, val dateMs: Long, val unread: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): Payload = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        val body = try {
            if (c.responseCode !in 200..299) throw IOException("A híd hibát adott (HTTP ${c.responseCode}).")
            c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
        val p = try {
            json.decodeFromString(Payload.serializer(), body)
        } catch (e: Exception) {
            throw IOException("A híd nem JSON-t adott vissza. Ellenőrizd, hogy a webappot „Bárki” hozzáféréssel telepítetted, és a /exec végű URL-t másoltad be.")
        }
        p.error?.let { throw IOException("A híd hibát jelzett: $it") }
        p
    }

    fun toMails(p: Payload): List<GrokMail> = p.mails.map {
        GrokMail(
            id = it.id, threadId = it.threadId, subject = it.subject,
            automation = MailParser.automationName(it.snippet, it.subject),
            snippet = MailParser.cleanSnippet(it.snippet).take(300),
            dateMs = it.dateMs, unread = it.unread,
        )
    }.sortedByDescending { it.dateMs }
}
