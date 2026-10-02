package com.afede.kidago.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing
import com.afede.kidago.ui.theme.KidaGoWordmark

/** The three destinations of Project Architecture's Navigation Compose graph. */
enum class Destination(val route: String, val label: String) {
    Scan("scan", "Escanear"),
    Products("products", "Productos"),
    Settings("settings", "Ajustes"),
}

private data class Tab(val destination: Destination, val icon: ImageVector)

// Provisional icons: the Figma icon set is not available as assets; see TASK-003 report.
private val tabs = listOf(
    Tab(Destination.Scan, Icons.Default.Search),
    Tab(Destination.Products, Icons.AutoMirrored.Filled.List),
)

/**
 * Shared chrome: header (wordmark, [headerContent] slot, persistent gear), the navigation host and the
 * bottom tab bar with a live badge on Productos. Feature screens plug in through the screen slots.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    productsBadge: Int = 0,
    headerContent: @Composable () -> Unit = {},
    scanScreen: @Composable () -> Unit = { PlaceholderScreen(Destination.Scan) },
    productsScreen: @Composable () -> Unit = { PlaceholderScreen(Destination.Products) },
    settingsScreen: @Composable () -> Unit = { PlaceholderScreen(Destination.Settings) },
) {
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(
            onSettings = {
                // Single instance: repeated taps never stack Ajustes, and back returns to the origin tab.
                navController.navigate(Destination.Settings.route) { launchSingleTop = true }
            },
            content = headerContent,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(navController, startDestination = Destination.Scan.route) {
                composable(Destination.Scan.route) { scanScreen() }
                composable(Destination.Products.route) { productsScreen() }
                composable(Destination.Settings.route) { settingsScreen() }
            }
        }
        TabBar(route = route, productsBadge = productsBadge) { destination ->
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(horizontal = KidaGoSpacing.Gap)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("KidaGo", style = KidaGoWordmark, color = KidaGoColors.Text)
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = Destination.Settings.label, tint = KidaGoColors.Text)
            }
        }
        content()
    }
}

@Composable
private fun TabBar(route: String?, productsBadge: Int, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = KidaGoColors.Surface, contentColor = KidaGoColors.Text) {
        tabs.forEach { (destination, icon) ->
            NavigationBarItem(
                selected = route == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    BadgedBox(badge = {
                        if (destination == Destination.Products && productsBadge > 0) {
                            CountPill(productsBadge)
                        }
                    }) { Icon(icon, contentDescription = null) }
                },
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = KidaGoColors.Accent,
                    selectedTextColor = KidaGoColors.Accent,
                    indicatorColor = KidaGoColors.Background,
                    unselectedIconColor = KidaGoColors.Text,
                    unselectedTextColor = KidaGoColors.Text,
                ),
            )
        }
    }
}

/** Small red pill with a count; grows horizontally for several digits. */
@Composable
private fun CountPill(count: Int) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(KidaGoColors.Accent, KidaGoShapes.Pill)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = KidaGoColors.Surface,
            modifier = Modifier.testTag("tab-badge"),
        )
    }
}

/** Implementation placeholder until each Feature supplies its screen. */
@Composable
fun PlaceholderScreen(destination: Destination) {
    Box(Modifier.fillMaxSize().testTag("screen-${destination.route}"), contentAlignment = Alignment.Center) {
        Text("${destination.label} (pendiente)", style = MaterialTheme.typography.headlineMedium)
    }
}
