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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.Keys
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.currentGoal
import app.financas.domain.goalStatuses
import app.financas.domain.litSegments
import app.financas.domain.summarizeMonth
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.BigMoney
import app.financas.ui.components.Card
import app.financas.ui.components.Glyph
import app.financas.ui.components.Ic
import app.financas.ui.components.IconButton
import app.financas.ui.components.PrimaryButton
import app.financas.ui.components.SecondaryButton
import app.financas.ui.components.SectionLabel
import app.financas.ui.components.SegBar
import app.financas.ui.components.segs
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ColumnScope.LancadoScreen(vm: AppViewModel, d: AppData, nav: NavHostController, id: Long) {
    val entry = d.entries.firstOrNull { it.id == id }
    val home = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
    if (entry == null) {
        LaunchedEffect(Unit) { home() }
        return
    }
    val today = vm.today
    val date = LocalDate.ofEpochDay(entry.epochDay)
    val month = YearMonth.from(date)
    val series = entry.seriesId
    val without = remember(d, id) { d.entries.filter { it.id != id && (series == null || it.seriesId != series) } }
    val after = remember(d, month, today) { summarizeMonth(month, today, d.settings, d.categories, d.entries, d.bills) }
    val before = remember(without, month, today) { summarizeMonth(month, today, d.settings, d.categories, without, d.bills) }
    val category = d.category(entry.categoryId)
    val withdrawal = entry.kind == Keys.RECEITA && entry.source == Keys.RESGATE
    val group = if (entry.kind == Keys.RECEITA) null else category?.groupKey ?: Keys.CUSTOS

    val title = when {
        withdrawal -> "Resgate lançado"
        entry.kind == Keys.RECEITA -> "Receita lançada"
        group == Keys.RESERVA -> "Guardado"
        group == Keys.INVEST -> "Investido"
        entry.fixedBillId != null -> "Conta paga"
        else -> "Gasto lançado"
    }
    val where = buildList {
        add(Dates.relative(date, today))
        entry.method?.let { add(it) }
        if (entry.installmentCount > 1) add("${entry.installmentNo}/${entry.installmentCount}")
    }.joinToString(" · ")
    val origin = when (entry.source) {
        Keys.SALARIO -> "Salário"
        Keys.VARIAVEL -> d.settings.variableName
        Keys.OUTRA -> "Outra"
        Keys.RESGATE -> category?.name ?: "Resgate"
        else -> category?.name ?: "—"
    }

    Column(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Ic.Close, "Fechar", home, bg = C.Surface, size = 48.dp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable {
                    vm.discardEntry(id) {
                        nav.navigate(Routes.lancar(kind = entry.kind)) { popUpTo(Routes.HOME) }
                    }
                }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("desfazer", style = T.mono(14, color = C.Muted)) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(24.dp).background(group?.let { C.group(it) } ?: C.Invest, CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(Ic.Check, C.Bg, 16.dp, 2.4f)
                    }
                    Text(title, style = T.sans(15))
                }
                BigMoney(entry.amountCents, 44)
                Row {
                    Text("em ", style = T.sans(15, color = C.Muted))
                    Text(origin, style = T.sans(15))
                    Text(" · $where", style = T.sans(15, color = C.Muted))
                }
            }

            Card(gap = 18.dp) {
                SectionLabel("O que mudou")
                when (group) {
                    Keys.CUSTOS -> {
                        val area = after.custos.areas.firstOrNull { it.category.id == entry.categoryId }
                        if (area != null) {
                            val planned = area.category.plannedCents
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(area.category.name, style = T.sans(15), modifier = Modifier.weight(1f))
                                    Text("${Money.brl(area.spentCents)} de ${Money.brl(planned)}", style = T.mono(13, color = C.Muted))
                                }
                                SegBar(segs(40, litSegments(40, area.spentCents, planned), if (area.isOver) C.Alerta else C.Text))
                                if (area.isOver) {
                                    Text("passou ${Money.brl(-area.remainingCents)} do planejado", style = T.mono(13, color = C.Alerta))
                                } else {
                                    Text("restam ${Money.brl(area.remainingCents)}", style = T.mono(13, color = C.Muted))
                                }
                            }
                            FreeChange(before.freeCents, after.freeCents, after.perDayCents)
                            if (area.isOver) {
                                val over = -area.remainingCents
                                val donor = after.custos.areas
                                    .filter { it.category.id != area.category.id && !it.category.archived && it.remainingCents >= over }
                                    .maxByOrNull { it.remainingCents }
                                if (donor != null) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("Compensar com ${donor.category.name}", style = T.sans(15))
                                            Text(
                                                "${Money.brl(donor.remainingCents)} → ${Money.brl(donor.remainingCents - over)}",
                                                style = T.mono(13, color = C.Muted),
                                            )
                                        }
                                        Box(
                                            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.Raised)
                                                .clickable {
                                                    vm.updateCategory(donor.category.copy(plannedCents = donor.category.plannedCents - over))
                                                    vm.updateCategory(area.category.copy(plannedCents = area.category.plannedCents + over))
                                                }.padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { Text("ajustar", style = T.mono(14)) }
                                    }
                                    Text(
                                        "Ajustar move ${Money.brl(over)} do planejado de ${donor.category.name} para ${area.category.name}.",
                                        style = T.sans(12, color = C.Muted),
                                    )
                                }
                            }
                        } else {
                            FreeChange(before.freeCents, after.freeCents, after.perDayCents)
                        }
                    }
                    Keys.RESERVA, Keys.INVEST -> {
                        val key: String = group!!
                        val g = after.group(key)
                        Row(Modifier.fillMaxWidth()) {
                            Text(if (key == Keys.RESERVA) "Guardado no mês" else "Investido no mês", style = T.sans(15), modifier = Modifier.weight(1f))
                            Text("${Money.brl(g.spentCents)} / ${Money.brl(g.budgetCents)}", style = T.mono(13, color = C.Muted))
                        }
                        SegBar(segs(40, litSegments(40, g.spentCents, g.budgetCents), C.group(key)))
                        GoalChange(d, without, key, today)
                    }
                    else -> {
                        FreeChange(before.freeCents, after.freeCents, after.perDayCents)
                        if (withdrawal && category != null) GoalChange(d, without, category.groupKey, today)
                        if (after.invest.directCents > 0 && entry.source == Keys.VARIAVEL) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("Para investir no mês", style = T.sans(15), modifier = Modifier.weight(1f))
                                Text(Money.brl(after.invest.remainingCents.coerceAtLeast(0)), style = T.mono(15, color = C.Invest))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("lançar outro", { nav.navigate(Routes.lancar(kind = entry.kind)) { popUpTo(Routes.HOME) } }, Modifier.weight(1f))
            PrimaryButton("voltar ao início", home, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FreeChange(before: Long, after: Long, perDay: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Livre para gastar", style = T.sans(15), modifier = Modifier.weight(1f))
            Text(Money.brl(after), style = T.mono(15, color = if (after < 0) C.Alerta else C.Text))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("antes ${Money.brl(before)}", style = T.mono(13, color = C.Muted), modifier = Modifier.weight(1f))
            perDay?.let { Text("≈ ${Money.brl(it)} por dia", style = T.mono(13, color = C.Muted)) }
        }
    }
}

