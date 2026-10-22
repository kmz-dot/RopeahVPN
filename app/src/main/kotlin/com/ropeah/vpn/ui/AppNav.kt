package com.ropeah.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ropeah.vpn.BuildConfig
import com.ropeah.vpn.R
import com.ropeah.vpn.data.SessionGuard
import com.ropeah.vpn.data.SessionState
import kotlinx.coroutines.delay
import com.ropeah.vpn.ui.CloudflareScanPage
import com.ropeah.vpn.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Home : Tab("home", R.string.nav_home, Icons.Filled.Home)
    data object Servers : Tab("servers", R.string.nav_servers, Icons.Filled.Dns)
    data object Account : Tab("account", R.string.nav_account, Icons.Filled.Person)
}

private const val ROUTE_CLOUDFLARE_SCAN = "cloudflare_scan"
private const val ROUTE_SETTINGS = "settings"

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

    val expired = session != null && SessionGuard.isExpired(session, nowMs)

    LaunchedEffect(expired) {
        if (expired) sessionViewModel.stopVpn()
    }

    when {
        !ready -> LoadingGate()
        session == null -> LoginScreen(viewModel = sessionViewModel)
        expired -> ExpiredScreen(
            session = session!!,
            checking = checkingAccess,
            onCheckAgain = { sessionViewModel.revalidateNow() },
            onLogout = { sessionViewModel.logout() },
        )
        else -> MainScaffold(
            session = session!!,
            sessionViewModel = sessionViewModel,
        )
    }
}

@Composable
private fun LoadingGate() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    session: SessionState,
    sessionViewModel: SessionViewModel,
) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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

    val isMainTab = onRoute in listOf(Tab.Home.route, Tab.Servers.route, Tab.Account.route)

    fun navigateTo(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.72f),
                drawerContainerColor = MaterialTheme.colorScheme.background,
            ) {
                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                ) {
                    Column {
                        Text(
                            text = "V2EON",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = session.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DrawerItem(
                    icon = Icons.Filled.Home,
                    label = stringResource(R.string.nav_home),
                    selected = onRoute == Tab.Home.route || onRoute == null,
                    onClick = {
                        navigateTo(Tab.Home.route)
                        scope.launch { drawerState.close() }
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.Dns,
                    label = stringResource(R.string.nav_servers),
                    selected = onRoute == Tab.Servers.route,
                    onClick = {
                        navigateTo(Tab.Servers.route)
                        scope.launch { drawerState.close() }
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.nav_account),
                    selected = onRoute == Tab.Account.route,
                    onClick = {
                        navigateTo(Tab.Account.route)
                        scope.launch { drawerState.close() }
                    },
                )
                if (!BuildConfig.MANAGED_MODE) {
                    DrawerItem(
                        icon = Icons.Filled.Cloud,
                        label = stringResource(R.string.nav_cloudflare_scan),
                        selected = onRoute == ROUTE_CLOUDFLARE_SCAN,
                        onClick = {
                            nav.navigate(ROUTE_CLOUDFLARE_SCAN) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        },
                    )
                }
                DrawerItem(
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.nav_settings),
                    selected = onRoute == ROUTE_SETTINGS,
                    onClick = {
                        nav.navigate(ROUTE_SETTINGS) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                        }
                        scope.launch { drawerState.close() }
                    },
                )
                Spacer(Modifier.weight(1f))
                DrawerItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = stringResource(R.string.drawer_logout),
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        sessionViewModel.logout()
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
        },
    ) {
        Scaffold(
            topBar = {
                when (onRoute) {
                    Tab.Home.route -> DrawerTopBar(
                        title = stringResource(R.string.drawer_title_home),
                        onMenuClick = { scope.launch { drawerState.open() } },
                    )
                    Tab.Servers.route -> DrawerTopBar(
                        title = stringResource(R.string.servers_screen_title),
                        onMenuClick = { scope.launch { drawerState.open() } },
                        actions = {
                            val context = LocalContext.current
                            IconButton(onClick = {
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        LogExporter.capture(context)
                                    }
                                    LogExporter.share(context, file)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Article,
                                    contentDescription = stringResource(R.string.action_share_log),
                                )
                            }
                        },
                    )
                    Tab.Account.route -> DrawerTopBar(
                        title = stringResource(R.string.account_title),
                        onMenuClick = { scope.launch { drawerState.open() } },
                    )
                    ROUTE_SETTINGS -> DrawerTopBar(
                        title = stringResource(R.string.settings_title),
                        onMenuClick = { scope.launch { drawerState.open() } },
                    )
                    else -> {}
                }
            },
            bottomBar = {
                if (isMainTab) {
                    NavigationBar {
                        listOf(Tab.Home, Tab.Servers, Tab.Account).forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = {
                                    Icon(imageVector = tab.icon, contentDescription = null)
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
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
                        onOpenAccount = { navigateTo(Tab.Account.route) },
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
                composable(Tab.Account.route) {
                    AccountScreen(
                        session = session,
                        onLogout = { sessionViewModel.logout() },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen()
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
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = {
            Icon(imageVector = icon, contentDescription = null)
        },
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerTopBar(
    title: String,
    onMenuClick: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.drawer_open),
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}
