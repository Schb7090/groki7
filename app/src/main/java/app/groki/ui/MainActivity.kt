package app.groki.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.groki.model.ActionItem
import app.groki.model.Category
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTab = intent.getIntExtra(EXTRA_TAB, 0)
        setContent { GrokiTheme { GrokiApp(vm, startTab) } }
    }

    companion object { const val EXTRA_TAB = "tab" }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Ma", Icons.Filled.Today),
    Tab("Teendők", Icons.Filled.Checklist),
    Tab("Promók", Icons.Filled.LocalOffer),
    Tab("Levelek", Icons.Filled.Email),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrokiApp(vm: AppViewModel, startTab: Int) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // percenként frissülő "most" a visszaszámlálókhoz
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsentResult(it.data)
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(ui.pendingConsent) {
        ui.pendingConsent?.let {
            vm.consentLaunched()
            consent.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }
    LaunchedEffect(ui.snapshot.lastSyncMs > 0) {
        if (ui.snapshot.lastSyncMs > 0 && Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(undo) {
        val e = undo ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(e.message, actionLabel = "Visszavonás", duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) vm.runUndo(e)
        vm.undo.value = null
    }
    LaunchedEffect(ui.error) {
        val err = ui.error ?: return@LaunchedEffect
        if (ui.snapshot.lastSyncMs > 0) {
            val r = snackbar.showSnackbar(err, actionLabel = "Újra", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.refresh() else vm.dismissError()
        }
    }

    if (ui.snapshot.lastSyncMs == 0L) {
        Scaffold { pad ->
            Box(Modifier.padding(pad)) { SignInScreen(ui.syncing, ui.error) { vm.refresh() } }
        }
        return
    }

    val actions = ItemActions(
        open = { openId = it.id },
        done = vm::done,
        snoozeTomorrow = { vm.snooze(it, 24) },
        decide = { item, d -> vm.decide(item, d) },
    )
    val open = ui.snapshot.openItems(now)
    val badges = listOf(
        open.count { it.category == Category.SECURITY },
        0,
        open.count { it.category == Category.PROMO && ui.snapshot.stateOf(it.id).decision == null },
        ui.snapshot.mails.count { it.unread },
    )
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(tabs[tab].label)
                            Text("Frissítve ${Format.ago(ui.snapshot.lastSyncMs, now)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Frissítés most") }, onClick = { menu = false; vm.refresh() })
                            DropdownMenuItem(text = { Text("Kijelentkezés és helyi adatok törlése") },
                                onClick = { menu = false; vm.signOut() })
                        }
                    },
                    scrollBehavior = scroll,
                )
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(t.label) },
                        icon = {
                            BadgedBox(badge = { if (badges[i] > 0) Badge { Text("${badges[i]}") } }) {
                                Icon(t.icon, t.label)
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = ui.syncing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            when (tab) {
                0 -> TodayScreen(ui.snapshot, now, actions) { tab = it }
                1 -> ItemsScreen(ui.snapshot, now, actions)
                2 -> PromosScreen(ui.snapshot, now, actions)
                else -> MailScreen(ui.snapshot)
            }
        }
    }

    val selected: ActionItem? = openId?.let { id -> ui.snapshot.summary?.items?.firstOrNull { it.id == id } }
    if (selected != null) {
        ItemSheet(
            item = selected,
            state = ui.snapshot.stateOf(selected.id),
            now = now,
            onDismissRequest = { openId = null },
            onDone = { vm.done(selected); openId = null },
            onSnooze = { h -> vm.snooze(selected, h); openId = null },
            onDismissItem = { vm.dismiss(selected); openId = null },
            onDecide = { d -> vm.decide(selected, d); openId = null },
            onReopen = { vm.reopen(selected); openId = null },
        )
    }
}
