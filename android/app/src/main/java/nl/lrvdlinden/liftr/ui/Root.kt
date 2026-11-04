package nl.lrvdlinden.liftr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.LiftrData
import nl.lrvdlinden.liftr.core.tr
import nl.lrvdlinden.liftr.data.Settings

@Composable
fun LiftrRoot() {
    val repo = LiftrApp.repository
    val data by repo.data.collectAsState()
    val loaded by repo.loaded.collectAsState()
    val holder = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(LiftrColors.background)) {
        if (!loaded) return@Box
        if (!Settings.onboardingDone) {
            OnboardingScreen()
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                holder.SaveableStateProvider("tab$tab") {
                    when (tab) {
                        0 -> HomeScreen(data)
                        1 -> HistoryScreen(data)
                        2 -> StatsScreen(data)
                        3 -> LibraryScreen(data)
                        else -> SettingsScreen(data)
                    }
                }
            }
            NavigationBar(containerColor = LiftrColors.card, tonalElevation = 0.dp) {
                val items = listOf(
                    tr("Today", "Vandaag") to Icons.Filled.LocalFireDepartment,
                    tr("History", "Geschiedenis") to Icons.Filled.CalendarMonth,
                    tr("Progress", "Voortgang") to Icons.Filled.ShowChart,
                    tr("Library", "Bibliotheek") to Icons.Filled.FitnessCenter,
                    tr("Settings", "Instellingen") to Icons.Filled.Settings,
                )
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, null, modifier = Modifier.size(24.dp)) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LiftrColors.accent, selectedTextColor = LiftrColors.accent,
                            unselectedIconColor = LiftrColors.muted, unselectedTextColor = LiftrColors.muted,
                            indicatorColor = LiftrColors.accent.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        }

        // Every screen in the stack stays composed (so its state survives a picker on top); the top one is drawn last.
        for (route in Nav.stack) {
            key(route.key) {
                Box(
                    Modifier.fillMaxSize().background(LiftrColors.background).navigationBarsPadding()
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                ) {
                    RouteScreen(route, data)
                }
            }
        }
    }
}

@Composable
private fun RouteScreen(route: Route, data: LiftrData) {
    when (route) {
        is Route.WorkoutDetail -> WorkoutDetailScreen(data, route.id)
        Route.Badges -> BadgesScreen(data)
        is Route.ExerciseDetail -> ExerciseDetailScreen(data, route.id)
        is Route.ExerciseEdit -> ExerciseEditScreen(data, route)
        is Route.ExercisePicker -> ExercisePickerScreen(data, route.onPick)
        is Route.RoutineEdit -> RoutineEditScreen(data, route.id)
        Route.Session -> SessionScreen(data)
        is Route.Summary -> SummaryScreen(data, route.workoutId)
        Route.Wallet -> WalletScreen(data)
        is Route.PassEdit -> PassEditScreen(data, route.id)
        is Route.PassFull -> PassFullScreen(data, route.id)
        is Route.GymPicker -> GymPickerScreen(route.onPick)
        is Route.Scanner -> ScannerScreen(route.onResult)
        Route.Import -> ImportScreen(data)
        Route.DataManagement -> DataManagementScreen(data)
        Route.Privacy -> PrivacyScreen()
        Route.BodyEntry -> BodyEntryScreen()
        Route.Onboarding -> OnboardingScreen()
    }
}

val Transparent = Color.Transparent
