package app.financas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import app.financas.domain.Money
import app.financas.domain.closeMonth
import app.financas.domain.currentGoal
import app.financas.domain.goalStatuses
import app.financas.domain.groupBalance
import app.financas.domain.litSegments
import app.financas.domain.summarizeMonth
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.BigMoney
import app.financas.ui.components.Card
import app.financas.ui.components.Dot
import app.financas.ui.components.Ic
import app.financas.ui.components.IconButton
import app.financas.ui.components.Pill
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.SegBar
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.YearMonth

@Composable
fun ColumnScope.ResumoScreen(vm: AppViewModel, d: AppData, nav: NavHostController, month: YearMonth) {
    val today = vm.today
    val close = remember(d, month, today) { closeMonth(summarizeMonth(month, today, d.settings, d.categories, d.entries, d.bills)) }
    val s = close.summary
    val isLatest = month == YearMonth.from(today).minusMonths(1)
    val leftoverLabel = "Sobra de " + Dates.monthName(month)
    val alreadyMoved = d.entries.any { it.description == leftoverLabel }
    val goal = currentGoal(goalStatuses(d.goals, d.settings, d.categories, d.entries, today))

    val leave = {
        vm.markSummarySeen(month)
        if (!nav.popBackStack()) nav.navigate(Routes.HOME)
    }

    Column(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${Dates.monthName(month)} fechou", style = T.mono(14, 500), modifier = Modifier.weight(1f))
                IconButton(Ic.Close, "Fechar", leave, bg = C.Surface, size = 48.dp)
            }

            Card {
                val over = close.leftoverCents < 0
                SectionLabel(if (over) "Passou dos custos" else "Sobrou dos custos")
                BigMoney(if (over) -close.leftoverCents else close.leftoverCents, 40, if (over) C.Alerta else C.Text)
                if (!over && close.leftoverCents > 0 && isLatest && !alreadyMoved) {
                    Text("Para onde vai a sobra?", style = T.sans(15))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("guardar", { vm.moveLeftover(month, Keys.RESERVA, close.leftoverCents); leave() }, dot = C.Reserva)
                        Pill("investir", { vm.moveLeftover(month, Keys.INVEST, close.leftoverCents); leave() }, dot = C.Invest)
                        Pill("manter", leave, bg = C.Surface, fg = C.Muted)
                    }
                    goal?.let {
                        val verb = if (it.goal.groupKey == Keys.RESERVA) "guardar" else "investir"
                        Text("Sugestão: $verb, porque ${it.goal.name.lowercase()} é a meta atual.", style = T.sans(13, color = C.Muted))
                    }
                } else if (alreadyMoved) {
                    Text("A sobra já foi levada para as metas.", style = T.sans(13, color = C.Muted))
                }
            }

            if (goal != null) {
                val key = goal.goal.groupKey
                val start = groupBalance(key, d.settings, d.categories, d.entries, month.atDay(1).toEpochDay() - 1)
                val end = groupBalance(key, d.settings, d.categories, d.entries, month.atEndOfMonth().toEpochDay())
                val target = goal.goal.targetCents
                val a = litSegments(40, start, target)
                val b = litSegments(40, end, target)
                val pct = { v: Long -> if (target <= 0) 100 else (v.coerceAtMost(target) * 100 / target).toInt() }
                Card {
                    Row(Modifier.fillMaxWidth()) {
                        Text(goal.goal.name, style = T.sans(13, color = C.Muted), modifier = Modifier.weight(1f))
                        Text("${pct(start)}% → ${pct(end)}%", style = T.mono(13, color = C.group(key)))
                    }
                    SegBar(List(40) { i -> if (i < a) C.groupDim(key) else if (i < b) C.group(key) else C.Empty })
                    Text("Segmentos claros: o que entrou em ${Dates.monthName(month)}.", style = T.sans(12, color = C.Muted))
                }
            }

            Card(gap = 14.dp) {
                SumRow("Entradas", C.Muted, Money.brl(s.incomeCents))
                if (s.withdrawalCents > 0) SumRow("Resgates para custos", C.Muted, Money.brl(s.withdrawalCents))
                if (s.repaidCents > 0) SumRow("Reposições de resgate", C.Muted, Money.brl(s.repaidCents))
                SumRow("Custos", C.Text, "${Money.brl(s.custos.spentCents)} / ${Money.brl(s.custos.budgetCents)}", if (s.custos.spentCents > s.custos.budgetCents) C.Alerta else C.Text)
                SumRow("Guardado", C.Reserva, "${Money.brl(s.reserva.netContributionCents)} / ${Money.brl(s.reserva.budgetCents)}")
                SumRow(
                    "Investido", C.Invest, "${Money.brl(s.invest.netContributionCents)} / ${Money.brl(s.invest.budgetCents)}",
                    if (s.invest.netContributionCents >= s.invest.budgetCents && s.invest.budgetCents > 0) C.Invest else C.Text,
                )
            }

            if (close.over.isNotEmpty() || close.under.isNotEmpty()) {
                Card(gap = 10.dp) {
                    SectionLabel("Destaques")
                    close.over.take(3).forEach {
                        Row(Modifier.fillMaxWidth()) {
                            Text("${it.category.name} passou do plano", style = T.sans(15), modifier = Modifier.weight(1f))
                            Text("+ ${Money.brl(-it.remainingCents)}", style = T.mono(14, color = C.Alerta))
                        }
                    }
                    close.under.take(2).forEach {
                        Row(Modifier.fillMaxWidth()) {
                            Text("${it.category.name} ficou abaixo", style = T.sans(15), modifier = Modifier.weight(1f))
                            Text("− ${Money.brl(it.remainingCents)}", style = T.mono(14, color = C.Muted))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (isLatest) "começar ${Dates.monthName(month.plusMonths(1))}" else "voltar", leave)
    }
}

@Composable
private fun SumRow(name: String, dot: androidx.compose.ui.graphics.Color, value: String, valueColor: androidx.compose.ui.graphics.Color = C.Text) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(dot)
        Text(name, style = T.sans(15), modifier = Modifier.weight(1f))
        Text(value, style = T.mono(14, color = valueColor), maxLines = 1)
    }
}
