package dev.whayn.thyme.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.whayn.thyme.DoseListViewModel
import dev.whayn.thyme.MedicationsViewModel
import dev.whayn.thyme.SettingsViewModel
import dev.whayn.thyme.StatsViewModel
import dev.whayn.thyme.ui.DoseListScreen
import dev.whayn.thyme.ui.MedicationEditorScreen
import dev.whayn.thyme.ui.MedicationsScreen
import dev.whayn.thyme.ui.SettingsScreen
import dev.whayn.thyme.ui.StatsScreen
import java.time.LocalDate

@Composable
fun ThymeNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.Today,
        modifier = modifier,
    ) {
        composable<Destinations.Today> {
            val context = LocalContext.current.applicationContext
            val viewModel: DoseListViewModel = viewModel(
                factory = DoseListViewModel.factory(context),
            )
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshClock()
            }
            DoseListScreen(
                doses = viewModel.doses.collectAsStateWithLifecycle().value,
                date = viewModel.date.collectAsStateWithLifecycle().value,
                today = viewModel.today.collectAsStateWithLifecycle().value,
                now = viewModel.now.collectAsStateWithLifecycle().value,
                onToggle = viewModel::toggle,
                onSelectDate = viewModel::selectDate,
                onNavigateToAdd = { navController.navigate(Destinations.MedicationEditor()) },
                contentPadding = contentPadding,
            )
        }
        composable<Destinations.Medications> {
            val context = LocalContext.current.applicationContext
            val viewModel: MedicationsViewModel = viewModel(
                factory = MedicationsViewModel.factory(context),
            )
            MedicationsScreen(
                medications = viewModel.medications.collectAsStateWithLifecycle().value,
                onEditRegimen = { medicationId, regimenId ->
                    navController.navigate(Destinations.MedicationEditor(medicationId, regimenId))
                },
                onAddCourse = { medicationId ->
                    navController.navigate(Destinations.MedicationEditor(medicationId, null))
                },
                onStop = viewModel::stop,
                onDelete = viewModel::delete,
                contentPadding = contentPadding,
            )
        }
        composable<Destinations.Stats> {
            val context = LocalContext.current.applicationContext
            val viewModel: StatsViewModel = viewModel(
                factory = StatsViewModel.factory(context),
            )
            StatsScreen(
                summary = viewModel.summary.collectAsStateWithLifecycle().value,
                window = viewModel.window.collectAsStateWithLifecycle().value,
                onSelectWindow = viewModel::selectWindow,
                month = viewModel.month.collectAsStateWithLifecycle().value,
                calendarDays = viewModel.calendarDays.collectAsStateWithLifecycle().value,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onDayClick = { date -> navController.navigate(Destinations.DayDetail(date.toEpochDay())) },
                contentPadding = contentPadding,
            )
        }
        composable<Destinations.DayDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Destinations.DayDetail>()
            val context = LocalContext.current.applicationContext
            val viewModel: DoseListViewModel = viewModel(
                factory = DoseListViewModel.factory(context, LocalDate.ofEpochDay(route.epochDay)),
            )
            DoseListScreen(
                doses = viewModel.doses.collectAsStateWithLifecycle().value,
                date = viewModel.date.collectAsStateWithLifecycle().value,
                today = viewModel.today.collectAsStateWithLifecycle().value,
                now = viewModel.now.collectAsStateWithLifecycle().value,
                onToggle = viewModel::toggle,
                onSelectDate = viewModel::selectDate,
                onNavigateToAdd = { navController.navigate(Destinations.MedicationEditor()) },
                contentPadding = contentPadding,
            )
        }
        composable<Destinations.Settings> {
            val context = LocalContext.current.applicationContext
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(context),
            )
            SettingsScreen(
                settings = viewModel.settings.collectAsStateWithLifecycle().value,
                onThemeModeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor,
                contentPadding = contentPadding,
            )
        }
        composable<Destinations.MedicationEditor> { backStackEntry ->
            val destination = backStackEntry.toRoute<Destinations.MedicationEditor>()
            MedicationEditorScreen(
                medicationId = destination.medicationId,
                regimenId = destination.regimenId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
