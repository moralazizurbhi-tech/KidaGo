package com.afede.kidago.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
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

/** The three destinations of Project Architecture's Navigation Compose graph. */
enum class Destination(val route: String, val label: String) {
    Scan("scan", "Escanear"),
    Products("products", "Productos"),
    Settings("settings", "Ajustes"),
}

private class Tab(val destination: Destination, @DrawableRes val icon: Int, val iconWidth: Int, val iconHeight: Int)

// Figma order: Productos on the left, Escanear on the right. Sizes are the Figma icon frames (dp);
// the teddy bear is cropped to its visible 53 rows.
private val tabs = listOf(
    Tab(Destination.Products, R.drawable.ic_tab_products, 83, 53),
    Tab(Destination.Scan, R.drawable.ic_tab_scan, 70, 70),
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
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = KidaGoSpacing.Gap, top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
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
        content()
    }
}

// Figma frame: 480 wide, bar silhouette 506 x 157 starting just below the status of the screen; the visible part
// of the bar is 135 high and the rest runs off the bottom edge.
private const val FIGMA_WIDTH = 480f
private val TabBarHeight = 135.dp
private val GestureInset = 24.dp
private const val SVG_X = 21f // the exported SVG is 522 wide, centred on the 480 frame
private const val SVG_Y = 4f

@Composable
private fun TabBar(route: String?, productsBadge: Int, onSelect: (Destination) -> Unit) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barPath = remember { PathParser().parsePathString(TAB_BAR_PATH).toPath() }
    // The Figma bar height already includes the gesture-bar area; only a taller (3-button) inset adds height.
    BoxWithConstraints(Modifier.fillMaxWidth().height(TabBarHeight + (bottomInset - GestureInset).coerceAtLeast(0.dp)).clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / FIGMA_WIDTH
            val shape = Path().apply {
                addPath(barPath)
                transform(Matrix().apply { translate(-SVG_X * s, -SVG_Y * s); scale(s, s) })
            }
            // The two soft Figma drop shadows (0 2 3 @15%, 0 1 1 @30%).
            drawIntoCanvas { canvas ->
                for ((blur, dy, alpha) in listOf(Triple(6f, 2f, .15f), Triple(2f, 1f, .30f))) {
                    val paint = Paint().apply {
                        color = KidaGoColors.Surface
                        asFrameworkPaint().setShadowLayer(blur * s, 0f, dy * s, Color.Black.copy(alpha = alpha).toArgb())
                    }
                    canvas.drawPath(shape, paint)
                }
            }
            // Below the lobes the bar is solid, whatever the inset height.
            drawRect(KidaGoColors.Surface, topLeft = Offset(0f, 100f * s), size = Size(size.width, size.height))
        }
        Row(Modifier.fillMaxSize()) {
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
        if (selected) {
            // Soft red glow under the active tab.
            Image(
                painterResource(R.drawable.tab_active_blob),
                contentDescription = null,
                Modifier.align(Alignment.TopCenter).offset(y = 70.dp).requiredSize(251.dp, 150.dp),
            )
        }
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 20.dp).height(70.dp), contentAlignment = Alignment.TopCenter) {
                BadgedBox(badge = { if (badge > 0) CountPill(badge) }) {
                    Icon(
                        painterResource(tab.icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.requiredSize(tab.iconWidth.dp, tab.iconHeight.dp).offset(y = if (tab.iconHeight < 70) 5.dp else 0.dp),
                    )
                }
            }
            Text(
                tab.destination.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = color,
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
