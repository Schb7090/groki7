package app.groki.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.groki.model.ActionItem
import app.groki.model.Category
import app.groki.model.Decision
import app.groki.model.Priority
import app.groki.model.Snapshot

class ItemActions(
    val open: (ActionItem) -> Unit,
    val done: (ActionItem) -> Unit,
    val snoozeTomorrow: (ActionItem) -> Unit,
    val decide: (ActionItem, Decision) -> Unit,
)

private val listPadding = PaddingValues(bottom = 96.dp)

// ---------------------------------------------------------------- Ma
@Composable
fun TodayScreen(snap: Snapshot, now: Long, actions: ItemActions, goTo: (Int) -> Unit) {
    val open = snap.openItems(now)
    val security = open.filter { it.category == Category.SECURITY }
    val soon = open.filter {
        it.category != Category.SECURITY && it.dueEpochMs != null && it.dueEpochMs - now < 48 * 3_600_000L
    }
    val urgent = open.filter { it.priority == Priority.HIGH && it !in security && it !in soon }
    val promosOpen = open.count { it.category == Category.PROMO && snap.stateOf(it.id).decision == null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Biztonság", security.size, MaterialTheme.colorScheme.error, Modifier.weight(1f)) { goTo(1) }
                StatTile("Lejár 48 órán belül", soon.size, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) { goTo(1) }
                StatTile("Döntésre vár", promosOpen, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { goTo(2) }
            }
        }
        snap.summary?.executiveSummary?.let { exec ->
            item { ExecCard(exec, snap.summary.generatedAt) }
        }
        if (open.isEmpty() && snap.summary != null) {
            item { EmptyState(Icons.Filled.CheckCircle, "Minden kész", "Nincs nyitott teendő. Húzd le a frissítéshez.") }
        }
        section("Biztonsági ellenőrzés", security, now, actions)
        section("Hamarosan lejár", soon, now, actions)
        section("Sürgős", urgent.take(6), now, actions)
        if (urgent.size > 6) item {
            TextButton(onClick = { goTo(1) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Összes sürgős (${urgent.size})")
            }
        }
        if (snap.summary?.nextSteps?.isNotEmpty() == true) {
            item { SectionHeader("A Grok javaslata a következő lépésekre") }
            items(snap.summary.nextSteps) {
                Text("• $it", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String, list: List<ActionItem>, now: Long, a: ItemActions,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionHeader(title, list.size) }
    items(list, key = { "$title-${it.id}" }) { item ->
        SwipeItemCard(item, now, onOpen = { a.open(item) }, onDone = { a.done(item) }, onSnooze = { a.snoozeTomorrow(item) })
    }
}

@Composable
private fun ExecCard(text: String, generated: String?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SmartToy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grok összefoglaló", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (generated != null) Text(generated, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Teendők
@Composable
fun ItemsScreen(snap: Snapshot, now: Long, actions: ItemActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    val base = if (showClosed) snap.closedItems() else snap.openItems(now)
    val list = base.filter { cat == null || it.category.name == cat }.filter {
        query.isBlank() || listOfNotNull(it.title, it.details, it.nextStep).any { s -> s.contains(query, true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Keresés a teendők között") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("Mind ${base.size}") }) }
                items(Category.entries) { c ->
                    val n = base.count { it.category == c }
                    FilterChip(selected = cat == c.name, onClick = { cat = if (cat == c.name) null else c.name },
                        label = { Text("${c.style().label} $n") },
                        leadingIcon = { Icon(c.style().icon, null, Modifier.size(16.dp)) })
                }
                item { FilterChip(selected = showClosed, onClick = { showClosed = !showClosed }, label = { Text("Lezártak") }) }
            }
        }
        if (list.isEmpty()) item {
            EmptyState(Icons.Filled.CheckCircle, if (showClosed) "Nincs lezárt elem" else "Nincs találat",
                if (query.isNotBlank()) "Próbálj más keresőszót." else "Ebben a nézetben nincs teendő.")
        }
        Priority.entries.forEach { p ->
            val group = list.filter { it.priority == p }
            if (group.isNotEmpty()) {
                item(key = "p-$p") { SectionHeader(p.label(), group.size) }
                items(group, key = { it.id }) { item ->
                    if (showClosed) Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        ItemCard(item, now) { actions.open(item) }
                    } else SwipeItemCard(item, now, { actions.open(item) }, { actions.done(item) }, { actions.snoozeTomorrow(item) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Promók
@Composable
fun PromosScreen(snap: Snapshot, now: Long, actions: ItemActions) {
    val promos = snap.openItems(now).filter { it.category == Category.PROMO }
        .sortedWith(compareBy({ snap.stateOf(it.id).decision != null }, { it.dueEpochMs ?: Long.MAX_VALUE }))
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        if (promos.isEmpty()) item {
            EmptyState(Icons.Filled.LocalOffer, "Nincs nyitott ajánlat", "A Promó-figyelő új találatai itt jelennek meg.")
        }
        items(promos, key = { it.id }) { p ->
            val decision = snap.stateOf(p.id).decision
            Card(
                onClick = { actions.open(p) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(4.dp))
                    DueText(p, now)
                    p.details?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (p.details?.contains("card", true) == true || p.details?.contains("kártya", true) == true) {
                        Text("⚠ Bankkártyát kérhet – fizetés előtt állj meg.", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (decision == Decision.WANT) {
                            Text("✓ Kérem", color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterVertically))
                            p.links.firstOrNull()?.let { link ->
                                OutlinedButton(onClick = { runCatching { uri.openUri(link) } }) { Text("Megnyitás") }
                            }
                            TextButton(onClick = { actions.done(p) }) { Text("Elintézve") }
                        } else {
                            Button(onClick = {
                                actions.decide(p, Decision.WANT)
                                p.links.firstOrNull()?.let { runCatching { uri.openUri(it) } }
                            }) { Text("Kérem") }
                            OutlinedButton(onClick = { actions.decide(p, Decision.SKIP) }) { Text("Nem kérem") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Levelek
@Composable
fun MailScreen(snap: Snapshot) {
    var auto by rememberSaveable { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    val groups = snap.mails.groupBy { it.automation }.toList().sortedByDescending { it.second.size }
    val list = snap.mails.filter { auto == null || it.automation == auto }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        item {
            LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = auto == null, onClick = { auto = null }, label = { Text("Mind ${snap.mails.size}") }) }
                items(groups) { (name, ms) ->
                    FilterChip(selected = auto == name, onClick = { auto = if (auto == name) null else name },
                        label = { Text("$name ${ms.size}") })
                }
            }
        }
        if (list.isEmpty()) item {
            EmptyState(Icons.Filled.MarkEmailUnread, "Nincs Grok-levél", "Az elmúlt 14 nap noreply@x.ai értesítői itt jelennek meg.")
        }
        items(list, key = { it.id }) { m ->
            ListItem(
                modifier = Modifier.clickable {
                    runCatching { uri.openUri("https://mail.google.com/mail/u/0/#all/${m.threadId}") }
                },
                overlineContent = { Text("${m.automation} · ${Format.ago(m.dateMs)}") },
                headlineContent = {
                    Text(m.subject, fontWeight = if (m.unread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(m.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    Icon(if (m.unread) Icons.Filled.MarkEmailUnread else Icons.Filled.SmartToy, null,
                        tint = if (m.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
        }
    }
}

// ---------------------------------------------------------------- Bejelentkezés
@Composable
fun SignInScreen(busy: Boolean, error: String?, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.SmartToy, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Groki", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "A Grok automatizmusaid (Task Extractor, Promó-figyelő, Pénzvadász) eredményei egy helyen: " +
                "teendők, lejáró ajánlatok és biztonsági riasztások.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Csak olvasási hozzáférés (Gmail, Drive). Az adatok a telefonon maradnak.",
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Kapcsolódás…" else "Bejelentkezés Google-fiókkal")
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
