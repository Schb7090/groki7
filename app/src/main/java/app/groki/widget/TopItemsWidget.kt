package app.groki.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.groki.data.Graph
import app.groki.model.Category
import app.groki.model.Priority
import app.groki.ui.MainActivity

/** Kezdőképernyő-widget: a 4 legfontosabb nyitott teendő, koppintásra megnyitja az appot. */
class TopItemsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Graph.init(context)
        val snap = Graph.repo.snapshot.value
        val items = snap.openItems().filter { it.category == Category.SECURITY || it.priority == Priority.HIGH }
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(20.dp)
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        text = if (items.isEmpty()) "Groki · minden rendben" else "Groki · ${items.size} sürgős",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = GlanceTheme.colors.onSurface,
                        ),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    if (snap.summary == null) {
                        Text(
                            "Nyisd meg az appot a bejelentkezéshez.",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        )
                    }
                    items.take(4).forEach {
                        val mark = if (it.category == Category.SECURITY) "🔒 " else "• "
                        Text(
                            text = mark + it.title,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                            modifier = GlanceModifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

class TopItemsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopItemsWidget()
}
