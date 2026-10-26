package app.financas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.financas.domain.Dates
import app.financas.ui.components.BottomNav
import app.financas.ui.components.Tab
import app.financas.ui.components.UndoToast
import app.financas.ui.screens.AjustesScreen
import app.financas.ui.screens.BudgetScreen
import app.financas.ui.screens.ConfigScreen
import app.financas.ui.screens.FixosScreen
import app.financas.ui.screens.GoalFormScreen
import app.financas.ui.screens.GoalsScreen
import app.financas.ui.screens.HistoryScreen
import app.financas.ui.screens.HomeScreen
import app.financas.ui.screens.LancadoScreen
import app.financas.ui.screens.LancarArgs
import app.financas.ui.screens.LancarScreen
import app.financas.ui.screens.ResumoScreen
import app.financas.ui.theme.C
import kotlinx.coroutines.delay

object Routes {
    const val HOME = "home"
    const val HISTORICO = "historico"
    const val METAS = "metas"
    const val ORCAMENTO = "orcamento"
    const val FIXOS = "fixos"
    const val AJUSTES = "ajustes"
    const val CONFIG = "config"

    fun lancar(edit: Long = -1, cat: Long = -1, amount: Long = -1, bill: Long = -1, kind: String = "gasto") =
        "lancar?edit=$edit&cat=$cat&amount=$amount&bill=$bill&kind=$kind"

    fun lancado(id: Long) = "lancado/$id"
    fun meta(id: Long = -1) = "meta?id=$id"
    fun resumo(key: Int) = "resumo/$key"
}

fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshDay()
        onPauseOrDispose { }
    }
    val loaded = data
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(C.Bg))
        return
    }
    val nav = rememberNavController()
    val start = remember { if (loaded.settings.onboarded) Routes.HOME else Routes.CONFIG }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        NavHost(nav, start, Modifier.fillMaxSize()) {
            composable(Routes.HOME) { TabFrame(Tab.INICIO, nav) { WithData(vm) { d -> HomeScreen(vm, d, nav) } } }
            composable(Routes.HISTORICO) { TabFrame(Tab.HISTORICO, nav) { WithData(vm) { d -> HistoryScreen(vm, d, nav) } } }
            composable(Routes.METAS) { TabFrame(Tab.METAS, nav) { WithData(vm) { d -> GoalsScreen(vm, d, nav) } } }
            composable(Routes.ORCAMENTO) { TabFrame(Tab.ORCAMENTO, nav) { WithData(vm) { d -> BudgetScreen(vm, d, nav) } } }
            composable(Routes.FIXOS) { TabFrame(Tab.ORCAMENTO, nav) { WithData(vm) { d -> FixosScreen(vm, d, nav) } } }
            composable(Routes.AJUSTES) { TabFrame(Tab.ORCAMENTO, nav) { WithData(vm) { d -> AjustesScreen(vm, d, nav) } } }
            composable(
                "lancar?edit={edit}&cat={cat}&amount={amount}&bill={bill}&kind={kind}",
                arguments = listOf(
                    navArgument("edit") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("cat") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("amount") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("bill") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "gasto" },
                ),
            ) { e ->
                val a = e.arguments!!
                Screen {
                    WithData(vm) { d -> LancarScreen(
                        vm, d, nav,
                        LancarArgs(
                            editId = a.getLong("edit").takeIf { it > 0 },
                            categoryId = a.getLong("cat").takeIf { it > 0 },
                            amountCents = a.getLong("amount").takeIf { it > 0 },
                            billId = a.getLong("bill").takeIf { it > 0 },
                            kind = a.getString("kind") ?: "gasto",
                        ),
                    ) }
                }
            }
            composable("lancado/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                Screen { WithData(vm) { d -> LancadoScreen(vm, d, nav, e.arguments!!.getLong("id")) } }
            }
            composable("meta?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                Screen { WithData(vm) { d -> GoalFormScreen(vm, d, nav, e.arguments!!.getLong("id").takeIf { it > 0 }) } }
            }
            composable("resumo/{key}", arguments = listOf(navArgument("key") { type = NavType.IntType })) { e ->
                Screen { WithData(vm) { d -> ResumoScreen(vm, d, nav, Dates.fromKey(e.arguments!!.getInt("key"))) } }
            }
            composable(Routes.CONFIG) { Screen { WithData(vm) { d -> ConfigScreen(vm, d, nav) } } }
        }

        val toast = vm.toast
        if (toast != null) {
            LaunchedEffect(toast.id) { delay(5000); vm.dismissToast(toast.id) }
            val onTab = route in setOf(Routes.HOME, Routes.HISTORICO, Routes.METAS, Routes.ORCAMENTO, Routes.FIXOS, Routes.AJUSTES)
            UndoToast(
                toast.text, toast.value, if (toast.undo != null) ({ vm.undoToast() }) else null,
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = if (onTab) 100.dp else 24.dp),
            )
        }
    }
}

/** Tela de uma aba: conteúdo em cima, navegação embaixo. */
@Composable
private fun TabFrame(tab: Tab, nav: NavHostController, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), content = content)
        BottomNav(
            current = tab,
            onTab = {
                nav.tab(
                    when (it) {
                        Tab.INICIO -> Routes.HOME
                        Tab.HISTORICO -> Routes.HISTORICO
                        Tab.METAS -> Routes.METAS
                        Tab.ORCAMENTO -> Routes.ORCAMENTO
                    },
                )
            },
            onAdd = { nav.navigate(Routes.lancar()) },
        )
    }
}

/** Tela cheia sem a navegação de baixo. */
@Composable
private fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(C.Bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        content = content,
    )
}

/** Lê os dados dentro da tela, para o grafo de navegação não depender deles. */
@Composable
private fun WithData(vm: AppViewModel, content: @Composable (AppData) -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    data?.let { content(it) }
}
