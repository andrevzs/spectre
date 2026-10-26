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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.Keys
import app.financas.domain.Dates
import app.financas.domain.GoalStatus
import app.financas.domain.MonthSummary
import app.financas.domain.Money
import app.financas.domain.Todo
import app.financas.domain.allocateSegments
import app.financas.domain.currentGoal
import app.financas.domain.goalStatuses
import app.financas.domain.litSegments
import app.financas.domain.pendingClose
import app.financas.domain.summarizeMonth
import app.financas.domain.todos
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.BigMoney
import app.financas.ui.components.Card
import app.financas.ui.components.Dot
import app.financas.ui.components.GroupedSegBar
import app.financas.ui.components.ListRow
import app.financas.ui.components.MonthNav
import app.financas.ui.components.Pill
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.SegBar
import app.financas.ui.components.segs
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import app.financas.ui.tab
import java.time.YearMonth

@Composable
fun ColumnScope.HomeScreen(vm: AppViewModel, d: AppData, nav: NavHostController) {
    val today = vm.today
    val month = vm.month
    val isCurrent = month == YearMonth.from(today)
    val summary = remember(d, month, today) { summarizeMonth(month, today, d.settings, d.categories, d.entries, d.bills) }
    val goals = remember(d, today) { goalStatuses(d.goals, d.settings, d.categories, d.entries, today) }
    val goal = currentGoal(goals)
    val firstMonth = d.entries.isEmpty()

    LaunchedEffect(Unit) {
        if (!vm.closePrompted) {
            vm.closePrompted = true
            pendingClose(d.settings, d.entries, today)?.let { nav.navigate(Routes.resumo(Dates.key(it))) }
        }
    }

    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cabeçalho: mês e situação do plano
        Row(Modifier.fillMaxWidth().height(48.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MonthNav(Dates.monthShort(month), { vm.month = month.minusMonths(1) }, { vm.month = month.plusMonths(1) })
            Spacer(Modifier.weight(1f))
            if (firstMonth) {
                Text("mês começando", style = T.sans(13, color = C.Muted))
            } else {
                val over = summary.areasOver.size
                val color = if (over == 0) C.Invest else C.Alerta
                Row(
                    Modifier.height(44.dp).clickable { nav.tab(Routes.ORCAMENTO) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Dot(color)
                    Text(
                        when (over) { 0 -> "dentro do plano"; 1 -> "1 área acima do plano"; else -> "$over áreas acima do plano" },
                        style = T.sans(13, color = color),
                    )
                }
            }
        }

        GoalCard(goal, firstMonth, onClick = { nav.tab(Routes.METAS) }, onCreate = { nav.navigate(Routes.meta()) })
        FreeCard(summary, isCurrent, firstMonth)

        if (firstMonth && isCurrent) {
            FirstSteps(d, summary, vm, nav)
        } else if (isCurrent) {
            Todos(todos(summary, d.settings, today), vm, nav, today.dayOfMonth)
        }
    }
}

@Composable
private fun GoalCard(goal: GoalStatus?, firstMonth: Boolean, onClick: () -> Unit, onCreate: () -> Unit) {
    if (goal == null) {
        Card {
            SectionLabel("Nenhuma meta ainda")
            Text("Uma meta mostra quando você chega lá, com base no que guarda por mês.", style = T.sans(15))
            Pill("+ criar meta", onCreate)
        }
        return
    }
    val color = C.group(goal.goal.groupKey)
    Card(Modifier.clickable(onClick = onClick), gap = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                if (firstMonth) "Meta atual · ${goal.goal.name}" else goal.goal.name,
                style = T.sans(13, color = C.Muted), modifier = Modifier.weight(1f), maxLines = 1,
            )
            Text(if (goal.reached) "atingida" else "${goal.percent}%", style = T.mono(14, color = color))
        }
        BigMoney(goal.currentCents, 40)
        SegBar(segs(40, litSegments(40, goal.currentCents, goal.goal.targetCents), color))
        Row(Modifier.fillMaxWidth()) {
            Row(Modifier.weight(1f)) {
                if (firstMonth) {
                    Text("faltam ", style = T.mono(13, color = C.Muted))
                    Text(Money.short(goal.missingCents), style = T.mono(13))
                } else {
                    Text("meta ", style = T.mono(13, color = C.Muted))
                    Text(Money.short(goal.goal.targetCents), style = T.mono(13))
                }
            }
            Text("previsão ", style = T.mono(13, color = C.Muted))
            Text(goal.reachMonth?.let { Dates.monthShort(it) } ?: if (goal.reached) "—" else "sem aporte", style = T.mono(13))
        }
    }
}

