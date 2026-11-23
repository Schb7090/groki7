package app.groki.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.groki.R
import app.groki.data.Repository
import app.groki.model.ActionItem
import app.groki.model.Category
import app.groki.model.Priority
import app.groki.ui.MainActivity

/**
 * Értesítési elv: csak ami tényleg sürgős, és minden elemről legfeljebb egyszer.
 * - Biztonság: minden új biztonsági tétel.
 * - Határidő: nyitott elem, amelynek a határideje 24 órán belül van.
 * Egy szinkron legfeljebb egy (csoportosított) értesítést küld csatornánként.
 */
object Notifier {
    private const val CH_SECURITY = "security"
    private const val CH_DEADLINE = "deadline"
    private const val CH_ACCOUNT = "account"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_SECURITY, "Biztonsági riasztások", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_DEADLINE, "Lejáró határidők", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_ACCOUNT, "Fiók és bejelentkezés", NotificationManager.IMPORTANCE_LOW),
        ))
    }

    private fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, tab: Int): PendingIntent = PendingIntent.getActivity(
        context, tab,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    suspend fun notifyImportant(context: Context, repo: Repository) {
        val snap = repo.snapshot.value
        val now = System.currentTimeMillis()
        val open = snap.openItems(now).filter { it.id !in snap.notifiedIds }
        val security = open.filter { it.category == Category.SECURITY }
        val deadline = open.filter {
            it.category != Category.SECURITY && it.dueEpochMs != null &&
                it.dueEpochMs - now in 0..24 * 3_600_000L && it.priority != Priority.LOW
        }
        if (!canPost(context)) return
        post(context, CH_SECURITY, 1001, "Biztonsági ellenőrzés kell", security, tab = 0)
        post(context, CH_DEADLINE, 1002, "24 órán belül lejár", deadline, tab = 0)
        repo.markNotified((security + deadline).map { it.id })
    }

    @Suppress("MissingPermission")
    private fun post(context: Context, channel: String, id: Int, title: String, items: List<ActionItem>, tab: Int) {
        if (items.isEmpty()) return
        val style = NotificationCompat.InboxStyle()
        items.take(6).forEach { style.addLine(it.title) }
        if (items.size > 6) style.setSummaryText("+${items.size - 6} további")
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (items.size == 1) title else "$title (${items.size})")
            .setContentText(items.first().title)
            .setStyle(style)
            .setContentIntent(openApp(context, tab))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    @Suppress("MissingPermission")
    fun notifySignIn(context: Context) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_ACCOUNT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Groki: jelentkezz be újra")
            .setContentText("A Google hozzáférés megújításához nyisd meg az appot.")
            .setContentIntent(openApp(context, 0))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1003, n)
    }
}
