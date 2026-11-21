package com.afede.kidago.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.afede.kidago.R
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing
import com.afede.kidago.ui.theme.KidaGoWordmark

/** The destinations of the Navigation Compose graph: three tabs and Ajustes (FEAT-006 C7). */
enum class Destination(val route: String, val label: String) {
    Scan("scan", "Escanear"),
    Products("products", "Productos"),
    Price("price", "Precio"),
    Settings("settings", "Ajustes"),
}

private class Tab(val destination: Destination, @DrawableRes val icon: Int, val iconSize: Dp)

// Figma order: Productos, Escanear, Precio. Productos is the dog face (the app's mascot), Precio the euro sign.
private val tabs = listOf(
    Tab(Destination.Products, R.drawable.ic_tab_products, 40.dp),
    Tab(Destination.Scan, R.drawable.ic_tab_scan, 40.dp),
    Tab(Destination.Price, R.drawable.ic_euro, 38.dp),
)

/**
 * Shared chrome: header (afede logo + wordmark, [headerContent] slot, persistent gear), the navigation host and
 * the curved bottom tab bar with a live badge on Productos. Feature screens plug in through the screen slots.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    productsBadge: Int = 0,
    headerContent: @Composable () -> Unit = {},
    scanScreen: @Composable () -> Unit = { PlaceholderScreen(Destination.Scan) },
    productsScreen: @Composable () -> Unit = { PlaceholderScreen(Destination.Products) },
    priceScreen: @Composable () -> Unit = { PlaceholderScreen(Destination.Price) },
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
                composable(Destination.Price.route) { priceScreen() }
                composable(Destination.Settings.route) { settingsScreen() }
            }
        }
        TabBar(route = route, productsBadge = productsBadge) { destination ->
            // Ajustes is opened on top of a tab, outside the tabs' saved state. Close it first: otherwise the
            // popUpTo below saves it under the start tab and restoreState brings it straight back (Escanear stuck).
            if (route == Destination.Settings.route) navController.popBackStack(Destination.Settings.route, inclusive = true)
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
    Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = KidaGoSpacing.Gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // The logo file is cropped to its artwork (the Figma export carries a lot of empty margin).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.afede_logo), contentDescription = "afede", Modifier.size(width = 112.dp, height = 34.dp))
                Text("KidaGo", style = KidaGoWordmark, color = KidaGoColors.Text)
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = Destination.Settings.label, tint = KidaGoColors.Text)
            }
        }
        // The slot aligns to the 32 px content margin, like the screens below (FEAT-001 UI Definition, strip width).
        Box(Modifier.fillMaxWidth().padding(horizontal = KidaGoSpacing.Outer)) { content() }
    }
}

// Bottom bar (FEAT-006 UI Definition, validated 2026-11-21): a floating white pill with three equal tabs; the active
// tab's icon rises into a white bubble with the brand's pink gradient. Design px (1 dp = 1 design px, see DesignPixels).
private val BubbleRise = 36.dp // the bar starts this far below the top; the active bubble starts at the top
private val BarHeight = 96.dp
private val BarShape = RoundedCornerShape(40.dp)
private val GestureInset = 24.dp

@Composable
private fun TabBar(route: String?, productsBadge: Int, onSelect: (Destination) -> Unit) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The design already leaves room for the gesture bar; only a taller (3-button) inset adds height.
    val extra = (bottomInset - GestureInset).coerceAtLeast(0.dp)
    Box(Modifier.fillMaxWidth().height(BubbleRise + BarHeight + 6.dp + extra).testTag("tab-bar")) {
        Box(
            Modifier.padding(start = 16.dp, end = 16.dp, top = BubbleRise).fillMaxWidth().height(BarHeight)
                .shadow(10.dp, BarShape)
                .background(KidaGoColors.Surface, BarShape)
                .border(1.dp, KidaGoColors.Text.copy(alpha = 0.08f), BarShape),
        )
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(BubbleRise + BarHeight)) {
            tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = route == tab.destination.route,
                    badge = if (tab.destination == Destination.Products) productsBadge else 0,
                    modifier = Modifier.weight(1f),
                ) { onSelect(tab.destination) }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) KidaGoColors.Accent else KidaGoColors.Text
    Box(modifier.fillMaxSize().selectable(selected, role = Role.Tab, onClick = onClick)) {
        val icon: @Composable () -> Unit = {
            BadgedBox(badge = { if (badge > 0) CountPill(badge) }) {
                Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(tab.iconSize))
            }
        }
        if (selected) {
            // The raised bubble: white with the bar's shadow, holding the pink gradient circle and the red icon.
            Box(Modifier.align(Alignment.TopCenter).size(76.dp).shadow(10.dp, CircleShape).background(KidaGoColors.Surface, CircleShape), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(64.dp).background(Brush.verticalGradient(listOf(KidaGoColors.Accent.copy(alpha = 0.05f), KidaGoColors.Accent.copy(alpha = 0.22f))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { icon() }
            }
        } else {
            Box(Modifier.align(Alignment.TopCenter).padding(top = BubbleRise + 12.dp)) { icon() }
        }
        Text(
            tab.destination.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = color,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = BubbleRise + 60.dp),
        )
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
