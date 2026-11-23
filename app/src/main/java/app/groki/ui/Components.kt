package app.groki.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.groki.model.ActionItem
import app.groki.model.Category
import app.groki.model.Decision
import app.groki.model.ItemState
import app.groki.model.Priority

data class CatStyle(val label: String, val icon: ImageVector)

fun Category.style() = when (this) {
    Category.SECURITY -> CatStyle("Biztonság", Icons.Filled.Security)
    Category.PROMO -> CatStyle("Promó", Icons.Filled.LocalOffer)
    Category.MONEY -> CatStyle("Pénzügy", Icons.Filled.Payments)
    Category.TASK -> CatStyle("Teendő", Icons.Filled.TaskAlt)
}

fun Priority.label() = when (this) {
    Priority.HIGH -> "Sürgős"
    Priority.MEDIUM -> "Közepes"
    Priority.LOW -> "Ráér"
}

@Composable
fun categoryColor(c: Category): Color = when (c) {
    Category.SECURITY -> MaterialTheme.colorScheme.error
    Category.PROMO -> MaterialTheme.colorScheme.tertiary
    Category.MONEY -> MaterialTheme.colorScheme.secondary
    Category.TASK -> MaterialTheme.colorScheme.primary
}

@Composable
fun SectionHeader(title: String, count: Int? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f))
        if (count != null) Text("$count", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun DueText(item: ActionItem, now: Long) {
    val due = item.dueEpochMs
    val text = when {
        due != null -> Format.countdown(due, now)
        !item.due.isNullOrBlank() -> item.due
        else -> return
    }
    val urgent = due != null && due - now < 24 * 3_600_000L
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp),
            tint = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Kártya csúsztatással: jobbra = kész, balra = halasztás holnapig. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeItemCard(
    item: ActionItem,
    now: Long,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        when (it) {
            SwipeToDismissBoxValue.StartToEnd -> { onDone(); true }
            SwipeToDismissBoxValue.EndToStart -> { onSnooze(); true }
            SwipeToDismissBoxValue.Settled -> false
        }
    })
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        backgroundContent = {
            val dir = state.dismissDirection
            val bg by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                }, label = "swipe",
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp)); Text("Kész")
                }
                if (dir == SwipeToDismissBoxValue.EndToStart) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Holnap"); Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.Snooze, null)
                }
            }
        },
    ) {
        ItemCard(item, now, onOpen)
    }
}

@Composable
fun ItemCard(item: ActionItem, now: Long, onOpen: () -> Unit) {
    val accent = categoryColor(item.category)
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.category == Category.SECURITY) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.14f)) {
                Icon(item.category.style().icon, item.category.style().label,
                    tint = accent, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = item.nextStep ?: item.details
                if (!sub.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityDot(item.priority)
                    Spacer(Modifier.width(10.dp))
                    DueText(item, now)
                }
            }
        }
    }
}

@Composable
fun PriorityDot(p: Priority) {
    val c = when (p) {
        Priority.HIGH -> MaterialTheme.colorScheme.error
        Priority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        Priority.LOW -> MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(c))
        Spacer(Modifier.width(4.dp))
        Text(p.label(), style = MaterialTheme.typography.labelMedium, color = c)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Részletes nézet alsó lapon, minden művelettel egy helyen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(
    item: ActionItem,
    state: ItemState,
    now: Long,
    onDismissRequest: () -> Unit,
    onDone: () -> Unit,
    onSnooze: (hours: Long) -> Unit,
    onDismissItem: () -> Unit,
    onDecide: (Decision) -> Unit,
    onReopen: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uri = LocalUriHandler.current
    val clip = LocalClipboardManager.current
    var snoozeMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.category.style().icon, null, tint = categoryColor(item.category))
                Spacer(Modifier.width(8.dp))
                Text(item.category.style().label, style = MaterialTheme.typography.labelLarge,
                    color = categoryColor(item.category))
                Spacer(Modifier.width(12.dp))
                PriorityDot(item.priority)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            DueText(item, now)
            if (item.dueEpochMs != null) Text("Határidő: ${Format.date(item.dueEpochMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!item.nextStep.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        Text("Következő lépés", style = MaterialTheme.typography.labelLarge)
                        Text(item.nextStep, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (!item.details.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Részletek", style = MaterialTheme.typography.labelLarge)
                Text(item.details, style = MaterialTheme.typography.bodyMedium)
            }
            if (!item.owner.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Felelős: ${item.owner}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.links.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.links.forEach { link ->
                        AssistChip(
                            onClick = { runCatching { uri.openUri(link) } },
                            label = { Text(link.removePrefix("https://").removePrefix("www.").take(32), maxLines = 1) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
                                Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            if (item.category == Category.PROMO) {
                val d = state.decision
                Text(
                    when (d) { Decision.WANT -> "Döntés: kérem"; Decision.SKIP -> "Döntés: nem kérem"; null -> "Döntés kell" },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onDecide(Decision.WANT) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ThumbUp, null); Spacer(Modifier.width(6.dp)); Text("Kérem")
                    }
                    OutlinedButton(onClick = { onDecide(Decision.SKIP) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ThumbDown, null); Spacer(Modifier.width(6.dp)); Text("Nem kérem")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.status == app.groki.model.Status.DONE || state.status == app.groki.model.Status.DISMISSED) {
                    Button(onClick = onReopen, modifier = Modifier.weight(1f)) { Text("Újranyitás") }
                } else {
                    Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text("Kész")
                    }
                    Box(Modifier.weight(1f)) {
                        FilledTonalButton(onClick = { snoozeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Snooze, null); Spacer(Modifier.width(6.dp)); Text("Halaszt")
                        }
                        DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                            listOf(3L to "3 órára", 24L to "Holnapig", 72L to "3 napra", 168L to "1 hétre").forEach { (h, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = { snoozeMenu = false; onSnooze(h) })
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onDismissItem, label = { Text("Elvetés") },
                    leadingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(AssistChipDefaults.IconSize)) })
                AssistChip(
                    onClick = {
                        clip.setText(AnnotatedString(listOfNotNull(item.title, item.nextStep, item.details)
                            .joinToString("\n")))
                    },
                    label = { Text("Másolás") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
