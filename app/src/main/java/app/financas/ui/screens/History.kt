package app.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.financas.data.Entry
import app.financas.data.Keys
import app.financas.domain.Dates
import app.financas.domain.Money
import app.financas.domain.entriesIn
import app.financas.domain.goesToInvest
import app.financas.ui.AppData
import app.financas.ui.AppViewModel
import app.financas.ui.Routes
import app.financas.ui.components.Chip
import app.financas.ui.components.Dot
import app.financas.ui.components.IconButton
import app.financas.ui.components.Ic
import app.financas.ui.components.MonthNav
import app.financas.ui.components.TextInput
import app.financas.ui.theme.C
import app.financas.ui.theme.T
import java.time.LocalDate

private enum class Filter(val label: String) { TODOS("todos"), GASTOS("gastos"), ENTRADAS("entradas"), GUARDADO("guardado"), INVESTIDO("investido") }

@Composable
fun ColumnScope.HistoryScreen(vm: AppViewModel, d: AppData, nav: NavHostController) {
    val today = vm.today
    val month = vm.month
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.TODOS) }

    val groupOf = { e: Entry -> if (e.kind == Keys.RECEITA) null else d.category(e.categoryId)?.groupKey ?: Keys.CUSTOS }
    val monthEntries = remember(d, month) { entriesIn(d.entries, month) }
    val totals = remember(monthEntries) {
        listOf(
            monthEntries.filter { it.kind == Keys.RECEITA }.sumOf { it.amountCents },
            monthEntries.filter { groupOf(it) == Keys.CUSTOS }.sumOf { it.amountCents },
            monthEntries.filter { groupOf(it) == Keys.RESERVA }.sumOf { it.amountCents },
            monthEntries.filter { groupOf(it) == Keys.INVEST }.sumOf { it.amountCents },
        )
    }
    val q = query.trim().lowercase()
    val searchAll = searching && q.isNotEmpty()
    val list = remember(d, month, q, filter, searchAll) {
        (if (searchAll) d.entries else monthEntries)
            .filter {
                when (filter) {
                    Filter.TODOS -> true
                    Filter.GASTOS -> groupOf(it) == Keys.CUSTOS
                    Filter.ENTRADAS -> it.kind == Keys.RECEITA
                    Filter.GUARDADO -> groupOf(it) == Keys.RESERVA
                    Filter.INVESTIDO -> groupOf(it) == Keys.INVEST
                }
            }
            .filter { e ->
                q.isEmpty() || e.description.lowercase().contains(q) ||
                    (d.category(e.categoryId)?.name?.lowercase()?.contains(q) ?: false) ||
                    Money.plain(e.amountCents).contains(q)
            }
            .sortedWith(compareByDescending<Entry> { it.epochDay }.thenByDescending { it.id })
    }
    val days = list.groupBy { it.epochDay }.toList()

    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("histórico", style = T.mono(14, 500))
                IconButton(Ic.Search, "Buscar lançamentos", { searching = !searching; if (!searching) query = "" })
                Spacer(Modifier.weight(1f))
                MonthNav(Dates.monthShort(month), { vm.month = month.minusMonths(1) }, { vm.month = month.plusMonths(1) }, weight = 400)
            }
        }
        item {
            Column(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Total("Entradas", C.Muted, totals[0], Modifier.weight(1f))
                    Total("Gastos", C.Text, totals[1], Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Total("Guardado", C.Reserva, totals[2], Modifier.weight(1f))
                    Total("Investido", C.Invest, totals[3], Modifier.weight(1f))
                }
            }
        }
        if (searching) {
            item {
                Box(Modifier.padding(top = 14.dp)) {
                    TextInput(query, { query = it }, "descrição, área ou valor", label = "Buscar")
                }
            }
        }
        item {
            LazyRow(
                Modifier.padding(top = 14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Filter.entries.toList()) { f -> Chip(f.label, f == filter, { filter = f }, mono = true) }
            }
        }
        if (days.isEmpty()) {
            item {
                Text(
                    if (searchAll) "Nada encontrado." else "Nenhum lançamento neste mês.",
                    style = T.sans(15, color = C.Muted),
                    modifier = Modifier.padding(start = 8.dp, top = 24.dp),
                )
            }
        }
        days.forEach { (day, entries) ->
            item(key = "d$day") {
                val date = LocalDate.ofEpochDay(day)
                val label = Dates.dayLabel(date, today) + if (date.year != today.year) " ${date.year}" else ""
                Text(label, style = T.mono(13, color = C.Muted), modifier = Modifier.padding(start = 8.dp, top = 18.dp))
            }
            items(entries, key = { it.id }) { e -> EntryRow(e, d) { nav.navigate(Routes.lancar(edit = e.id)) } }
        }
    }
}

@Composable
private fun Total(name: String, dot: Color, cents: Long, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(dot)
            Text(name, style = T.sans(13, color = C.Muted))
        }
        Text(Money.brl(cents), style = T.mono(16), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun EntryRow(e: Entry, d: AppData, onClick: () -> Unit) {
    val cat = d.category(e.categoryId)
    val receita = e.kind == Keys.RECEITA
    val group = if (receita) null else cat?.groupKey ?: Keys.CUSTOS
    val s = d.settings
    val text = e.description.ifBlank {
        when {
            receita && e.source == Keys.SALARIO -> "Salário"
            receita && e.source == Keys.VARIAVEL -> s.variableName
            receita -> "Receita"
            else -> cat?.name ?: "Gasto"
        }
    }
    val where = when {
        receita && goesToInvest(e, s) -> "${s.variableName} · para investimento"
        receita && e.source == Keys.SALARIO -> "Dividido ${s.pctCustos} · ${s.pctReserva} · ${s.pctInvest}"
        receita -> (if (e.source == Keys.VARIAVEL) s.variableName else "Outra") + " · dividido pelo orçamento"
        else -> listOfNotNull(
            cat?.name ?: "Sem área",
            when (group) { Keys.RESERVA -> "guardado"; Keys.INVEST -> "investido"; else -> e.method },
            if (e.installmentCount > 1) "${e.installmentNo}/${e.installmentCount}" else null,
        ).joinToString(" · ")
    }
    val dot = when { receita -> C.Invest; else -> C.group(group!!) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Dot(dot)
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text, style = T.sans(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(where, style = T.sans(13, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                (if (receita) "+" else "−") + Money.brl(e.amountCents),
                style = T.mono(15, color = if (receita) C.Invest else C.Text),
                maxLines = 1, softWrap = false,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    }
}
