package com.ropeah.vpn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ropeah.vpn.BuildConfig
import com.ropeah.vpn.R
import com.ropeah.vpn.data.SessionGuard
import com.ropeah.vpn.data.SessionState
import com.ropeah.vpn.ui.neo.MicroLabel
import com.ropeah.vpn.ui.neo.NeoBackground
import com.ropeah.vpn.ui.neo.NeoBottomBar
import com.ropeah.vpn.ui.neo.NeoLogoMark
import com.ropeah.vpn.ui.neo.NeoNavItem
import com.ropeah.vpn.ui.neo.NeoTopBar
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import com.ropeah.vpn.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROUTE_CLOUDFLARE_SCAN = "cloudflare_scan"
private const val ROUTE_SETTINGS = "settings"

private sealed class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Home : Tab("home", R.string.nav_home, Icons.Filled.Home)
    data object Servers : Tab("servers", R.string.nav_servers, Icons.Filled.Dns)
    data object Account : Tab("account", R.string.nav_account, Icons.Filled.Person)
    data object Settings : Tab(ROUTE_SETTINGS, R.string.nav_settings, Icons.Filled.Settings)
}

@Composable
fun AppNav() {
    val sessionViewModel: SessionViewModel = viewModel()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val ready by sessionViewModel.ready.collectAsStateWithLifecycle()
    val checkingAccess by sessionViewModel.checkingAccess.collectAsStateWithLifecycle()

    // Server-anchored clock tick, coarse enough to only drive the lock screen.
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(10_000L)
        }
    }

    val locked = session != null && SessionGuard.isLocked(session, nowMs)

    LaunchedEffect(locked) {
        if (locked) sessionViewModel.stopVpn()
    }

    NeoBackground {
        when {
            !ready -> LoadingGate()
            // The login gate is part of managed mode; a local-only build keeps the
            // original server-management flow instead of a dead-end login screen.
            session == null && BuildConfig.MANAGED_MODE -> LoginScreen(viewModel = sessionViewModel)
            locked -> {
                val lockMessage by sessionViewModel.syncMessage.collectAsStateWithLifecycle()
                ExpiredScreen(
                    session = session!!,
                    checking = checkingAccess,
                    suspended = session?.suspended == true,
                    message = lockMessage,
                    onCheckAgain = { sessionViewModel.revalidateNow() },
                    onLogout = { sessionViewModel.logout() },
                )
            }
            else -> MainScaffold(
                session = session,
                sessionViewModel = sessionViewModel,
            )
        }
    }
}

@Composable
private fun LoadingGate() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeoLogoMark(size = 64.dp)
            Spacer(Modifier.height(26.dp))
            CircularProgressIndicator(
                color = NeoTeal,
                trackColor = Color.White.copy(alpha = 0.08f),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(18.dp))
            MicroLabel(text = stringResource(R.string.neo_loading))
        }
    }
}

@Composable
private fun MainScaffold(
    session: SessionState?,
    sessionViewModel: SessionViewModel,
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val onRoute = currentDestination?.route
    val syncing by sessionViewModel.syncing.collectAsStateWithLifecycle()
    val syncMessage by sessionViewModel.syncMessage.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sessionViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) sessionViewModel.syncIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs = if (session != null) {
        listOf(Tab.Home, Tab.Servers, Tab.Account, Tab.Settings)
    } else {
        listOf(Tab.Home, Tab.Servers, Tab.Settings)
    }
    val isMainTab = onRoute in tabs.map { it.route }
    val selectedRoute = if (isMainTab) onRoute else null
    val navItems = tabs.map {
        NeoNavItem(
            route = it.route,
            label = stringResource(it.labelRes),
            icon = it.icon,
        )
    }

    fun navigateTo(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val title = when (onRoute) {
        Tab.Home.route -> stringResource(R.string.nav_home)
        Tab.Servers.route -> stringResource(R.string.nav_servers)
        Tab.Account.route -> stringResource(R.string.nav_account)
        ROUTE_SETTINGS -> stringResource(R.string.nav_settings)
        ROUTE_CLOUDFLARE_SCAN -> stringResource(R.string.nav_cloudflare_scan)
        else -> ""
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            NeoTopBar(title = title) {
                if (onRoute == Tab.Servers.route) {
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    LogExporter.capture(context)
                                }
                                LogExporter.share(context, file)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Article,
                            contentDescription = stringResource(R.string.action_share_log),
                            tint = NeoTextSecondary,
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (isMainTab) {
                NeoBottomBar(
                    items = navItems,
                    selectedRoute = selectedRoute,
                    onSelect = ::navigateTo,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    session = session,
                    onSelectServer = { navigateTo(Tab.Servers.route) },
                    onOpenAccount = {
                        if (session != null) navigateTo(Tab.Account.route)
                    },
                )
            }
            composable(Tab.Servers.route) {
                ServerListScreen(
                    session = session,
                    syncing = syncing,
                    syncMessage = syncMessage,
                    onSync = { sessionViewModel.syncNow() },
                )
            }
            if (session != null) {
                composable(Tab.Account.route) {
                    AccountScreen(
                        session = session,
                        onLogout = { sessionViewModel.logout() },
                        syncing = syncing,
                        syncMessage = syncMessage,
                        onSync = { sessionViewModel.syncNow() },
                    )
                }
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onOpenCloudflareScan = if (!BuildConfig.MANAGED_MODE) {
                        {
                            nav.navigate(ROUTE_CLOUDFLARE_SCAN) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            composable(ROUTE_CLOUDFLARE_SCAN) {
                val vm: VpnViewModel = viewModel()
                val activeServer by vm.activeServer.collectAsStateWithLifecycle()
                CloudflareScanPage(
                    server = activeServer,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