@Composable
private fun FreeCard(s: MonthSummary, isCurrent: Boolean, firstMonth: Boolean) {
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Livre para gastar", style = T.sans(13, color = C.Muted), modifier = Modifier.weight(1f))
            s.perDayCents?.let { Text("≈ ${Money.brl(it)}/dia", style = T.mono(13, color = C.Muted)) }
        }
        BigMoney(s.freeCents, 32, if (s.freeCents < 0) C.Alerta else C.Text)

        val custos = s.custos
        val parts = allocateSegments(40, listOf(custos.budgetCents, s.reserva.budgetCents, s.invest.budgetCents))
        val nC = parts[0]
        val spentN = litSegments(nC, custos.spentCents, custos.budgetCents)
        val pendN = litSegments(nC, s.pendingFixedCents, custos.budgetCents).coerceAtMost(nC - spentN)
        val spentColor = if (s.freeCents < 0) C.Alerta else C.Text
        val custosSegs = List(nC) { i -> if (i < spentN) spentColor else if (i < spentN + pendN) C.Pending else C.Empty }
        val mark = s.monthFraction?.let { Math.round(it * nC).toInt() }
        GroupedSegBar(
            listOf(
                custosSegs,
                segs(parts[1], litSegments(parts[1], s.reserva.spentCents, s.reserva.budgetCents), C.Reserva),
                segs(parts[2], litSegments(parts[2], s.invest.spentCents, s.invest.budgetCents), C.Invest),
            ),
            markPart = 0, markAt = mark,
        )
        if (isCurrent && s.monthFraction != null) {
            Text("| hoje · ${(s.monthFraction * 100).toInt()}% do mês", style = T.mono(12, color = C.Muted))
        }
        if (firstMonth && s.baseIsPlanned) {
            Text("Com base no salário previsto de ${Money.brl(s.baseCents)}.", style = T.sans(13, color = C.Muted))
        }
    }
}

@Composable
private fun Todos(list: List<Todo>, vm: AppViewModel, nav: NavHostController, day: Int) {
    if (list.isEmpty()) return
    Column(Modifier.padding(horizontal = 8.dp)) {
        SectionLabel("Para fazer")
        list.forEach { t ->
            when (t) {
                is Todo.ConfirmSalary -> TodoRow("Confirmar salário", "${Money.brl(t.cents)} · dia ${t.day}", "confirmar", C.Text) { vm.confirmSalary() }
                is Todo.PayBill -> {
                    val b = t.status.bill
                    val dueDay = t.status.due.dayOfMonth
                    val title = b.name + if (dueDay < day) " venceu dia $dueDay" else " vence dia $dueDay"
                    if (b.variable) {
                        TodoRow(title, "≈ ${Money.brl(b.amountCents)} · valor varia", "informar", C.Text) {
                            nav.navigate(Routes.lancar(cat = b.categoryId ?: -1, amount = b.amountCents, bill = b.id))
                        }
                    } else {
                        TodoRow(title, Money.brl(b.amountCents), "paguei", C.Text) { vm.payBill(b) }
                    }
                }
                is Todo.Contribute -> {
                    val reserva = t.groupKey == Keys.RESERVA
                    TodoRow(
                        if (reserva) "Guardar na reserva" else "Investir",
                        Money.brl(t.remainingCents),
                        if (reserva) "guardar" else "investir",
                        C.group(t.groupKey),
                    ) { nav.navigate(Routes.lancar(cat = t.categoryId, amount = t.remainingCents)) }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(title: String, sub: String, action: String, dot: Color, onClick: () -> Unit) {
    ListRow(title, sub) { Pill(action, onClick, dot = dot) }
}

@Composable
private fun FirstSteps(d: AppData, s: MonthSummary, vm: AppViewModel, nav: NavHostController) {
    val bills = s.bills.count { it.bill.active }
    Column(Modifier.padding(horizontal = 8.dp)) {
        SectionLabel("Primeiros passos")
        StepRow("1", "Confirme o salário", "Quando cair na conta, dia ${d.settings.payday}", "confirmar") { vm.confirmSalary() }
        if (bills > 0) {
            StepRow("2", "Pague as contas fixas", "$bills ${if (bills == 1) "conta" else "contas"} · um toque em cada", "ver") { nav.navigate(Routes.FIXOS) }
        }
        StepRow(if (bills > 0) "3" else "2", "Lance o primeiro gasto", "Valor, área, pronto", "lançar") { nav.navigate(Routes.lancar()) }
    }
    Text(
        "toque no + para lançar um gasto ↓",
        style = T.mono(12, color = C.Muted), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun StepRow(n: String, title: String, sub: String, action: String, onClick: () -> Unit) {
    ListRow(
        title, sub,
        leading = {
            Box(Modifier.size(28.dp).background(C.Raised, CircleShape), contentAlignment = Alignment.Center) {
                Text(n, style = T.mono(13))
            }
        },
    ) { Pill(action, onClick) }
}
