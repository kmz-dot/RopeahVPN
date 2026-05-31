package com.ropeah.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ropeah.vpn.R
import com.ropeah.vpn.util.LogExporter
import com.ropeah.vpn.ui.CloudflareScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel

private sealed class Tab(val route: String, val labelRes: Int) {
    data object Home : Tab("home", R.string.nav_home)
    data object Servers : Tab("servers", R.string.nav_servers)
    data object Settings : Tab("settings", R.string.nav_settings)
}

private const val ROUTE_CLOUDFLARE_SCAN = "cloudflare_scan"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val onRoute = currentDestination?.route

    val isMainTab = onRoute in listOf(Tab.Home.route, Tab.Servers.route)

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

                // Drawer header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                ) {
                    Column {
                        Text(
                            text = "RopeahVPN",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.drawer_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Navigation items
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
                DrawerItem(
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.nav_settings),
                    selected = onRoute == Tab.Settings.route,
                    onClick = {
                        nav.navigate(Tab.Settings.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                        }
                        scope.launch { drawerState.close() }
                    },
                )
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
                    Tab.Settings.route -> DrawerTopBar(
                        title = stringResource(R.string.settings_title),
                        onMenuClick = { scope.launch { drawerState.open() } },
                    )
                    else -> {}
                }
            },
            bottomBar = {
                if (isMainTab) {
                    NavigationBar {
                        listOf(Tab.Home, Tab.Servers).forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            Tab.Home -> Icons.Filled.Home
                                            Tab.Servers -> Icons.Filled.Dns
                                            Tab.Settings -> Icons.Filled.Settings
                                        },
                                        contentDescription = null,
                                    )
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
                        onSelectServer = {
                            navigateTo(Tab.Servers.route)
                        },
                    )
                }
                composable(Tab.Servers.route) {
                    ServerListScreen()
                }
                composable(Tab.Settings.route) {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
