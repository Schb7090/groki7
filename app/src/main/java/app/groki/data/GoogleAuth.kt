package app.groki.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Csak olvasási jogosultság: Gmail (a Grok értesítők) és Drive (az Action Items fájlok).
 * Az app soha nem küld levelet és nem módosít Drive-fájlt.
 */
object GoogleAuth {
    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/gmail.readonly"),
        Scope("https://www.googleapis.com/auth/drive.readonly"),
    )

    sealed interface Result {
        data class Token(val accessToken: String) : Result
        data class NeedsConsent(val intent: PendingIntent) : Result
        data class Failed(val message: String) : Result
    }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(scopes).build()

    suspend fun authorize(context: Context): Result = try {
        val r = Identity.getAuthorizationClient(context).authorize(request()).await()
        val pi = r.pendingIntent
        when {
            r.hasResolution() && pi != null -> Result.NeedsConsent(pi)
            r.accessToken != null -> Result.Token(r.accessToken!!)
            else -> Result.Failed("Nem érkezett hozzáférési token.")
        }
    } catch (e: Exception) {
        Result.Failed(e.message ?: e.javaClass.simpleName)
    }

    fun tokenFromConsent(context: Context, data: Intent?): Result = try {
        val r = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        r.accessToken?.let { Result.Token(it) } ?: Result.Failed("A hozzájárulás nem adott tokent.")
    } catch (e: Exception) {
        Result.Failed(e.message ?: e.javaClass.simpleName)
    }
}
