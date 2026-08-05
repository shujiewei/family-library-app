package com.familylibrary.app

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familylibrary.app.ui.admin.AdminPinDialog
import com.familylibrary.app.ui.browse.BrowseScreen
import com.familylibrary.app.ui.reading.ReadingScreen
import com.familylibrary.app.ui.scan.BatchScanScreen
import com.familylibrary.app.ui.scan.ScanOrganizeScreen
import com.familylibrary.app.ui.search.BookDetailScreen
import com.familylibrary.app.ui.search.SearchScreen
import com.familylibrary.app.ui.settings.SettingsScreen
import com.familylibrary.app.ui.shelf.ShelfScreen
import com.familylibrary.app.ui.theme.FamilyLibraryTheme
import com.familylibrary.app.ui.wishlist.WishlistScanScreen
import com.familylibrary.app.ui.wishlist.WishlistScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = FamilyLibraryApplication.from(application)
        setContent {
            val ready by app.serviceLocator.initialized.collectAsState()
            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    app.serviceLocator.ensureInitialized()
                }
            }
            FamilyLibraryTheme {
                if (ready) {
                    FamilyLibraryApp(app)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private sealed class Tab(val route: String, val label: String) {
    data object Search : Tab("search", "查找")
    data object Shelf : Tab("shelf", "书架")
    data object Browse : Tab("browse", "分类")
    data object Reading : Tab("reading", "阅读")
    data object Wishlist : Tab("wishlist", "待购")
    data object Settings : Tab("settings", "设置")
}

@Composable
fun FamilyLibraryApp(app: FamilyLibraryApplication) {
    val navController = rememberNavController()
    val adminController = app.serviceLocator.adminModeController
    val isAdmin by adminController.isAdminMode.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val tabs = listOf(Tab.Search, Tab.Shelf, Tab.Browse, Tab.Reading, Tab.Wishlist, Tab.Settings)
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    if (showPinDialog) {
        val failed by adminController.failedAttempts.collectAsState()
        AdminPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                scope.launch {
                    if (adminController.isInCooldown()) return@launch
                    val ok = withContext(Dispatchers.IO) {
                        val settings = app.serviceLocator.appSettingsDao.get()
                        settings != null && com.familylibrary.app.util.Hash.verifyPin(
                            pin, settings.adminPinSalt, settings.adminPinHash
                        )
                    }
                    if (ok) {
                        adminController.enter()
                        showPinDialog = false
                    } else {
                        adminController.recordFailedAttempt()
                    }
                }
            },
            isInCooldown = adminController.isInCooldown(),
            cooldownRemainingMs = adminController.cooldownRemainingMs(),
            failedAttempts = failed,
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val icon = when (tab) {
                            Tab.Search -> Icons.Default.Search
                            Tab.Shelf -> Icons.Default.MenuBook
                            Tab.Browse -> Icons.Default.Category
                            Tab.Reading -> Icons.Default.Book
                            Tab.Wishlist -> Icons.Default.ShoppingCart
                            Tab.Settings -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Search.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Search.route) {
                SearchScreen(
                    app = app,
                    isAdmin = isAdmin,
                    onBookClick = { id -> navController.navigate("book/$id") },
                )
            }
            composable(Tab.Shelf.route) {
                ShelfScreen(
                    isAdmin = isAdmin,
                    app = app,
                    onBookClick = { id -> navController.navigate("book/$id") },
                    onRequestAdmin = { showPinDialog = true },
                    onScanBatch = { rowId, label ->
                        navController.navigate(
                            "batch_scan/$rowId?label=${URLEncoder.encode(label, "UTF-8")}",
                        )
                    },
                    onScanOrganize = { rowId, label ->
                        navController.navigate(
                            "scan_organize/$rowId?label=${URLEncoder.encode(label, "UTF-8")}",
                        )
                    },
                )
            }
            composable(Tab.Browse.route) {
                BrowseScreen(app = app, onBookClick = { id ->
                    navController.navigate("book/$id")
                })
            }
            composable(Tab.Reading.route) {
                ReadingScreen(app = app)
            }
            composable(Tab.Wishlist.route) {
                WishlistScreen(
                    app = app,
                    onScanWishlist = { navController.navigate("wishlist_scan") },
                )
            }
            composable("wishlist_scan") {
                WishlistScanScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    app = app,
                    onRequestAdmin = { showPinDialog = true },
                )
            }
            composable(
                route = "batch_scan/{rowId}?label={label}",
                arguments = listOf(
                    navArgument("rowId") { type = NavType.LongType },
                    navArgument("label") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val rowId = entry.arguments?.getLong("rowId") ?: return@composable
                val label = URLDecoder.decode(entry.arguments?.getString("label").orEmpty(), "UTF-8")
                BatchScanScreen(
                    app = app,
                    targetRowId = rowId,
                    targetLabel = label,
                    adminController = app.serviceLocator.adminModeController,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = "scan_organize/{rowId}?label={label}",
                arguments = listOf(
                    navArgument("rowId") { type = NavType.LongType },
                    navArgument("label") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val rowId = entry.arguments?.getLong("rowId") ?: return@composable
                val label = URLDecoder.decode(entry.arguments?.getString("label").orEmpty(), "UTF-8")
                ScanOrganizeScreen(
                    app = app,
                    targetRowId = rowId,
                    targetLabel = label,
                    adminController = app.serviceLocator.adminModeController,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "book/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                BookDetailScreen(
                    bookId = bookId,
                    app = app,
                    isAdmin = isAdmin,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