/** Progresso da meta atual do grupo: segmentos antigos apagados, os novos na cor cheia. */
@Composable
private fun GoalChange(d: AppData, without: List<app.financas.data.Entry>, group: String, today: LocalDate) {
    val now = goalStatuses(d.goals.filter { it.groupKey == group }, d.settings, d.categories, d.entries, today)
    val goal = currentGoal(now) ?: return
    val prev = goalStatuses(listOf(goal.goal), d.settings, d.categories, without, today).first()
    val a = litSegments(40, prev.currentCents, goal.goal.targetCents)
    val b = litSegments(40, goal.currentCents, goal.goal.targetCents)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(goal.goal.name, style = T.sans(15), modifier = Modifier.weight(1f))
            Text("${prev.percent}% → ${goal.percent}%", style = T.mono(13, color = C.group(group)))
        }
        // Aporte: segmentos novos na cor cheia. Resgate: os que saíram ficam apagados.
        SegBar(
            if (b >= a) List(40) { i -> if (i < a) C.groupDim(group) else if (i < b) C.group(group) else C.Empty }
            else List(40) { i -> if (i < b) C.group(group) else if (i < a) C.groupDim(group) else C.Empty },
        )
        goal.reachMonth?.let { Text("previsão ${Dates.monthShort(it)}", style = T.mono(13, color = C.Muted)) }
    }
}
