package com.chandu.gatesystem.ai2027.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chandu.gatesystem.ai2027.ui.screens.*
import com.chandu.gatesystem.ai2027.ui.vm.HomeVm
import com.chandu.gatesystem.ai2027.ui.vm.factoryOf

/** Screens that are replaced (not stacked) when they open the screen they launch. */
private val launchers = listOf("setup/", "revision/", "examsetup/", "practice/")

/** All 21 screens in one graph. [HomeVm] is created once (activity scope) and shared by the dashboard-family screens. */
@Composable
fun GateNav(nav: NavHostController = rememberNavController()) {
    val c = LocalContainer.current
    val home: HomeVm = viewModel(factory = factoryOf { HomeVm(c) })

    fun go(route: String) {
        val cur = nav.currentDestination?.route.orEmpty()
        val replace = launchers.any { cur.startsWith(it) } && (route.startsWith("practice/") || route.startsWith("exam/") || route.startsWith("report/"))
        nav.navigate(route) {
            launchSingleTop = true
            if (replace) popUpTo(cur) { inclusive = true }
        }
    }
    val back: () -> Unit = { if (!nav.popBackStack()) go(Routes.DASH) }
    fun NavBackStackEntry.s(key: String) = arguments?.getString(key).orEmpty()
    fun NavBackStackEntry.i(key: String) = arguments?.getInt(key) ?: 1
    val strArg = { n: String -> navArgument(n) { type = NavType.StringType } }
    val intArg = { n: String -> navArgument(n) { type = NavType.IntType } }

    NavHost(nav, startDestination = Routes.INIT) {
        composable(Routes.INIT) {
            SystemInitScreen { setupDone ->
                nav.navigate(if (setupDone) Routes.DASH else Routes.DETECT) { popUpTo(Routes.INIT) { inclusive = true } }
            }
        }
        composable(Routes.DETECT) {
            PlayerDetectionScreen { nav.navigate(Routes.DASH) { popUpTo(Routes.DETECT) { inclusive = true } } }
        }
        composable(Routes.DASH) { DashboardScreen(home, ::go) }
        composable(Routes.QUESTS) { DailyQuestsScreen(home, back, ::go) }
        composable(Routes.CALENDAR) { CalendarScreen(home, back, ::go) }
        composable(Routes.SUBJECTS) { SubjectSelectionScreen(back, ::go) }
        composable(Routes.SUBJECT, listOf(strArg("id"))) { SubjectTopicsScreen(it.s("id"), back, ::go) }
        composable(Routes.LESSON, listOf(strArg("topic"))) { LessonScreen(it.s("topic"), back, ::go) }
        composable(Routes.SETUP, listOf(strArg("kind"), intArg("day"))) { SetLauncherScreen(it.s("kind"), it.i("day"), back, ::go) }
        composable(Routes.REVISION, listOf(intArg("day"))) { RevisionLauncherScreen(it.i("day"), back, ::go) }
        composable(Routes.PRACTICE, listOf(strArg("id"))) { PracticeScreen(it.s("id"), back, ::go) }
        composable(Routes.REPORT, listOf(strArg("id"))) { PracticeReportScreen(it.s("id"), back, ::go) }
        composable(Routes.EXAM_SETUP, listOf(intArg("day"))) { ExamLauncherScreen(it.i("day"), back, ::go) }
        composable(Routes.EXAM, listOf(strArg("id"))) { ExamScreen(it.s("id"), back, ::go) }
        composable(Routes.MOCKS) { MockDashboardScreen(back, ::go) }
        composable(Routes.MONSTER) { ErrorMonsterScreen(back, ::go) }
        composable(Routes.PLAYER) { PlayerStatusScreen(home, back) }
        composable(Routes.XP) { XpRankScreen(home, back) }
        composable(Routes.WORKOUT) { WorkoutScreen(home, back) }
        composable(Routes.ALARMS) { AlarmSettingsScreen(back) }
        composable(Routes.AI) { AiTeacherScreen(back) }
        composable(Routes.BANK) { QuestionBankScreen(back) }
        composable(Routes.BACKUP) { BackupScreen(back) }
        composable(Routes.SETTINGS) { AppSettingsScreen(back, ::go) }
    }
}
