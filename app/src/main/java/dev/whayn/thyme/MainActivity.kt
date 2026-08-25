package dev.whayn.thyme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.whayn.thyme.alert.AlertRingerService
import dev.whayn.thyme.ui.alert.AlertActivity
import dev.whayn.thyme.ui.RingingBanner
import dev.whayn.thyme.ui.nav.Destinations
import dev.whayn.thyme.ui.nav.ThymeNavHost
import dev.whayn.thyme.ui.theme.ThymeTheme
import dev.whayn.thyme.ui.theme.rememberReducedMotion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThymeApp() }
    }
}

private data class BottomDestination(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val matches: (NavDestination?) -> Boolean,
)

@Composable
private fun ThymeApp() {
    val context = LocalContext.current.applicationContext
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context),
    )
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    ThymeTheme(mode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        val navController = rememberNavController()
        val doseListViewModel: DoseListViewModel = viewModel(
            factory = DoseListViewModel.factory(context),
        )
        val reducedMotion = rememberReducedMotion()
        val entry by navController.currentBackStackEntryAsState()
        // hasRoute rather than matching route strings: the string form works only
        // as long as no route name is a prefix of another, which is a trap waiting
        // for the next destination to be added.
        val destination = entry?.destination
        val isToday = destination?.hasRoute(Destinations.Today::class) == true
        val isMedications = destination?.hasRoute(Destinations.Medications::class) == true
        val isFullScreen =
            destination?.hasRoute(Destinations.MedicationDetail::class) == true ||
                    destination?.hasRoute(Destinations.CourseEditor::class) == true ||
                    destination?.hasRoute(Destinations.MedicationMetadata::class) == true ||
                    destination?.hasRoute(Destinations.AlertSetup::class) == true
        val bottomDestinations = listOf(
            BottomDestination("Today", Icons.Filled.Home, Destinations.Today) {
                it?.hasRoute(Destinations.Today::class) == true
            },
            BottomDestination(
                "Medications",
                Icons.AutoMirrored.Filled.List,
                Destinations.Medications,
            ) { it?.hasRoute(Destinations.Medications::class) == true },
            BottomDestination("Stats", Icons.Filled.BarChart, Destinations.Stats) {
                it?.hasRoute(Destinations.Stats::class) == true
            },
            BottomDestination("Settings", Icons.Filled.Settings, Destinations.Settings) {
                it?.hasRoute(Destinations.Settings::class) == true
            },
        )

        // Tracked here rather than by hoisting each screen's LazyListState,
        // because the FAB belongs to this Scaffold and the lists do not.
        var fabExpanded by remember { mutableStateOf(true) }
        val fabScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < -1f) fabExpanded = false
                    else if (available.y > 1f) fabExpanded = true
                    return Offset.Zero
                }
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(fabScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            // Sits above every tab rather than only Today: whichever screen you
            // happened to open, a ringing alarm needs a way out from here.
            topBar = {
                val ringing = AlertRingerService.ringing.collectAsStateWithLifecycle().value
                val bannerContext = LocalContext.current
                RingingBanner(
                    alert = ringing,
                    onOpen = {
                        ringing?.let {
                            bannerContext.startActivity(
                                AlertActivity.intent(
                                    bannerContext, it.groupKey, it.doseIds,
                                    it.forDate, it.tier, it.critical,
                                )
                            )
                        }
                    },
                    onSilence = { AlertRingerService.stop(bannerContext) },
                )
            },
            bottomBar = {
                if (!isFullScreen) NavigationBar {
                    bottomDestinations.forEach { item ->
                        NavigationBarItem(
                            selected = item.matches(destination),
                            onClick = {
                                if (item.route === Destinations.Today) {
                                    doseListViewModel.showToday()
                                }
                                navController.navigate(item.route) {
                                    popUpTo(Destinations.Today) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = isToday || isMedications,
                    enter = if (reducedMotion) EnterTransition.None
                    else fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.8f),
                    exit = if (reducedMotion) ExitTransition.None
                    else fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.8f),
                ) {
                    // Collapses to an icon once the list is scrolled. The wide
                    // extended form covered a whole card mid-scroll; it stays
                    // extended at the top of the list, where it has room and
                    // where a first-time user needs the label.
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(Destinations.MedicationMetadata()) },
                        expanded = fabExpanded,
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Add medication") },
                        text = { Text("Add medication") },
                    )
                }
            },
        ) { innerPadding ->
            ThymeNavHost(
                navController = navController,
                doseListViewModel = doseListViewModel,
                contentPadding = innerPadding,
            )
        }
    }
}

