package dev.whayn.thyme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.whayn.thyme.ui.nav.Destinations
import dev.whayn.thyme.ui.nav.ThymeNavHost
import dev.whayn.thyme.ui.theme.ThymeTheme

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
        val entry by navController.currentBackStackEntryAsState()
        // hasRoute rather than matching route strings: the string form works only
        // as long as no route name is a prefix of another, which is a trap waiting
        // for the next destination to be added.
        val destination = entry?.destination
        // DayDetail (drilled into from the Stats calendar) counts as "Today" for the
        // bottom nav: it's the same screen pinned to a different date, so leaving no
        // tab highlighted there would read as a dead end rather than part of Today.
        val isToday = destination?.hasRoute(Destinations.Today::class) == true ||
            destination?.hasRoute(Destinations.DayDetail::class) == true
        val isMedications = destination?.hasRoute(Destinations.Medications::class) == true
        val isEditor = destination?.hasRoute(Destinations.MedicationEditor::class) == true
        val bottomDestinations = listOf(
            BottomDestination("Today", Icons.Filled.Home, Destinations.Today) {
                it?.hasRoute(Destinations.Today::class) == true || it?.hasRoute(Destinations.DayDetail::class) == true
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

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isEditor) NavigationBar {
                    bottomDestinations.forEach { item ->
                        NavigationBarItem(
                            selected = item.matches(destination),
                            onClick = {
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
                if (isToday || isMedications) {
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(Destinations.MedicationEditor()) },
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Add medication") },
                    )
                }
            },
        ) { innerPadding ->
            ThymeNavHost(navController = navController, contentPadding = innerPadding)
        }
    }
}
