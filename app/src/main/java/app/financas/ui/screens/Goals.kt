package app.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.Keys
import app.financas.domain.Dates
import app.financas.domain.MonthlyBasis
import app.financas.domain.Money
import app.financas.domain.currentGoal
import app.financas.domain.goalStatuses
import app.financas.domain.groupBalance
import app.financas.domain.litSegments
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.BigMoney
import app.financas.ui.components.Card
import app.financas.ui.components.Dot
import app.financas.ui.components.Pill
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.SegBar
import app.financas.ui.components.segs
import app.financas.ui.theme.C
import app.financas.ui.theme.T

@Composable
fun ColumnScope.GoalsScreen(vm: AppViewModel, d: AppData, nav: NavHostController) {
    val today = vm.today
    val statuses = remember(d, today) { goalStatuses(d.goals, d.settings, d.categories, d.entries, today) }
    val current = currentGoal(statuses)
    val reserva = groupBalance(Keys.RESERVA, d.settings, d.categories, d.entries, today.toEpochDay())
    val invest = groupBalance(Keys.INVEST, d.settings, d.categories, d.entries, today.toEpochDay())

    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("metas", style = T.mono(14, 500), modifier = Modifier.weight(1f))
            Pill("+ nova", { nav.navigate(Routes.meta()) })
        }

        Card {
            SectionLabel("Patrimônio")
            BigMoney(reserva + invest, 36)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Amount("Guardado na reserva", C.Reserva, reserva, Modifier.weight(1f))
                Amount("Investido", C.Invest, invest, Modifier.weight(1f))
            }
            if (statuses.any { it.basis == MonthlyBasis.ESTIMADA }) {
                Text("Previsões estimadas pelo salário: ainda não há um mês fechado de lançamentos.", style = T.sans(13, color = C.Muted))
            }
        }

        if (statuses.isEmpty()) {
            Text("Nenhuma meta. Toque em + nova para criar a primeira.", style = T.sans(15, color = C.Muted), modifier = Modifier.padding(8.dp))
        } else {
            SectionLabel("Em ordem: a próxima começa quando a anterior termina", Modifier.padding(start = 8.dp))
            Column {
                statuses.forEachIndexed { i, s ->
                    val isCurrent = s == current
                    val color = if (isCurrent || s.reached) C.group(s.goal.groupKey) else C.Dim
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate(Routes.meta(s.goal.id)) }.padding(horizontal = 8.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("${i + 1}", style = T.mono(14, color = if (isCurrent) color else C.Muted), modifier = Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.goal.name, style = T.sans(15, color = if (isCurrent) C.Text else C.Text.copy(alpha = 0.8f)), modifier = Modifier.weight(1f))
                                Text(
                                    when {
                                        s.reached -> "atingida"
                                        isCurrent -> "atual · ${s.percent}%"
                                        else -> "${s.percent}%"
                                    },
                                    style = T.mono(13, color = if (isCurrent || s.reached) color else C.Muted),
                                )
                            }
                            SegBar(segs(40, litSegments(40, s.currentCents, s.goal.targetCents), color), height = 8.dp)
                            Row {
                                Text(
                                    "${Money.short(s.currentCents)} de ${Money.short(s.goal.targetCents)}",
                                    style = T.mono(13, color = C.Muted), modifier = Modifier.weight(1f),
                                )
                                Text(
                                    s.reachMonth?.let { Dates.monthShort(it) } ?: if (s.reached) "" else "sem previsão",
                                    style = T.mono(13),
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Amount(name: String, dot: androidx.compose.ui.graphics.Color, cents: Long, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(dot)
            Text(name, style = T.sans(13, color = C.Muted), maxLines = 1)
        }
        Text(Money.brl(cents), style = T.mono(15), maxLines = 1, softWrap = false)
    }
}
