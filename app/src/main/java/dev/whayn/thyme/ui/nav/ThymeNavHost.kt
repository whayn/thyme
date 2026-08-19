package dev.whayn.thyme.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.whayn.thyme.DoseListViewModel
import dev.whayn.thyme.MedicationDetailViewModel
import dev.whayn.thyme.MedicationsViewModel
import dev.whayn.thyme.SettingsViewModel
import dev.whayn.thyme.StatsViewModel
import dev.whayn.thyme.ui.CourseEditorScreen
import dev.whayn.thyme.ui.DoseListScreen
import dev.whayn.thyme.ui.MedicationDetailScreen
import dev.whayn.thyme.ui.MedicationMetadataScreen
import dev.whayn.thyme.ui.MedicationsScreen
import dev.whayn.thyme.ui.SettingsScreen
import dev.whayn.thyme.ui.StatsScreen
import dev.whayn.thyme.ui.theme.rememberReducedMotion

/** Bottom nav order, used to pick the slide direction between tabs. */
private val TabOrder = listOf(
    Destinations.Today::class,
    Destinations.Medications::class,
    Destinations.Stats::class,
    Destinations.Settings::class,
)

private fun NavDestination.tabIndex(): Int = TabOrder.indexOfFirst { hasRoute(it) }

/** Forward (later tab) slides left; backward (earlier tab) slides right. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlide(): AnimatedContentTransitionScope.SlideDirection {
    val from = initialState.destination.tabIndex()
    val to = targetState.destination.tabIndex()
    return if (to >= from) AnimatedContentTransitionScope.SlideDirection.Left
    else AnimatedContentTransitionScope.SlideDirection.Right
}

/** Full-screen (non-tab) destinations slide up over the list and fade back out. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.fullScreenEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) EnterTransition.None
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(300))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fullScreenExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) ExitTransition.None
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300))

@Composable
fun ThymeNavHost(
    navController: NavHostController,
    doseListViewModel: DoseListViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    NavHost(
        navController = navController,
        startDestination = Destinations.Today,
        modifier = modifier,
        enterTransition = {
            if (reducedMotion) EnterTransition.None
            else slideIntoContainer(tabSlide(), tween(300))
        },
        exitTransition = {
            if (reducedMotion) ExitTransition.None
            else slideOutOfContainer(tabSlide(), tween(300))
        },
        popEnterTransition = {
            if (reducedMotion) EnterTransition.None
            else slideIntoContainer(tabSlide(), tween(300))
        },
        popExitTransition = {
            if (reducedMotion) ExitTransition.None
            else slideOutOfContainer(tabSlide(), tween(300))
        },
    ) {
        composable<Destinations.Today> {
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                doseListViewModel.refreshClock()
            }
            DoseListScreen(
                state = doseListViewModel.doses.collectAsStateWithLifecycle().value,
                date = doseListViewModel.date.collectAsStateWithLifecycle().value,
                today = doseListViewModel.today.collectAsStateWithLifecycle().value,
                now = doseListViewModel.now.collectAsStateWithLifecycle().value,
                onToggle = doseListViewModel::toggle,
                onSelectDate = doseListViewModel::selectDate,
                onNavigateToAdd = { navController.navigate(Destinations.MedicationMetadata()) },
                contentPadding = contentPadding,
            )
        }
        composable<Destinations.Medications> {
            val context = LocalContext.current.applicationContext
            val viewModel: MedicationsViewModel = viewModel(
                factory = MedicationsViewModel.factory(context),
            )
            MedicationsScreen(
                state = viewModel.medications.collectAsStateWithLifecycle().value,
                onOpenMedication = { medicationId ->
                    navController.navigate(Destinations.MedicationDetail(medicationId))
                },
                contentPadding = contentPadding,
            )
        }
        composable<Destinations.Stats> {
            val context = LocalContext.current.applicationContext
            val viewModel: StatsViewModel = viewModel(
                factory = StatsViewModel.factory(context),
            )
            StatsScreen(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                window = viewModel.window.collectAsStateWithLifecycle().value,
                onSelectWindow = viewModel::selectWindow,
                month = viewModel.month.collectAsStateWithLifecycle().value,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onDayClick = { date ->
                    doseListViewModel.selectDate(date)
                    navController.navigate(Destinations.Today) {
                        popUpTo(Destinations.Today) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
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
                onSeedFakeData = viewModel::seedFakeData,
                onClearAllData = viewModel::clearAllData,
            )
        }
        composable<Destinations.MedicationDetail>(
            enterTransition = { fullScreenEnter(reducedMotion) },
            exitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(300)) },
            popEnterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(300)) },
            popExitTransition = { fullScreenExit(reducedMotion) },
        ) { backStackEntry ->
            val destination = backStackEntry.toRoute<Destinations.MedicationDetail>()
            val context = LocalContext.current.applicationContext
            val viewModel: MedicationDetailViewModel = viewModel(
                key = "medication-detail-${destination.medicationId}",
                factory = MedicationDetailViewModel.factory(context, destination.medicationId),
            )
            MedicationDetailScreen(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onEditMedication = {
                    navController.navigate(Destinations.MedicationMetadata(destination.medicationId))
                },
                onAddCourse = {
                    navController.navigate(Destinations.CourseEditor(destination.medicationId, null))
                },
                onEditCourse = { regimenId ->
                    navController.navigate(Destinations.CourseEditor(destination.medicationId, regimenId))
                },
                onStopAll = viewModel::stopAll,
                onDeleteMedication = {
                    viewModel.deleteMedication { navController.popBackStack() }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.CourseEditor>(
            enterTransition = { fullScreenEnter(reducedMotion) },
            exitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(300)) },
            popEnterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(300)) },
            popExitTransition = { fullScreenExit(reducedMotion) },
        ) { backStackEntry ->
            val destination = backStackEntry.toRoute<Destinations.CourseEditor>()
            CourseEditorScreen(
                medicationId = destination.medicationId,
                regimenId = destination.regimenId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destinations.MedicationMetadata>(
            enterTransition = { fullScreenEnter(reducedMotion) },
            exitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(300)) },
            popEnterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(300)) },
            popExitTransition = { fullScreenExit(reducedMotion) },
        ) { backStackEntry ->
            val destination = backStackEntry.toRoute<Destinations.MedicationMetadata>()
            MedicationMetadataScreen(
                medicationId = destination.medicationId,
                onSaved = { id ->
                    if (destination.medicationId == null) {
                        navController.navigate(Destinations.MedicationDetail(id)) {
                            popUpTo(Destinations.MedicationMetadata()) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
